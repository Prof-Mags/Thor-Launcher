package com.thor.data.stream

import android.content.Context
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.net.Proxy
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS to a paired host, and the certificates that make it possible.
 *
 * Once paired, **every** request has to go over TLS presenting the client
 * certificate — not just the last step of pairing. A GameStream host cannot tell
 * one client from another over plain HTTP, because the connection carries
 * nothing that identifies anybody, so it answers those requests as though the
 * caller were a stranger. That is not an error and produces no failure: it is
 * simply a channel that cannot express the answer.
 *
 * Shared rather than duplicated because getting it wrong is quiet. The pairing
 * handshake and the status check need exactly the same socket, and building it
 * twice invites the second one to be built slightly differently.
 */
@Singleton
class SecureChannel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: ClientIdentity,
) {

    private val directory: File
        get() = File(context.filesDir, "stream/hosts").apply { mkdirs() }

    /**
     * Remembers the certificate a host presented while pairing.
     *
     * Kept so later connections can be pinned to it. Without this the only
     * options are trusting the public roots — which never vouch for a
     * self-signed machine on a home network — or trusting anything at all, which
     * is not a check.
     */
    fun remember(address: String, certificate: X509Certificate) {
        runCatching { fileFor(address).writeBytes(certificate.encoded) }
            .onFailure { ThorLog.w(TAG, "Could not store the host certificate", it) }
    }

    /** The stored certificate for a host, or null if it has never paired. */
    fun certificateFor(address: String): X509Certificate? = runCatching {
        val file = fileFor(address)
        if (!file.exists()) return null
        file.inputStream().use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
        }
    }.getOrNull()

    fun forget(address: String) {
        runCatching { fileFor(address).delete() }
    }

    /**
     * Which queue a request waits in.
     *
     * Two, and they must not be one. OkHttp dispatches through a `Dispatcher`
     * that admits at most five concurrent requests **per host**, and every
     * client derived with `newBuilder()` shares that dispatcher — including the
     * image loader's. Opening a library fires one artwork request per game at a
     * single host, which fills those five slots with pictures.
     *
     * A launch then queues behind them and is never dispatched at all. Since no
     * socket is ever opened, no connect or read timeout applies, and the only
     * thing that eventually fires is the caller's own deadline — so a launch
     * blocked by box art is indistinguishable from a PC that has stopped
     * answering, which is exactly how it was reported.
     */
    enum class Lane {
        /** Status, pairing, the app list, launching. Must never wait on artwork. */
        CONTROL,

        /** Box art. Many, unimportant, and deliberately capped. */
        ARTWORK,
    }

    /*
     * One dispatcher per lane, made once.
     *
     * Per lane rather than per client, because a `Dispatcher` owns a thread
     * pool: building one per request — and `clientFor` is called per request —
     * would leak an executor every time. These two are the whole cost.
     */
    private val controlDispatcher by lazy {
        Dispatcher().apply {
            maxRequests = CONTROL_MAX_REQUESTS
            maxRequestsPerHost = CONTROL_MAX_REQUESTS
        }
    }

    private val artworkDispatcher by lazy {
        Dispatcher().apply {
            maxRequests = ARTWORK_MAX_REQUESTS
            /*
             * Kept low on purpose. Every one of these is a fresh TLS handshake
             * against a machine that is also being asked to run a game, and box
             * art arriving a moment later costs nothing.
             */
            maxRequestsPerHost = ARTWORK_MAX_REQUESTS
        }
    }

    /**
     * A client that presents THOR's certificate and accepts only [serverCert].
     *
     * Owes nothing to the launcher's shared client. A GameStream host is not a
     * metadata API: it wants short timeouts, no cache, no proxy, no connection
     * reuse and a queue of its own, and every one of those was previously
     * inherited from a client tuned for the opposite.
     */
    fun clientFor(
        serverCert: X509Certificate,
        lane: Lane = Lane.CONTROL,
    ): OkHttpClient {
        val me = identity.get()

        /*
         * Our own key manager, because the default one refuses to send the
         * certificate.
         *
         * A `KeyManagerFactory` builds a manager that picks a certificate by
         * matching it against the **accepted issuers** the server advertises
         * during the handshake. THOR's certificate is self-signed — its issuer is
         * itself, and no host lists it — so `chooseClientAlias` finds no
         * candidate, returns null, and the client sends *no certificate at all*.
         *
         * The failure that produces is the worst kind: not a rejection, but a
         * server waiting for something that is never sent. The connection opens,
         * the handshake stalls, and it surfaces as a timeout — which reads as a
         * slow network and is nothing of the sort. Raising timeouts made it take
         * longer to fail.
         *
         * This one has a single identity and offers it unconditionally, which is
         * exactly right for a client that owns precisely one certificate and is
         * talking to a host that already knows it by heart.
         */
        val keyManager = SingleIdentityKeyManager(me.certificate, me.privateKey)

        val pinned = PinnedTrustManager(serverCert)
        val ssl = SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(keyManager), arrayOf<TrustManager>(pinned), SecureRandom())
        }

        /*
         * Built from nothing, which is the whole point.
         *
         * This used to derive from the launcher's shared client with
         * `newBuilder()`, and inherited every one of its decisions: a 64 MB
         * on-disk cache, `retryOnConnectionFailure`, a 60-second call timeout, a
         * 30-second read timeout, and — most damaging — its `Dispatcher`, which
         * admits five requests per host and was being filled with box art while
         * a launch waited behind it, never dispatched and therefore never timed
         * out by anything but the caller.
         *
         * Moonlight constructs `new OkHttpClient.Builder()` for exactly this
         * reason: a GameStream host is nothing like a metadata API, and the
         * settings that suit one are actively wrong for the other. Sharing an
         * object to "reuse configuration" is how the configuration of one thing
         * silently becomes the configuration of another.
         */
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, pinned)
            /*
             * The host's certificate names a machine, and THOR connects by
             * address — over Tailscale, by an address the certificate has never
             * heard of. The name can therefore never match, and identity is
             * established by the pin instead.
             */
            .hostnameVerifier { _, _ -> true }
            /*
             * Its own pool, holding nothing.
             *
             * Derived clients share the pool of the one they came from, so these
             * TLS sockets were being kept alive in the same pool the launcher
             * uses for every catalogue and artwork request — and a connection
             * left half-open by an earlier attempt is handed straight back to
             * the next one, which then waits on a socket the host has long since
             * stopped listening to. That is a hang rather than an error, and it
             * is indistinguishable from a slow network.
             *
             * Moonlight uses exactly this: a pool of zero idle connections with a
             * one-millisecond life. A fresh socket per request costs a handshake
             * and removes an entire class of stall.
             */
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            /*
             * Its own queue, for the same reason it has its own pool.
             *
             * Inherited from the launcher's client, this is the dispatcher every
             * catalogue lookup, artwork fetch and metadata request also uses —
             * so a request here waits behind whatever else the launcher happens
             * to be doing, up to five per host. That is not a delay a game
             * launch can absorb, and the wait is invisible: a queued call has no
             * socket and therefore no timeout of its own.
             */
            .dispatcher(if (lane == Lane.ARTWORK) artworkDispatcher else controlDispatcher)
            /*
             * No proxy, ever. The host is a machine on the local network or at
             * the end of a VPN, and a system proxy configured for the internet
             * cannot reach it — it will accept the connection and never answer.
             */
            .proxy(Proxy.NO_PROXY)
            // Moonlight's own figures. If a GameStream host has not answered in
            // seven seconds it is not going to.
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            // The response is XML that changes on every request; caching it would
            // be answering the question with the previous answer.
            .cache(null)
            .build()
    }

    /*
     * There was a socket factory here that turned on every protocol the device
     * supported, on the theory that it prevented a version-negotiation stall.
     *
     * It was written from memory and Moonlight has nothing like it. What
     * Moonlight actually does is build a fresh `SSLContext` per request, with a
     * comment saying that is what avoids "the SSLv3 fallback that causes
     * connection failures" — the opposite remedy. Enabling every supported
     * protocol *offers* SSLv3 and TLS 1.0, which is the fallback being guarded
     * against, and leaves the outcome to whatever the two ends negotiate.
     *
     * The platform's own defaults are correct here and are what Moonlight uses.
     */

    /**
     * Offers THOR's one certificate, whatever the server asks for.
     *
     * Every method that would normally choose between identities returns the
     * only one there is. `keyType` and `issuers` are ignored on purpose: they are
     * how the default manager decides *not* to send a self-signed certificate,
     * and that decision is the whole bug this replaces.
     *
     * Extends `X509ExtendedKeyManager` rather than implementing `X509KeyManager`
     * because the engine path — which is what a TLS socket actually uses on
     * Android — calls the `Engine` variants, and a plain implementation would be
     * silently bypassed for exactly the same result.
     */
    private class SingleIdentityKeyManager(
        private val certificate: X509Certificate,
        private val key: PrivateKey,
    ) : X509ExtendedKeyManager() {

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
            arrayOf(ALIAS)

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ) = ALIAS

        override fun chooseEngineClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            engine: SSLEngine?,
        ) = ALIAS

        // THOR is never the server end of one of these connections.
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ) = null

        override fun getCertificateChain(alias: String?) = arrayOf(certificate)

        override fun getPrivateKey(alias: String?) = key

        private companion object {
            const val ALIAS = "thor"
        }
    }

    /**
     * Trusts one certificate: the host's, as it presented it during pairing.
     *
     * Written out rather than accepting everything, because "accept anything on
     * the local network" and "accept the machine we already verified" look
     * identical in a diff and are not the same promise.
     */
    private class PinnedTrustManager(private val expected: X509Certificate) : X509TrustManager {

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val presented = chain?.firstOrNull()
                ?: throw CertificateException("The host presented no certificate")

            /*
             * Pinned to the **public key**, not to the exact certificate bytes.
             *
             * The key is the thing that cannot be forged and the thing that
             * stays constant; the certificate around it carries dates, a serial
             * number and an encoding that a host is free to reissue at any time
             * without becoming a different machine. Comparing whole certificates
             * meant a Sunshine that re-signed its own — on upgrade, or on a
             * config change — was indistinguishable from an impostor, and the
             * only symptom was a handshake that stopped working one day.
             *
             * This is what certificate pinning normally means, and it is no
             * weaker: possession of the key is the claim being checked.
             */
            if (presented.publicKey != expected.publicKey) {
                throw CertificateException(
                    "The host's key does not match the one recorded when pairing",
                )
            }
        }

        // THOR is the client here and never validates one.
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(expected)
    }

    /** One file per host, named by a hash so an address cannot escape the folder. */
    private fun fileFor(address: String) = File(directory, "${address.hashCode()}.crt")

    private companion object {
        const val TAG = "Stream"

        /** Moonlight's own figures; a host that has not answered by then will not. */
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val READ_TIMEOUT_MS = 7_000L

        /**
         * Control traffic is nearly always one request at a time.
         *
         * The limit exists to stop a queue forming, not to allow parallelism —
         * status checks for several hosts are the only case that overlaps, and
         * they are to different hosts anyway.
         */
        const val CONTROL_MAX_REQUESTS = 8

        /** Artwork is many and can wait; the host has better things to do. */
        const val ARTWORK_MAX_REQUESTS = 3

        /** The store never leaves memory, so this guards nothing and is required. */
        val EMPTY_PASSWORD = charArrayOf()
    }
}
