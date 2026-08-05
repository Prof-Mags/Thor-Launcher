package com.thor.data.stream

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamApp
import com.thor.core.model.StreamHost
import com.thor.data.network.await
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a GameStream host about itself.
 *
 * `/serverinfo` is the protocol's introduction: it answers before any pairing
 * has happened, over plain HTTP on 47989, and says what the machine is called,
 * whether *this* client is paired with it, and whether it is already streaming
 * something. Everything the Stream screen shows before a host is set up comes
 * from this one request.
 *
 * XML rather than JSON, because that is what the protocol speaks — it was
 * designed around NVIDIA's own client and never modernised. Read with the
 * platform's pull parser, as the Torznab feed is, rather than pulling in a
 * dependency to model a handful of elements.
 */
@Singleton
class StreamHostClient @Inject constructor(
    private val client: OkHttpClient,
    private val channel: SecureChannel,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * What [host] says about itself right now.
     *
     * Never throws: an unreachable PC is the ordinary case — it is asleep, or on
     * another network, or Sunshine is not running — and the screen wants to say
     * which rather than show an error.
     */
    suspend fun status(host: StreamHost, clientId: String): HostStatus =
        withContext(ioDispatcher) {
            if (!host.isUsable) return@withContext HostStatus.Offline("No address")

            /*
             * Asked over TLS once there is a certificate to ask with.
             *
             * `PairStatus` is a statement about *this client*, and the plain HTTP
             * port carries nothing that identifies one — so a host answers it
             * with zero for everybody, forever, however thoroughly they are
             * paired. Querying only that port made a completed pairing report
             * itself as not paired, with no error anywhere and nothing to
             * suggest the question had been asked on a channel incapable of
             * answering it.
             *
             * The unencrypted port is still the right one before pairing: there
             * is no certificate to present, and it is how a host is found to
             * exist at all.
             */
            val serverCert = channel.certificateFor(host.address)

            /*
             * Tried over TLS first, then plainly — and the fallback matters.
             *
             * A stored certificate means THOR paired with this host once, not
             * that it still is: unpairing happens on the PC, and the first thing
             * the user knows about it is the TLS handshake being refused. Left
             * there, a perfectly reachable machine would report itself offline
             * for a reason that is not a network problem at all.
             *
             * Falling back to the unencrypted port answers the question that is
             * still answerable — the host is up, and this client is not paired —
             * which is exactly what the screen needs to say.
             */
            var tlsProblem: String? = null

            if (serverCert != null) {
                val secure = runCatching {
                    // Its own, longer budget. A TLS handshake presenting a client
                    // certificate costs a round trip and an RSA signature on top
                    // of the request, and over a VPN to a sleeping PC that is
                    // comfortably more than a plain GET takes.
                    withTimeout(SECURE_TIMEOUT_MS) {
                        query(channel.clientFor(serverCert), host, clientId, secure = true)
                    }
                }.onFailure { error ->
                    /*
                     * The exception's type as well as its text.
                     *
                     * A TLS failure's message is often empty or unhelpful —
                     * `SSLHandshakeException` with nothing after it is common —
                     * while the class name alone separates the cases that
                     * matter: a peer that rejected us, a certificate that did
                     * not verify, a socket that closed. Truncating to the
                     * message threw away the more informative half.
                     */
                    ThorLog.w(TAG, "${host.address}: TLS query failed", error)
                    tlsProblem = listOfNotNull(
                        error::class.simpleName,
                        error.message?.takeIf(String::isNotBlank),
                        error.cause?.message?.takeIf(String::isNotBlank),
                    ).joinToString(": ").take(160)
                }.getOrNull()

                if (secure is HostStatus.Online) return@withContext secure

                /*
                 * Ask the port itself, because "timeout" has two causes and only
                 * one of them is anything to do with TLS.
                 *
                 * A refused connection is a machine saying no. A timeout is
                 * packets leaving and nothing coming back, which is a firewall
                 * dropping them silently — and on Windows that is the normal
                 * outcome for a VPN interface, because Sunshine's rules are
                 * added for the private network profile and Tailscale is
                 * classified as public. Port 47989 being open while 47984 is not
                 * is exactly what that looks like from here.
                 *
                 * One bare socket settles it, and turns an unactionable
                 * "timeout" into an instruction about a firewall.
                 */
                tlsProblem = describeReachability(host.address, tlsProblem)
                ThorLog.i(TAG, "${host.address}: TLS query failed ($tlsProblem); asking unpaired")
            }

            try {
                withTimeout(TIMEOUT_MS) {
                    query(client, host, clientId, secure = false).let { status ->
                        /*
                         * Says that the *check* failed, not that the pairing did.
                         *
                         * Reaching here with a stored certificate means THOR
                         * paired with this host and then could not confirm it
                         * over TLS — and the unencrypted port reports every
                         * client as unpaired, so without this the screen states
                         * the opposite of what is true and offers pairing again
                         * to someone already listed on their PC.
                         */
                        if (status is HostStatus.Online && serverCert != null) {
                            status.copy(
                                paired = true,
                                note = "Paired, but this device could not confirm it " +
                                    "over TLS: ${tlsProblem ?: "no answer on 47984"}",
                            )
                        } else {
                            status
                        }
                    }
                }
            } catch (e: IOException) {
                /*
                 * Named rather than generalised to "offline".
                 *
                 * "Connection refused" means the machine is there and Sunshine
                 * is not; a timeout means the machine is not answering at all;
                 * "unable to resolve host" means the address is wrong. Three
                 * different things to go and do, and one word for all of them
                 * sends the user to check the wrong one.
                 */
                ThorLog.w(TAG, "${host.address}: unreachable", e)
                HostStatus.Offline(e.shortReason())
            } catch (e: TimeoutCancellationException) {
                HostStatus.Offline("No answer — is the PC awake?")
            } catch (e: IllegalArgumentException) {
                ThorLog.w(TAG, "${host.address}: unreadable reply", e)
                HostStatus.Offline("Answered, but not like a GameStream host")
            } catch (e: CancellationException) {
                // The caller gave up, which is not an answer about the host.
                throw e
            } catch (e: Exception) {
                /*
                 * Anything else, still reported as an answer rather than thrown.
                 *
                 * The contract above is that this never throws, and it held only
                 * for the three failures that had been thought of. The rest of
                 * the stack can fail in other ways — the client certificate is
                 * generated and presented by a crypto library with exceptions of
                 * its own — and one of those would leave the section to turn it
                 * into a status, which it does without a log and without knowing
                 * which host it was asking about.
                 */
                ThorLog.w(TAG, "${host.address}: check failed", e)
                HostStatus.Offline(e.shortReason())
            }
        }

    /**
     * Whether port 47984 accepts a connection at all, said in words.
     *
     * Separates the two failures a timeout hides: a port nothing can reach, and a
     * port that connects but will not complete a TLS handshake. The remedies are
     * a firewall rule and a certificate problem respectively, and they have
     * nothing in common.
     */
    private suspend fun describeReachability(address: String, tlsProblem: String?): String =
        withContext(ioDispatcher) {
            val reachable = runCatching {
                withTimeout(PROBE_TIMEOUT_MS) {
                    java.net.Socket().use { socket ->
                        socket.connect(
                            java.net.InetSocketAddress(address, HTTPS_PORT),
                            PROBE_TIMEOUT_MS.toInt(),
                        )
                        true
                    }
                }
            }.getOrDefault(false)

            if (reachable) {
                "port 47984 is open but the secure handshake failed" +
                    (tlsProblem?.let { " ($it)" } ?: "")
            } else {
                "nothing is answering on port 47984, though 47989 answers"
            }
        }

    /**
     * The host's library.
     *
     * Only available over TLS, and not because of secrecy: the list is per
     * client, so a host cannot answer it without knowing who is asking. A caller
     * with no stored certificate has not paired and has nothing to ask with.
     *
     * @return the apps, or null when the host could not be asked. Null and empty
     *   are different answers — one is "could not reach it", the other is "this
     *   PC has nothing configured to stream" — and the screen says which.
     */
    suspend fun apps(host: StreamHost, clientId: String): List<StreamApp>? =
        withContext(ioDispatcher) {
            val serverCert = channel.certificateFor(host.address)
                ?: return@withContext null

            /*
             * Carries a fresh `uuid`, as every other request in this protocol
             * does.
             *
             * It identifies the request rather than the client — Moonlight
             * generates a new one per call and the pairing handshake here already
             * does the same. NVIDIA's own host refuses requests without it, and a
             * host that ignores it costs nothing.
             */
            val url = "https://${host.address}:$HTTPS_PORT/applist" +
                "?uniqueid=$clientId&uuid=${UUID.randomUUID().toString().replace("-", "")}"

            try {
                withTimeout(SECURE_TIMEOUT_MS) {
                    channel.clientFor(serverCert)
                        .newCall(Request.Builder().url(url).build())
                        .await()
                        .use { response ->
                            if (!response.isSuccessful) {
                                ThorLog.w(TAG, "applist: HTTP ${response.code}")
                                null
                            } else {
                                parseApps(response.body?.string().orEmpty())
                            }
                        }
                }
            } catch (e: IOException) {
                ThorLog.w(TAG, "${host.address}: could not read the app list", e)
                null
            } catch (e: TimeoutCancellationException) {
                ThorLog.w(TAG, "${host.address}: app list timed out", e)
                null
            }
        }

    /**
     * Reads `<App>` entries out of the reply.
     *
     * Matched as whole blocks first and then field by field, rather than
     * collecting every `<ID>` and every `<AppTitle>` into parallel lists: those
     * lists go out of step the moment one app is missing a field, and the result
     * is a library where the titles belong to the wrong games.
     */
    private fun parseApps(xml: String): List<StreamApp> =
        APP_BLOCK.findAll(xml).mapNotNull { match ->
            val block = match.value
            val id = block.textOf("ID")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = block.textOf("AppTitle")?.takeIf(String::isNotBlank) ?: "App $id"

            StreamApp(
                id = id,
                title = title,
                hdr = block.textOf("IsHdrSupported") == "1",
            )
        }.toList()

    /** One `/serverinfo` request, on whichever port the caller has credentials for. */
    private suspend fun query(
        httpClient: OkHttpClient,
        host: StreamHost,
        clientId: String,
        secure: Boolean,
    ): HostStatus {
        val url = if (secure) {
            "https://${host.address}:$HTTPS_PORT/serverinfo?uniqueid=$clientId"
        } else {
            "http://${host.address}:$HTTP_PORT/serverinfo?uniqueid=$clientId"
        }

        return httpClient.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) {
                HostStatus.Offline("HTTP ${response.code}")
            } else {
                parse(response.body?.string().orEmpty())
            }
        }
    }

    /**
     * Reads the fields the screen needs, and ignores the rest.
     *
     * A `serverinfo` reply carries display modes, codec support, GPU details and
     * more; none of it matters until something is actually being streamed, and
     * modelling it now would be a large type that has to change the first time a
     * host version differs.
     */
    private fun parse(xml: String): HostStatus {
        if (xml.isBlank()) return HostStatus.Offline("Empty reply")

        val hostname = xml.textOf("hostname")
        val paired = xml.textOf("PairStatus") == "1"

        /*
         * `currentgame` is 0 when idle, and an app id when not.
         *
         * A host mid-session can be resumed but not launched into, and finding
         * that out from a refusal after pressing play is worse than being told
         * before.
         */
        val current = xml.textOf("currentgame")?.takeIf { it.isNotBlank() && it != "0" }

        return HostStatus.Online(
            name = hostname?.takeIf(String::isNotBlank) ?: "GameStream host",
            paired = paired,
            currentGame = current,
        )
    }

    companion object {
        private const val TAG = "Stream"

        /** Unencrypted, and only until pairing; paired traffic moves to 47984. */
        const val HTTP_PORT = 47989

        /**
         * Where a paired client asks, presenting its certificate.
         *
         * The distinction is not about secrecy. A host cannot say whether *this*
         * client is paired unless the connection says who this client is, and
         * only the TLS port carries that.
         */
        const val HTTPS_PORT = 47984

        /** Long enough for a PC waking from sleep, short enough to feel answered. */
        private const val TIMEOUT_MS = 6_000L

        /**
         * Longer, because a TLS handshake is not a plain GET.
         *
         * Presenting a client certificate costs an extra round trip and an RSA
         * signature, and four seconds — enough for the unencrypted query —
         * expired before the secure one finished. The fallback then reported the
         * host as unpaired, which the unencrypted port says about everybody.
         */
        private const val SECURE_TIMEOUT_MS = 12_000L

        /** A bare TCP connect, only to tell a blocked port from a TLS problem. */
        private const val PROBE_TIMEOUT_MS = 3_000L

        /** One `<App>` element, so its fields cannot be paired with a neighbour's. */
        private val APP_BLOCK = Regex("<App>.*?</App>", RegexOption.DOT_MATCHES_ALL)

        /**
         * The identity THOR presents to a host.
         *
         * Generated once and kept, because pairing is bound to it: a client that
         * announced a new id each time would be a new, unpaired device on every
         * launch, and the user would be typing PINs forever.
         */
        fun newClientId(): String = UUID.randomUUID().toString().replace("-", "")

        /**
         * The text of the first matching element.
         *
         * A regex over the document rather than a parse into a tree, and that is
         * a deliberate limit: only three flat fields are wanted, none of them
         * repeats, and none can nest. Anything that needed structure would want
         * the pull parser instead.
         */
        private fun String.textOf(tag: String): String? =
            Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.unescapeXml()

        /**
         * Turns the five XML entities back into the characters they stand for.
         *
         * Game titles are user-facing text written by whoever set up Sunshine, and
         * ampersands and apostrophes are ordinary in them — "Assassin's Creed",
         * "Command &amp; Conquer". The host escapes them because it must, and
         * reading the document with a regex means nothing unescapes them again, so
         * without this the library lists games by names nobody gave them.
         *
         * `&amp;` is undone last: doing it first would turn `&amp;lt;` — a
         * literal "&lt;" in a title — into a tag.
         */
        private fun String.unescapeXml(): String = replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")

        private fun Throwable.shortReason(): String =
            message?.lineSequence()?.firstOrNull()?.trim()?.take(60)
                ?: "Could not reach it"
    }
}
