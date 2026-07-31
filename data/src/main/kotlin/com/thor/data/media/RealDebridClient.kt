package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.data.network.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user's debrid account is usable, and what to say when it is not. */
sealed interface DebridStatus {
    data object NotConfigured : DebridStatus
    data class Connected(val username: String, val daysRemaining: Int?) : DebridStatus
    data object InvalidToken : DebridStatus
    data class Error(val reason: String) : DebridStatus
}

/** The outcome of turning a torrent into something the player can open. */
sealed interface ResolvedStream {
    data class Ready(val url: String, val fileName: String?) : ResolvedStream

    /** Accepted but still downloading; [progress] is 0..1 where the service says. */
    data class Downloading(val progress: Float) : ResolvedStream

    data class Failed(val reason: String) : ResolvedStream
}

/**
 * Real-Debrid.
 *
 * The piece that makes torrent sources behave like streams. A magnet on its own
 * is a peer-to-peer download that starts slowly and may never finish; handed to
 * a debrid service that already holds the file, it becomes an ordinary HTTP URL
 * that seeks instantly. The whole Movies section is designed around that
 * distinction, which is why cache status is part of the source model rather
 * than a detail in here.
 *
 * Nothing in THOR downloads or shares any torrent data. This adds a magnet to
 * the user's own account and asks that account for a link; the transfer is
 * between Real-Debrid and the player.
 */
@Singleton
class RealDebridClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) {

    suspend fun isConfigured(): Boolean = token() != null

    private suspend fun token(): String? =
        settings.media.first().realDebridToken.takeIf(String::isNotBlank)

    suspend fun checkConnection(): DebridStatus {
        val token = token() ?: return DebridStatus.NotConfigured

        return try {
            request("/user", token).use { response ->
                when {
                    response.code == 401 || response.code == 403 -> DebridStatus.InvalidToken
                    !response.isSuccessful -> DebridStatus.Error("HTTP ${response.code}")
                    else -> {
                        val user = json.decodeFromString<RdUser>(response.body?.string().orEmpty())
                        DebridStatus.Connected(
                            username = user.username.orEmpty(),
                            daysRemaining = user.premiumSeconds?.let { it / SECONDS_PER_DAY },
                        )
                    }
                }
            }
        } catch (e: IOException) {
            DebridStatus.Error(e.message ?: "Network error")
        }
    }

    /**
     * Which of [infoHashes] the service already holds.
     *
     * Asked in one request for the whole list, because this decides the order of
     * every source on screen and doing it per source would mean the list
     * re-sorting itself under the user several times a second.
     *
     * A failure here returns an empty set rather than throwing: not knowing what
     * is cached is a worse list, not a broken one.
     */
    suspend fun cachedHashes(infoHashes: Collection<String>): Set<String> {
        val token = token() ?: return emptySet()
        val hashes = infoHashes.filter { it.isNotBlank() }.distinct()
        if (hashes.isEmpty()) return emptySet()

        return try {
            val path = "/torrents/instantAvailability/" + hashes.joinToString("/")
            request(path, token).use { response ->
                if (!response.isSuccessful) return emptySet()
                val body = response.body?.string().orEmpty()

                /*
                 * Read structurally rather than deserialised into a type.
                 *
                 * The response is keyed by hash and its values differ in shape by
                 * hoster and by whether anything is held at all — an object of
                 * file maps when cached, an empty array when not. A data class
                 * for that would have to model every variant to extract one bit
                 * of information: is this key non-empty.
                 */
                val root = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                    ?: return emptySet()

                root.entries
                    .filter { (_, value) -> value.toString().length > EMPTY_ENTRY_LENGTH }
                    .map { (hash, _) -> hash.lowercase() }
                    .toSet()
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "Cache check failed", e)
            emptySet()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unexpected cache response", e)
            emptySet()
        }
    }

    /**
     * Turns a magnet into a playable URL.
     *
     * Four steps, in Real-Debrid's own order: add the magnet, choose which files
     * inside it to keep, wait for it to be ready, then unrestrict the resulting
     * link. Cached torrents pass through the wait immediately, which is the
     * whole point of preferring them.
     *
     * @param preferLargest pick the biggest video file when the source did not
     *   name one, which for a season pack is the wrong answer and for everything
     *   else is right — callers with an episode in hand pass its index instead.
     */
    suspend fun resolve(
        magnetUri: String,
        fileIndex: Int? = null,
        preferLargest: Boolean = true,
    ): ResolvedStream {
        val token = token() ?: return ResolvedStream.Failed("Real-Debrid is not set up")

        return try {
            val torrentId = addMagnet(magnetUri, token)
                ?: return ResolvedStream.Failed("Real-Debrid rejected the magnet")

            selectFiles(torrentId, fileIndex, preferLargest, token)
            awaitLink(torrentId, token)
        } catch (e: IOException) {
            ResolvedStream.Failed(e.message ?: "Network error")
        }
    }

    private suspend fun addMagnet(magnetUri: String, token: String): String? {
        val body = FormBody.Builder().add("magnet", magnetUri).build()
        return post("/torrents/addMagnet", token, body).use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "addMagnet returned ${response.code}")
                return null
            }
            json.decodeFromString<RdAdded>(response.body?.string().orEmpty()).id
        }
    }

    /**
     * Chooses the files to download.
     *
     * Real-Debrid leaves a newly added torrent in `waiting_files_selection` and
     * does nothing until told what is wanted. Skipping this was the difference
     * between "it never becomes ready" and a working stream, and there is no
     * error to indicate it — the torrent simply sits there.
     */
    private suspend fun selectFiles(
        torrentId: String,
        fileIndex: Int?,
        preferLargest: Boolean,
        token: String,
    ) {
        val selection = when {
            fileIndex != null -> "${fileIndex + 1}"
            !preferLargest -> "all"
            else -> largestVideoFileId(torrentId, token) ?: "all"
        }

        val body = FormBody.Builder().add("files", selection).build()
        post("/torrents/selectFiles/$torrentId", token, body).close()
    }

    private suspend fun largestVideoFileId(torrentId: String, token: String): String? =
        request("/torrents/info/$torrentId", token).use { response ->
            if (!response.isSuccessful) return null
            json.decodeFromString<RdTorrentInfo>(response.body?.string().orEmpty())
                .files
                .orEmpty()
                .filter { file -> VIDEO_EXTENSIONS.any { file.path.orEmpty().endsWith(it, true) } }
                .maxByOrNull { it.bytes ?: 0L }
                ?.id
                ?.toString()
        }

    /**
     * Waits for the torrent to become downloadable, then unrestricts its link.
     *
     * Polled rather than pushed, because the API offers nothing else. A cached
     * torrent is normally ready on the first or second look; an uncached one is
     * reported back as still downloading rather than waited on indefinitely,
     * so the caller can show progress instead of appearing to hang.
     */
    private suspend fun awaitLink(torrentId: String, token: String): ResolvedStream {
        repeat(READY_POLL_ATTEMPTS) { attempt ->
            val info = request("/torrents/info/$torrentId", token).use { response ->
                if (!response.isSuccessful) return ResolvedStream.Failed("HTTP ${response.code}")
                json.decodeFromString<RdTorrentInfo>(response.body?.string().orEmpty())
            }

            when (info.status) {
                "downloaded" -> {
                    val link = info.links?.firstOrNull()
                        ?: return ResolvedStream.Failed("Real-Debrid returned no link")
                    return unrestrict(link, token)
                }

                "magnet_error", "error", "virus", "dead" ->
                    return ResolvedStream.Failed("Real-Debrid could not fetch this source")

                else -> if (attempt == READY_POLL_ATTEMPTS - 1) {
                    return ResolvedStream.Downloading((info.progress ?: 0f) / 100f)
                }
            }

            delay(READY_POLL_INTERVAL_MS)
        }

        return ResolvedStream.Downloading(0f)
    }

    private suspend fun unrestrict(link: String, token: String): ResolvedStream {
        val body = FormBody.Builder().add("link", link).build()
        return post("/unrestrict/link", token, body).use { response ->
            if (!response.isSuccessful) {
                return ResolvedStream.Failed("Could not unlock the stream (${response.code})")
            }
            val unrestricted =
                json.decodeFromString<RdUnrestricted>(response.body?.string().orEmpty())
            unrestricted.download
                ?.let { ResolvedStream.Ready(it, unrestricted.filename) }
                ?: ResolvedStream.Failed("Real-Debrid returned no download URL")
        }
    }

    private suspend fun request(path: String, token: String) =
        client.newCall(
            Request.Builder()
                .url("$BASE_URL$path")
                .header("Authorization", "Bearer $token")
                .build(),
        ).await()

    private suspend fun post(path: String, token: String, body: FormBody) =
        client.newCall(
            Request.Builder()
                .url("$BASE_URL$path")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build(),
        ).await()

    // -------------------------------------------------------------------- DTOs

    @Serializable
    private data class RdUser(
        val username: String? = null,
        @SerialName("premium") val premiumSeconds: Int? = null,
    )

    @Serializable
    private data class RdAdded(val id: String? = null)

    @Serializable
    private data class RdTorrentInfo(
        val status: String? = null,
        val progress: Float? = null,
        val links: List<String>? = null,
        val files: List<RdFile>? = null,
    )

    @Serializable
    private data class RdFile(
        val id: Int? = null,
        val path: String? = null,
        val bytes: Long? = null,
    )

    @Serializable
    private data class RdUnrestricted(
        val download: String? = null,
        val filename: String? = null,
    )

    private companion object {
        const val TAG = "Media"
        const val BASE_URL = "https://api.real-debrid.com/rest/1.0"
        const val SECONDS_PER_DAY = 86_400

        /**
         * An uncached hash comes back as `[]` or `{}`; anything longer holds
         * file information and therefore means the torrent is held.
         */
        const val EMPTY_ENTRY_LENGTH = 2

        const val READY_POLL_ATTEMPTS = 6
        const val READY_POLL_INTERVAL_MS = 1_500L

        val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm")
    }
}
