package com.thor.data.stream

import com.limelight.nvstream.jni.MoonBridge
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.model.StreamApp
import com.thor.core.model.StreamHost
import com.thor.core.model.SessionQuality
import com.thor.data.network.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a host says about itself when it is about to be streamed from.
 *
 * Read fresh at launch rather than reused from the status check. These three
 * fields decide how the core talks to the machine — which protocol dialect,
 * which codecs it will accept — and a value cached from minutes ago describes a
 * host that may since have been updated or reconfigured.
 */
data class ServerInfo(
    /** The host's protocol version, as `major.minor.build.revision`. */
    val appVersion: String,
    /** GeForce Experience's version. Blank on Sunshine, which is not GFE. */
    val gfeVersion: String,
    /** Bitmask of the codecs and modes the host will encode in. */
    val codecModeSupport: Int,
    /** The app id already streaming, or null when idle. */
    val currentGame: String?,
)

/**
 * A session the host has agreed to, and the secrets that address it.
 *
 * Produced by `/launch` or `/resume` and consumed by the streaming core. The key
 * and its id are generated here and sent to the host in the same request, which
 * is what lets the two ends encrypt the control stream without a further
 * exchange.
 */
data class LaunchedSession(
    val host: StreamHost,
    val app: StreamApp,
    val server: ServerInfo,
    /** Where RTSP negotiation begins. Absent on older hosts, which imply it. */
    val rtspSessionUrl: String,
    val riKey: ByteArray,
    val riKeyId: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    /** The settings this session was agreed under. */
    val quality: SessionQuality,
    /** True when the host was already running this app and picked it back up. */
    val resumed: Boolean,
) {
    /**
     * The AES initialisation vector, which is the key id in sixteen bytes.
     *
     * Big-endian in the first four and zero in the rest — not a nonce, despite
     * the name, but a value both ends derive identically from something they
     * have already agreed. Written out because getting the width or the byte
     * order wrong produces a session that connects and then decodes nothing.
     */
    val riKeyIv: ByteArray get() = ByteBuffer.allocate(IV_BYTES).putInt(riKeyId).array()

    /*
     * Arrays make the generated `equals` compare identity, which would make two
     * descriptions of the same session unequal. Nothing compares these, so the
     * honest thing is to say so rather than to write a comparison nobody uses.
     */
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    private companion object {
        const val IV_BYTES = 16
    }
}

/** Why a launch did not happen, in words the panel can show. */
class LaunchFailure(message: String) : Exception(message)

/**
 * Which part of a launch is happening.
 *
 * Reported to the screen because the two steps fail for entirely different
 * reasons and take entirely different lengths of time — one is a quick question
 * and the other waits for a PC to start a game. "Starting…" covering both means
 * a stall says nothing about where it stalled.
 */
enum class LaunchStage {
    /** Asking the host what it is, over the paired connection. */
    ASKING_HOST,

    /** Waiting for the host to actually start the game. */
    STARTING_GAME,
}

/**
 * Asks a host to start streaming something.
 *
 * The last HTTP step before the streaming core takes over: everything after this
 * happens over RTSP and UDP, in C. Split from [StreamHostClient] because the
 * requests differ in kind — one asks how a machine is, the other changes what it
 * is doing — and because launching has to survive a much longer wait.
 */
@Singleton
class StreamLauncher @Inject constructor(
    private val client: OkHttpClient,
    private val channel: SecureChannel,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Starts [app] on [host], or picks up the session already running.
     *
     * Chooses between `launch` and `resume` from what the host reports rather
     * than from what the caller expects: a host mid-session refuses `launch`
     * outright, and the user pressing play on the game already running means
     * "put it back on my screen".
     */
    suspend fun start(
        host: StreamHost,
        app: StreamApp,
        clientId: String,
        quality: SessionQuality,
        onStage: (LaunchStage) -> Unit = {},
    ): LaunchedSession = withContext(ioDispatcher) {
        val serverCert = channel.certificateFor(host.address)
            ?: throw LaunchFailure("This PC is not paired with Loki any more. Pair it again.")

        onStage(LaunchStage.ASKING_HOST)
        val server = serverInfo(host, clientId, serverCert)

        // The key is per session, never stored, and never leaves this exchange.
        val riKey = KeyGenerator.getInstance("AES")
            .apply { init(RI_KEY_BITS, SecureRandom()) }
            .generateKey()
            .encoded
        val riKeyId = SecureRandom().nextInt()

        val resuming = server.currentGame != null
        if (resuming && server.currentGame != app.id) {
            /*
             * Refused here rather than by the host.
             *
             * A GameStream host runs one session at a time. Asking it to launch
             * something else does not queue or switch — it fails, and it can
             * also leave the running session in a state the user then has to go
             * to the PC to fix. Saying no is the kinder answer, and the panel
             * already warns before it gets this far.
             */
            throw LaunchFailure(
                "This PC is already streaming something else. Stop that session first.",
            )
        }

        val verb = if (resuming) "resume" else "launch"
        onStage(LaunchStage.STARTING_GAME)
        val body = request(host, app, clientId, serverCert, riKey, riKeyId, quality, verb)

        /*
         * The host answers "0" for refusal rather than failing the request.
         *
         * An HTTP 200 carrying `<gamesession>0</gamesession>` is a no, and
         * treating the status code as the answer would have THOR proceed into a
         * session the host never created — which surfaces much later as RTSP
         * timing out against nothing.
         */
        val started = body.textOf(if (resuming) "resume" else "gamesession")
        if (started == null || started == "0") {
            throw LaunchFailure(
                body.textOf("message")?.takeIf(String::isNotBlank)
                    ?: "The PC refused to start it.",
            )
        }

        LaunchedSession(
            host = host,
            app = app,
            server = server,
            // Missing on older hosts, which expect the client to assume the
            // default RTSP endpoint rather than be told it.
            rtspSessionUrl = body.textOf("sessionUrl0").orEmpty(),
            riKey = riKey,
            riKeyId = riKeyId,
            width = quality.width,
            height = quality.height,
            fps = quality.fps,
            bitrateKbps = quality.bitrateKbps,
            quality = quality,
            resumed = resuming,
        )
    }

    /** Tells the host to end whatever it is streaming. */
    suspend fun quit(host: StreamHost, clientId: String): Boolean = withContext(ioDispatcher) {
        val serverCert = channel.certificateFor(host.address) ?: return@withContext false

        runCatching {
            val url = "https://${host.address}:${StreamHostClient.HTTPS_PORT}/cancel" +
                "?uniqueid=$clientId&uuid=${randomUuid()}"
            channel.clientFor(serverCert)
                .newCall(Request.Builder().url(url).build())
                .await()
                .use { it.isSuccessful }
        }.onFailure { ThorLog.w(TAG, "Could not stop the session", it) }.getOrDefault(false)
    }

    /**
     * Runs one request under a deadline it cannot outlive.
     *
     * The conversion to [LaunchFailure] is the point, not the timeout. A
     * `withTimeout` throws `TimeoutCancellationException`, which **is** a
     * `CancellationException` — and the launcher's `launchSafely` rethrows those
     * rather than reporting them, precisely so structured concurrency keeps
     * working. Left as it comes, a timeout would therefore skip every error
     * handler and leave the screen stuck exactly as an unbounded wait does: the
     * bound would be real and completely invisible.
     */
    private suspend fun <T> bounded(
        timeoutMs: Long,
        onTimeout: String,
        block: suspend () -> T,
    ): T = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        ThorLog.w(TAG, "Timed out: $onTimeout", e)
        throw LaunchFailure(onTimeout)
    }

    /**
     * A client built for exactly one request.
     *
     * Moonlight builds a fresh `SSLContext` and socket factory for every socket
     * it opens, with a comment saying it is required to avoid a fallback that
     * causes connection failures on Android. Reusing one client across the two
     * requests a launch makes was tempting and is the thing that reference
     * implementation specifically warns against.
     *
     * @param readTimeoutMs generous for a launch, ordinary for a question. A
     *   launch does not answer until the PC has started the game, so the usual
     *   seven seconds would abandon one that was going perfectly well.
     */
    private fun clientFor(serverCert: java.security.cert.X509Certificate, readTimeoutMs: Long) =
        channel.clientFor(serverCert).newBuilder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private suspend fun serverInfo(
        host: StreamHost,
        clientId: String,
        serverCert: java.security.cert.X509Certificate,
    ): ServerInfo {
        /*
         * TLS first, then plainly — which is what Moonlight does, and the part
         * THOR was missing.
         *
         * `getServerInfo` asks over HTTPS when it holds a pinned certificate and
         * drops to the unencrypted port when that fails. THOR only ever asked
         * over TLS, so a single stalled handshake ended the launch with "the PC
         * stopped answering" while the machine was sitting there answering
         * perfectly well on 47989.
         *
         * The fallback costs nothing in fidelity. Everything a launch needs from
         * this response — the version, the codec modes, whatever is already
         * running — is served identically on both ports. The one field that is
         * not is `PairStatus`, which the unencrypted port reports as zero for
         * everybody, and which is of no interest here: reaching this code means
         * a certificate is already stored and the app list has already been read
         * over the paired connection.
         */
        val secure = runCatching {
            fetch(
                clientFor(serverCert, SERVER_INFO_TIMEOUT_MS),
                "https://${host.address}:${StreamHostClient.HTTPS_PORT}/serverinfo" +
                    "?uniqueid=$clientId&uuid=${randomUuid()}",
            )
        }.onFailure { ThorLog.w(TAG, "serverinfo over TLS failed; trying 47989", it) }
            .getOrNull()

        val xml = secure ?: fetch(
            client,
            "http://${host.address}:${StreamHostClient.HTTP_PORT}/serverinfo" +
                "?uniqueid=$clientId&uuid=${randomUuid()}",
        )

        val appVersion = xml.textOf("appversion")
            ?: throw LaunchFailure("The PC did not report a version, so Loki cannot talk to it")

        return ServerInfo(
            appVersion = appVersion,
            gfeVersion = xml.textOf("GfeVersion").orEmpty(),
            /*
             * Absent on hosts old enough not to have the concept, and zero is
             * the right reading of that: no advertised modes, so the core falls
             * back on what the version alone implies.
             */
            codecModeSupport = xml.textOf("ServerCodecModeSupport")?.toIntOrNull() ?: 0,
            currentGame = xml.textOf("currentgame")?.takeIf { it.isNotBlank() && it != "0" },
        )
    }

    /**
     * One `/serverinfo` request, on whichever port the caller built a client for.
     *
     * Bounded far more tightly than the launch itself: this is a question the
     * host answers from memory — the same one the status check answers in a
     * couple of seconds — so spending the launch budget on it would mean two
     * minutes to discover a PC is unreachable.
     */
    private suspend fun fetch(http: OkHttpClient, url: String): String = bounded(
        timeoutMs = SERVER_INFO_TIMEOUT_MS,
        onTimeout = "The PC stopped answering on both ports. It may have gone to " +
            "sleep, or the connection dropped.",
    ) {
        http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) {
                throw LaunchFailure("The PC would not describe itself (HTTP ${response.code})")
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun request(
        host: StreamHost,
        app: StreamApp,
        clientId: String,
        serverCert: java.security.cert.X509Certificate,
        riKey: ByteArray,
        riKeyId: Int,
        quality: SessionQuality,
        verb: String,
    ): String {
        val http = clientFor(serverCert, LAUNCH_TIMEOUT_MS)
        /*
         * The core contributes query parameters of its own.
         *
         * Which ones depends on how it was compiled and what it supports, so
         * they are asked for rather than written out — a hand-copied list would
         * be correct until the vendored core was next updated, and then
         * silently wrong.
         */
        val fromCore = MoonBridge.getLaunchUrlQueryParameters().orEmpty()

        val url = buildString {
            append("https://${host.address}:${StreamHostClient.HTTPS_PORT}/$verb")
            append("?uniqueid=$clientId&uuid=${randomUuid()}")
            append("&appid=${app.id}")
            append("&mode=${quality.width}x${quality.height}x${quality.fps}")
            append("&additionalStates=1")
            /*
             * SOPS off.
             *
             * "Optimal playable settings" lets the host rewrite the game's own
             * graphics options to suit the stream. It is a reasonable default
             * for a PC monitor and a poor one here: it silently changes settings
             * the user chose, on their own machine, and only NVIDIA's host ever
             * implemented it properly.
             */
            append("&sops=${if (quality.optimizeGameSettings) 1 else 0}")
            append("&rikey=${riKey.hex()}")
            append("&rikeyid=$riKeyId")
            if (quality.enableHdr) {
                append(
                    "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0" +
                        "&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1" +
                        "&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0",
                )
            }
            append("&localAudioPlayMode=${if (quality.playAudioOnHost) 1 else 0}")
            append("&surroundAudioInfo=${quality.audio.surroundInfo}")
            /*
             * One gamepad, announced up front.
             *
             * This said zero, on the reasoning that the core reports controllers
             * as they arrive. That is true of a pad plugged in mid-session and
             * wrong for this device: the handheld's controls are built in and
             * present before the session exists, and a host told that no
             * controllers are attached does not create a virtual pad for the
             * game to find. The stream then runs perfectly with every button
             * press going nowhere.
             *
             * `gcpersist=0` so the virtual pad goes away with the session rather
             * than lingering on the PC afterwards.
             */
            append("&remoteControllersBitmap=1&gcmap=1&gcpersist=0")
            append(fromCore)
        }

        ThorLog.i(TAG, "$verb ${app.title} on ${host.address}")

        return bounded(
            timeoutMs = LAUNCH_TIMEOUT_MS,
            onTimeout = "The PC never finished starting it. It may still be launching — " +
                "check the PC, then try again.",
        ) {
            http.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    throw LaunchFailure("The PC refused to start it (HTTP ${response.code})")
                }
                response.body?.string().orEmpty()
            }
        }
    }

    private companion object {
        const val TAG = "Stream"

        /** GameStream's control-stream cipher is AES-128; the size is not a choice. */
        const val RI_KEY_BITS = 128

        /**
         * Long enough for any real game to start, short enough to give up.
         *
         * Two minutes covers a cold Steam launch on a PC waking its GPU, which
         * is the slowest honest case. Past that the host is not starting it.
         */
        const val LAUNCH_TIMEOUT_MS = 120_000L

        /** The same question the status check asks, and answered as quickly. */
        const val SERVER_INFO_TIMEOUT_MS = 15_000L

        /**
         * Two channels, front left and front right.
         *
         * The handheld has stereo speakers and a stereo headphone jack, so
         * asking for surround would mean the host encoding channels that are
         * then mixed back down — more bandwidth for the same sound.
         */
        const val STEREO_SURROUND_INFO = 0x30002

        fun randomUuid(): String = java.util.UUID.randomUUID().toString().replace("-", "")

        fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

        fun String.textOf(tag: String): String? =
            Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.trim()
    }
}
