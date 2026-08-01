package com.thor.data.stream

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.model.StreamHost
import com.thor.data.network.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How pairing is going, so the screen can show the PIN and say where it stopped.
 *
 * Each failure names the step that produced it. A handshake with six round trips
 * fails in six different ways, and "pairing failed" is the one message that
 * cannot be acted on — the remedy for a rejected PIN is nothing like the remedy
 * for a host that stopped answering halfway through.
 */
sealed interface PairingState {
    data object Idle : PairingState

    /** The PIN to type into Sunshine on the PC. Shown until the host accepts it. */
    data class AwaitingPin(val pin: String) : PairingState

    data object Verifying : PairingState
    data object Paired : PairingState
    data class Failed(val step: String, val reason: String) : PairingState
}

/**
 * Pairs THOR with a GameStream host.
 *
 * The handshake proves two things at once, in both directions: that the person
 * holding the handheld is also at the PC — by way of a PIN typed there — and
 * that each end is talking to the machine it thinks it is, by way of a challenge
 * neither could answer without the shared secret the PIN derives.
 *
 * It is intricate and it is not ours: this is NVIDIA's protocol as Sunshine
 * implements it, and every hash, order and padding here matches what the other
 * clients send because the host compares bytes rather than intent. The structure
 * mirrors the exchange step by step so a failure can be pointed at.
 *
 * Unencrypted throughout, on port 47989 — the secrecy comes from the PIN and the
 * certificates, not from the transport, and TLS only starts once there is a
 * client certificate the host will accept.
 */
@Singleton
class PairingClient @Inject constructor(
    client: OkHttpClient,
    private val identity: ClientIdentity,
    private val channel: SecureChannel,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * The shared client, retimed for a request that waits on a person.
     *
     * **Sunshine holds the pairing request open until the PIN is entered on the
     * PC.** That is the design — the exchange cannot continue without it — so the
     * response arrives only after someone has read four digits off a handheld,
     * walked to a computer, opened Sunshine and typed them in.
     *
     * THOR's ordinary client gives up after thirty seconds of silence and sixty
     * seconds in total, because it is tuned for API calls that answer or fail.
     * Against this endpoint those limits expire while the user is still crossing
     * the room, and the handshake dies with an `IOException` — reported as
     * "failed while talking to the host", which is true and describes a timeout
     * rather than the wait it actually was.
     *
     * Five minutes, and no overall call cap. A person who has walked away is
     * better served by Cancel than by an arbitrary deadline.
     */
    private val client: OkHttpClient = client.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(PIN_WAIT_MINUTES, TimeUnit.MINUTES)
        .writeTimeout(PIN_WAIT_MINUTES, TimeUnit.MINUTES)
        .cache(null)
        .build()

    /**
     * Runs the handshake, reporting each stage through [onState].
     *
     * @param onPin called as soon as there is a PIN to show. The user has to
     *   carry it to the PC and type it into Sunshine, so it has to appear before
     *   anything waits on it.
     */
    suspend fun pair(
        host: StreamHost,
        clientId: String,
        deviceName: String,
        onPin: (String) -> Unit,
    ): PairingState = withContext(ioDispatcher) {
        val me = identity.get()
        val pin = randomPin()
        val salt = randomBytes(SALT_BYTES)
        val key = deriveKey(salt, pin)

        /*
         * Clear anything left over before starting, always.
         *
         * A handshake that dies partway — a dropped connection, a rejected hash,
         * the app being closed — leaves Sunshine holding a half-open session for
         * this client, and it refuses to begin a new one while that exists. From
         * the handheld that is indistinguishable from the host having broken:
         * the attempt that failed once then fails *at the first step* forever
         * after, including attempts that would otherwise have succeeded.
         *
         * Cancelling was offered only as something the user could press. It
         * belongs here, unconditionally, because the state it clears is left
         * behind by failures rather than by choices — and cancelling a session
         * that does not exist costs one request and means nothing to the host.
         */
        runCatching { get(host, "phrase=cancel", clientId) }

        onPin(pin)
        ThorLog.i(TAG, "Pairing with ${host.address}")

        /*
         * Which round trip is in flight, for the failure to name.
         *
         * The `IOException` handler wraps the whole exchange, so without this it
         * could only report "talking to the host" — true of all six steps and
         * useful for none of them. A connection that drops while offering a
         * certificate means something completely different from one that drops
         * after the PIN was accepted, and the difference is the whole diagnosis.
         */
        var step = "offering our certificate"

        try {
            // ---- 1. Offer our certificate, and take the host's ---------------
            val step1 = get(
                host,
                "devicename=$deviceName&updateState=1&phrase=getservercert" +
                    "&salt=${salt.hex()}&clientcert=${me.certificatePem.toByteArray().hex()}",
                clientId,
            ) ?: return@withContext failed("offering our certificate", "No answer")

            if (step1.textOf("paired") != "1") {
                /*
                 * Names the remedy, which is on the PC rather than here.
                 *
                 * A host refuses at this step when it already holds something
                 * for this client — a session left half-open, or a certificate
                 * from an earlier pairing it still considers current. Neither
                 * can be cleared from the handheld, so an instruction to "try
                 * again" would be advice to repeat the thing that just failed.
                 */
                return@withContext failed(
                    "offering our certificate",
                    "The host refused to begin. Remove this device from Sunshine's " +
                        "client list on the PC, then pair again.",
                )
            }

            val serverCertHex = step1.textOf("plaincert")
                ?: return@withContext failed(
                    "offering our certificate",
                    "The host sent no certificate",
                )
            val serverCert = parseCertificate(serverCertHex.fromHex())
                ?: return@withContext failed(
                    "offering our certificate",
                    "The host's certificate could not be read",
                )

            // ---- 2. Challenge the host --------------------------------------
            step = "challenging the host"
            val clientChallenge = randomBytes(CHALLENGE_BYTES)
            val step2 = get(
                host,
                "devicename=$deviceName&updateState=1" +
                    "&clientchallenge=${encrypt(key, clientChallenge).hex()}",
                clientId,
            ) ?: return@withContext failed("challenging the host", "No answer")

            if (step2.textOf("paired") != "1") {
                return@withContext failed(
                    "challenging the host",
                    "Rejected. Check the PIN was typed correctly.",
                )
            }

            val encryptedResponse = step2.textOf("challengeresponse")?.fromHex()
                ?: return@withContext failed("challenging the host", "No response to the challenge")
            val decoded = decrypt(key, encryptedResponse)

            val serverResponseHash = decoded.copyOfRange(0, HASH_BYTES)
            val serverChallenge = decoded.copyOfRange(HASH_BYTES, HASH_BYTES + CHALLENGE_BYTES)

            // ---- 3. Answer the host's challenge ------------------------------
            step = "answering the host"
            val clientSecret = randomBytes(SECRET_BYTES)

            /*
             * **Our** certificate's signature, not the host's.
             *
             * Each side hashes the other's challenge together with its *own*
             * certificate signature and its own secret — that is what makes the
             * answer something only the holder of that certificate could
             * produce. Hashing the host's signature here instead produced a
             * value the host had no reason to expect and every reason to reject.
             */
            val challengeResponse =
                sha256(serverChallenge + me.certificate.signature + clientSecret)
            val step3 = get(
                host,
                "devicename=$deviceName&updateState=1" +
                    "&serverchallengeresp=${encrypt(key, challengeResponse).hex()}",
                clientId,
            ) ?: return@withContext failed("answering the host", "No answer")

            if (step3.textOf("paired") != "1") {
                return@withContext failed("answering the host", "The host rejected our response")
            }

            val pairingSecret = step3.textOf("pairingsecret")?.fromHex()
                ?: return@withContext failed("answering the host", "The host sent no secret")

            // ---- 4. Verify the host is who its certificate says --------------
            step = "verifying the host"
            val secret = pairingSecret.copyOfRange(0, SECRET_BYTES)
            val signature = pairingSecret.copyOfRange(SECRET_BYTES, pairingSecret.size)

            if (!verify(serverCert, secret, signature)) {
                return@withContext failed(
                    "verifying the host",
                    "Its signature did not match its certificate",
                )
            }

            /*
             * The mirror of the hash we sent: **our** challenge, the **host's**
             * signature, the host's secret.
             *
             * Both halves of this were wrong — it hashed the host's challenge
             * against our own signature, which is neither side's computation.
             * The comparison could therefore never succeed, and the message it
             * produced blamed the PIN: the one part of the exchange the user
             * controls, and the one part that was correct. A PIN typed perfectly
             * failed every time with an explanation pointing at the typing.
             */
            val expected = sha256(clientChallenge + serverCert.signature + secret)
            if (!expected.contentEquals(serverResponseHash)) {
                /*
                 * The PIN is wrong, and this is where that is discovered.
                 *
                 * Everything up to here succeeds with any PIN — the exchange is
                 * symmetric and both sides are simply encrypting with keys they
                 * believe match. It is only when the hashes are compared that a
                 * mismatch appears, which is why the message names the PIN
                 * rather than the step.
                 */
                return@withContext failed(
                    "verifying the host",
                    "The PIN did not match. Try again with a new one.",
                )
            }

            // ---- 5. Send our own secret, signed -------------------------------
            step = "completing the pairing"
            val step5 = get(
                host,
                "devicename=$deviceName&updateState=1" +
                    "&clientpairingsecret=${(clientSecret + sign(clientSecret)).hex()}",
                clientId,
            ) ?: return@withContext failed("completing the pairing", "No answer")

            if (step5.textOf("paired") != "1") {
                return@withContext failed(
                    "completing the pairing",
                    "The host rejected our secret",
                )
            }

            // ---- 6. Introduce ourselves over TLS ------------------------------
            step = "confirming the pairing"

            /*
             * The step that makes it stick, and its absence is invisible.
             *
             * Everything above can succeed — five exchanges, every one answering
             * `paired=1` — and the host still lists the client as unpaired,
             * because none of it has yet been done over a connection presenting
             * the certificate all of it was about. This final request is the
             * first time THOR uses the credential rather than negotiating it, and
             * completing it is what moves the client from "mid-pairing" to
             * "paired" on the host.
             *
             * Its omission produced the most misleading state of the lot: no
             * error anywhere, a handshake that reported success at every stage,
             * and a screen that went on saying "Not paired" — correctly, because
             * it asks the host rather than trusting the handshake.
             */
            val confirmed = secureGet(
                host = host,
                serverCert = serverCert,
                query = "devicename=$deviceName&updateState=1&phrase=pairchallenge",
                clientId = clientId,
            )

            if (confirmed?.textOf("paired") != "1") {
                return@withContext failed(
                    "confirming the pairing",
                    "The host accepted the exchange but would not confirm it over TLS",
                )
            }

            // Kept so every later request can be pinned to the machine we just
            // verified, rather than to the public roots that never vouched for it.
            channel.remember(host.address, serverCert)
            ThorLog.i(TAG, "Paired with ${host.address}")
            PairingState.Paired
        } catch (e: IOException) {
            ThorLog.w(TAG, "Pairing failed while $step", e)
            failed(step, e.message?.take(90) ?: "The connection dropped")
        } catch (e: GeneralSecurityException) {
            /*
             * The host's reply would not decrypt or verify.
             *
             * Almost always the derived key, which means the PIN — the exchange
             * is symmetric, so a wrong PIN produces bytes that are perfectly
             * well-formed and decrypt to nonsense rather than failing outright.
             * Named separately because it escaped to the caller before and was
             * reported as "failed while pairing", which points at nothing.
             */
            ThorLog.w(TAG, "Pairing failed while $step", e)
            failed(step, "The reply could not be decrypted — check the PIN")
        } catch (e: IndexOutOfBoundsException) {
            // A reply shorter than the protocol requires: a host speaking a
            // version of this exchange THOR does not know how to read.
            ThorLog.w(TAG, "Pairing failed while $step", e)
            failed(step, "The host's reply was shorter than expected")
        } catch (e: IllegalArgumentException) {
            // Hex that is not hex, usually an error page where XML was expected.
            ThorLog.w(TAG, "Pairing failed while $step", e)
            failed(step, "The host's reply could not be read")
        }
    }

    /**
     * Tells the host to forget an in-progress attempt.
     *
     * A handshake abandoned halfway leaves Sunshine waiting, and the next attempt
     * is refused at step one because "another pairing is in progress" — which
     * looks like a broken host rather than an unfinished conversation.
     */
    suspend fun cancel(host: StreamHost, clientId: String) = withContext(ioDispatcher) {
        runCatching { get(host, "phrase=cancel", clientId) }
        Unit
    }

    private suspend fun get(host: StreamHost, query: String, clientId: String): String? {
        val url = "http://${host.address}:${StreamHostClient.HTTP_PORT}" +
            "/pair?uniqueid=$clientId&uuid=${randomBytes(UUID_BYTES).hex()}&$query"

        return client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    /**
     * The same request, over TLS, presenting our certificate.
     *
     * The client certificate is the point: it is the credential the whole
     * handshake exists to establish, and the host only records the pairing once
     * it has seen it used. The socket itself comes from [SecureChannel], which
     * the status check uses too — building it twice invites the second one to be
     * built slightly differently, and the difference would be silent.
     */
    private suspend fun secureGet(
        host: StreamHost,
        serverCert: X509Certificate,
        query: String,
        clientId: String,
    ): String? {
        val secure = channel.clientFor(serverCert)
        val url = "https://${host.address}:$HTTPS_PORT" +
            "/pair?uniqueid=$clientId&uuid=${randomBytes(UUID_BYTES).hex()}&$query"

        return secure.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun failed(step: String, reason: String) = PairingState.Failed(step, reason)

    private class GeneralPairingError(val step: String, message: String) : Exception(message)

    private companion object {
        const val TAG = "Stream"

        const val SALT_BYTES = 16
        const val CHALLENGE_BYTES = 16
        const val SECRET_BYTES = 16
        const val HASH_BYTES = 32
        const val UUID_BYTES = 16

        /**
         * How long to wait for a PIN to be typed on the PC.
         *
         * Generous because the limit is a person walking to another room, not a
         * server thinking. Cancel is the way out of a wait, not a deadline.
         */
        const val PIN_WAIT_MINUTES = 5L

        /** Where a paired client talks; 47989 is only for negotiating. */
        const val HTTPS_PORT = 47984

        /** The store never leaves memory, so this guards nothing and is required. */
        val KEYSTORE_PASSWORD = charArrayOf()

        /**
         * SHA-256 of salt and PIN, truncated to an AES-128 key.
         *
         * SHA-256 rather than SHA-1: that is what protocol version 7 and later
         * use, which is every Sunshine build and every GameStream host since
         * 2019. The truncation is part of the specification rather than a
         * shortcut — the cipher is AES-128 whatever the digest length.
         */
        fun deriveKey(salt: ByteArray, pin: String): ByteArray =
            sha256(salt + pin.toByteArray()).copyOfRange(0, 16)

        fun sha256(input: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(input)

        /**
         * ECB, which is normally the wrong answer and is the right one here.
         *
         * The protocol encrypts single fixed-size blocks with no chaining, and a
         * mode with an IV would produce bytes the host cannot read. Choosing
         * differently would be choosing not to interoperate.
         */
        fun cipher(mode: Int, key: ByteArray): Cipher =
            Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"))
            }

        fun encrypt(key: ByteArray, data: ByteArray): ByteArray =
            cipher(Cipher.ENCRYPT_MODE, key).doFinal(data.padToBlock())

        fun decrypt(key: ByteArray, data: ByteArray): ByteArray =
            cipher(Cipher.DECRYPT_MODE, key).doFinal(data)

        /** NoPadding means the caller supplies whole blocks; short input is zero-filled. */
        fun ByteArray.padToBlock(): ByteArray {
            if (size % 16 == 0) return this
            return copyOf(((size / 16) + 1) * 16)
        }

        fun randomBytes(count: Int): ByteArray =
            ByteArray(count).also { SecureRandom().nextBytes(it) }

        /** Four digits, because that is what Sunshine's box accepts. */
        fun randomPin(): String = (0 until 4)
            .joinToString("") { SecureRandom().nextInt(10).toString() }

        fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }

        fun String.fromHex(): ByteArray = trim()
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        fun String.textOf(tag: String): String? =
            Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.trim()

        fun parseCertificate(der: ByteArray): X509Certificate? = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(der.inputStream()) as X509Certificate
        }.getOrNull()

        fun verify(cert: X509Certificate, data: ByteArray, signature: ByteArray): Boolean =
            runCatching {
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(cert)
                    update(data)
                    verify(signature)
                }
            }.getOrDefault(false)
    }

    private fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(identity.get().privateKey)
            update(data)
            sign()
        }
}
