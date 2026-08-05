package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.DebridService
import com.thor.data.network.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorBox.
 *
 * The same job Real-Debrid does — a magnet becomes an HTTP URL that seeks — and
 * a different set of edges to work around, which is the whole reason there is an
 * interface between the two rather than a flag inside one client.
 *
 * Three differences matter, and each one is a bug if it is assumed away.
 *
 * It answers the cache question with a yes or a no and nothing else, where
 * Real-Debrid answers by naming the files inside. So [CacheAvailability.
 * cachedHashes] is filled and `variantsByHash` is not, and a source resolved
 * here never carries instant file IDs — there is nothing to carry.
 *
 * It numbers the files in a torrent from zero, over the torrent's own list,
 * which is the same numbering a Stremio addon's `fileIdx` uses. Real-Debrid
 * numbers from one, and its client adds the offset; here the index is used as
 * given — but still checked against the list rather than trusted, because an
 * index pointing past the end would otherwise select nothing at all.
 *
 * And it hands over the link by a URL that carries the key in the query string
 * rather than by a header, which is the one place in this file the credential
 * leaves the Authorization line.
 *
 * Nothing here downloads or shares torrent data. This adds a magnet to the
 * user's own account and asks that account for a link; the transfer is between
 * TorBox and the player.
 */
@Singleton
class TorBoxClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : DebridClient {

    override val service: DebridService = DebridService.TORBOX

    override suspend fun isConfigured(): Boolean = key() != null

    private suspend fun key(): String? =
        settings.media.first().torBoxApiKey.takeIf(String::isNotBlank)

    override suspend fun checkConnection(): DebridStatus {
        val key = key() ?: return DebridStatus.NotConfigured

        return try {
            get("/user/me?settings=false", key).use { response ->
                when {
                    response.code == 401 || response.code == 403 -> DebridStatus.InvalidToken
                    !response.isSuccessful -> DebridStatus.Error("HTTP ${response.code}")
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val user = json.decodeFromString<TbResponse<TbUser>>(body).data
                        // A key that is syntactically fine and belongs to nobody
                        // comes back as a success with no user on it, which is a
                        // rejection however the status line reads.
                        if (user == null) {
                            DebridStatus.InvalidToken
                        } else {
                            DebridStatus.Connected(
                                username = user.email.orEmpty(),
                                daysRemaining = daysUntil(user.premiumExpiresAt),
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            DebridStatus.Error(e.message ?: "Network error")
        } catch (e: IllegalArgumentException) {
            DebridStatus.Error("Unexpected reply from TorBox")
        }
    }

    /**
     * How long the account has left, from the date TorBox gives.
     *
     * A date rather than a count of seconds, so it is parsed rather than
     * divided — and defensively, because the field is documented as an ISO
     * timestamp and an account without a subscription has none at all. A day
     * count that cannot be worked out is reported as absent, which the settings
     * row already handles: it is a detail beside "connected", not the answer.
     */
    private fun daysUntil(timestamp: String?): Int? {
        val raw = timestamp?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val expiry = OffsetDateTime.parse(raw)
            ChronoUnit.DAYS.between(OffsetDateTime.now(ZoneOffset.UTC), expiry)
                .toInt()
                .coerceAtLeast(0)
        }.getOrNull()
    }

    /**
     * Which of [infoHashes] TorBox already holds.
     *
     * One request for the batch, and the answer is a plain yes-or-no per hash:
     * every hash asked about is checked, and the ones that come back are the
     * ones it has. A request that fails returns null rather than an empty
     * answer, because "asked and told no" would delete every source from a list
     * that `cachedOnly` is filtering — see [DebridClient.cachedHashes].
     */
    override suspend fun cachedHashes(infoHashes: Collection<String>): CacheAvailability? {
        val key = key() ?: return null
        val hashes = infoHashes
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .distinct()
        if (hashes.isEmpty()) return CacheAvailability(emptySet(), emptySet())

        val checked = linkedSetOf<String>()
        val cached = linkedSetOf<String>()
        hashes.chunked(CACHE_HASH_BATCH_SIZE).forEach { batch ->
            val found = cachedHashBatch(batch, key) ?: return@forEach
            checked += batch
            cached += found
        }

        return CacheAvailability(checkedHashes = checked, cachedHashes = cached)
            .takeIf { it.checkedHashes.isNotEmpty() }
    }

    /**
     * One bounded cache call.
     *
     * The reply is read as a tree rather than as a declared shape, because the
     * documented `data` is a map keyed by hash in one format and a list of
     * objects carrying their own hash in the other, and which one arrives
     * depends on a query parameter that a proxy in front of the API is free to
     * ignore. Both are read here; anything else counts as no answer.
     */
    private suspend fun cachedHashBatch(hashes: List<String>, key: String): Set<String>? = try {
        val query = hashes.joinToString(",")
        get("/torrents/checkcached?hash=$query&format=object&list_files=false", key).use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "Cache check unavailable (HTTP ${response.code})")
                return null
            }

            val root = json.parseToJsonElement(response.body?.string().orEmpty())
                as? kotlinx.serialization.json.JsonObject
                ?: return null
            val requested = hashes.toSet()

            when (val data = root["data"]) {
                // Keyed by hash: every key present is a hash TorBox holds.
                is kotlinx.serialization.json.JsonObject -> data.keys
                    .map(String::lowercase)
                    .filter { it in requested }
                    .toSet()

                // A list of objects, each naming its own hash.
                is kotlinx.serialization.json.JsonArray -> data
                    .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                    .mapNotNull { entry ->
                        (entry["hash"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.content
                            ?.lowercase()
                    }
                    .filter { it in requested }
                    .toSet()

                // `data: null` is TorBox's way of saying it holds none of them,
                // which is an answer and has to stay one: turning it into "no
                // answer" would leave every source unknown and unordered.
                else -> emptySet()
            }
        }
    } catch (e: IOException) {
        ThorLog.w(TAG, "Cache check failed", e)
        null
    } catch (e: IllegalArgumentException) {
        ThorLog.w(TAG, "Unexpected cache response", e)
        null
    }

    /**
     * Turns a magnet into a playable URL.
     *
     * Three steps: hand over the magnet, wait for TorBox to say what is inside
     * it, then ask for a link to the one file wanted. A torrent it already holds
     * passes the wait immediately, which is the whole point of preferring cached
     * sources.
     *
     * @param instantFileIds ignored: TorBox names no file sets, and there is
     *   nothing here that would honour one. Accepted because the interface
     *   carries it for the service that does.
     */
    override suspend fun resolve(
        magnetUri: String,
        fileIndex: Int?,
        instantFileIds: List<Int>,
        preferLargest: Boolean,
    ): ResolvedStream {
        val key = key() ?: return ResolvedStream.Failed("TorBox is not set up")

        return try {
            val torrentId = addMagnet(magnetUri, key)
                ?: return ResolvedStream.Failed("TorBox rejected the magnet")

            awaitFile(torrentId, key, fileIndex, preferLargest)
        } catch (e: IOException) {
            ResolvedStream.Failed(e.message ?: "Network error")
        } catch (e: IllegalArgumentException) {
            ResolvedStream.Failed("Unexpected reply from TorBox")
        }
    }

    /**
     * Hands the magnet over, and takes back the id of the transfer.
     *
     * A magnet the account already has is not an error: TorBox answers with the
     * existing transfer's id, which is exactly what a cached source is and the
     * reason one starts instantly.
     */
    private suspend fun addMagnet(magnetUri: String, key: String): Int? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("magnet", magnetUri)
            // Not seeded back. The launcher is a client of the user's account
            // and has no business volunteering their bandwidth.
            .addFormDataPart("seed", NO_SEEDING)
            .addFormDataPart("allow_zip", "false")
            .build()

        return post("/torrents/createtorrent", key, body).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "createtorrent returned ${response.code}")
                return null
            }
            json.decodeFromString<TbResponse<TbCreated>>(raw).data?.torrentId
        }
    }

    /**
     * Waits for the file list, chooses one file, and asks for its link.
     *
     * Polled because the API offers nothing else, and the two waits are one: a
     * transfer that has no file list yet is still reading the magnet, and one
     * that has a list but is not finished is still downloading. Both report
     * back as progress rather than being waited on indefinitely, so the viewer
     * sees a figure instead of an apparent hang.
     */
    private suspend fun awaitFile(
        torrentId: Int,
        key: String,
        fileIndex: Int?,
        preferLargest: Boolean,
    ): ResolvedStream {
        repeat(READY_POLL_ATTEMPTS) { attempt ->
            val info = torrentInfo(torrentId, key)
                ?: return ResolvedStream.Failed("TorBox lost track of this transfer")

            val files = info.files.orEmpty()
            val ready = info.downloadPresent == true || info.downloadFinished == true

            if (ready && files.isNotEmpty()) {
                val file = chooseFile(files, fileIndex, preferLargest)
                    ?: return ResolvedStream.Failed("TorBox found no playable file")
                return requestLink(torrentId, file, key)
            }

            if (attempt == READY_POLL_ATTEMPTS - 1) {
                return ResolvedStream.Downloading((info.progress ?: 0f).coerceIn(0f, 1f))
            }
            delay(READY_POLL_INTERVAL_MS)
        }

        return ResolvedStream.Downloading(0f)
    }

    private suspend fun torrentInfo(torrentId: Int, key: String): TbTorrent? =
        get("/torrents/mylist?id=$torrentId&bypass_cache=true", key).use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "mylist returned ${response.code}")
                return null
            }
            json.decodeFromString<TbResponse<TbTorrent>>(response.body?.string().orEmpty()).data
        }

    /**
     * The one file to play.
     *
     * The addon's index when the torrent really has a video there, and the
     * largest video otherwise. Checked rather than trusted for the same reason
     * Real-Debrid's client checks it: a season pack whose index points at a
     * sample, an `.nfo` or a subtitle hands the player a file with no video in
     * it, which is not an error a player can report — it buffers, finds nothing,
     * and goes on buffering.
     */
    private fun chooseFile(
        files: List<TbFile>,
        fileIndex: Int?,
        preferLargest: Boolean,
    ): TbFile? {
        val named = fileIndex?.let { index -> files.firstOrNull { it.id == index } }
            ?.takeIf(::isVideoFile)
        return named
            ?: files.filter(::isVideoFile).maxByOrNull { it.size ?: 0L }
            ?: files.takeIf { preferLargest }?.maxByOrNull { it.size ?: 0L }
    }

    private fun isVideoFile(file: TbFile): Boolean {
        val name = file.shortName?.takeIf(String::isNotBlank) ?: file.name.orEmpty()
        return VIDEO_EXTENSIONS.any { extension -> name.endsWith(extension, ignoreCase = true) }
    }

    /**
     * Asks for the download URL for one file.
     *
     * The key travels in the query string here rather than in a header, which is
     * the API's own shape for this call and the only place in this file it does
     * so. Built through `HttpUrl` rather than by pasting the value into a
     * string, so a key with a character that means something in a URL cannot
     * quietly produce a request for a different file.
     */
    private suspend fun requestLink(torrentId: Int, file: TbFile, key: String): ResolvedStream {
        val fileId = file.id ?: return ResolvedStream.Failed("TorBox named no file to open")
        val url = "$BASE_URL/torrents/requestdl".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("token", key)
            ?.addQueryParameter("torrent_id", torrentId.toString())
            ?.addQueryParameter("file_id", fileId.toString())
            ?.addQueryParameter("redirect", "false")
            ?.build()
            ?: return ResolvedStream.Failed("Could not ask TorBox for a link")

        return client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) {
                return ResolvedStream.Failed("Could not unlock the stream (${response.code})")
            }
            val link = json
                .decodeFromString<TbResponse<String>>(response.body?.string().orEmpty())
                .data
                ?.takeIf(String::isNotBlank)
                ?: return ResolvedStream.Failed("TorBox returned no download URL")

            ResolvedStream.Ready(
                url = link,
                fileName = file.shortName?.takeIf(String::isNotBlank) ?: file.name,
            )
        }
    }

    private suspend fun get(path: String, key: String) =
        client.newCall(
            Request.Builder()
                .url("$BASE_URL$path")
                .header("Authorization", "Bearer $key")
                .build(),
        ).await()

    private suspend fun post(path: String, key: String, body: MultipartBody) =
        client.newCall(
            Request.Builder()
                .url("$BASE_URL$path")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build(),
        ).await()

    // -------------------------------------------------------------------- DTOs

    /**
     * The envelope every TorBox reply arrives in.
     *
     * `data` is nullable on purpose: a call that succeeded and found nothing
     * answers with a success and a null body, and reading that as a parse
     * failure would turn "no such torrent" into "TorBox is broken".
     */
    @Serializable
    private data class TbResponse<T>(
        val success: Boolean = false,
        val detail: String? = null,
        val data: T? = null,
    )

    @Serializable
    private data class TbUser(
        val email: String? = null,
        val plan: Int? = null,
        @SerialName("premium_expires_at") val premiumExpiresAt: String? = null,
    )

    @Serializable
    private data class TbCreated(
        @SerialName("torrent_id") val torrentId: Int? = null,
        val hash: String? = null,
    )

    @Serializable
    private data class TbTorrent(
        val id: Int? = null,
        val hash: String? = null,
        val progress: Float? = null,
        @SerialName("download_finished") val downloadFinished: Boolean? = null,
        @SerialName("download_present") val downloadPresent: Boolean? = null,
        val files: List<TbFile>? = null,
    )

    @Serializable
    private data class TbFile(
        val id: Int? = null,
        val name: String? = null,
        @SerialName("short_name") val shortName: String? = null,
        val size: Long? = null,
    )

    private companion object {
        const val TAG = "Media"
        const val BASE_URL = "https://api.torbox.app/v1/api"

        /**
         * How many hashes to ask about at once.
         *
         * They go in the query string, so the ceiling is the URL length rather
         * than a documented limit — and a batch that is refused for being too
         * long is refused for the whole batch, which is the failure that empties
         * a source list.
         */
        const val CACHE_HASH_BATCH_SIZE = 40

        /** TorBox's "do not seed", which is the only honest default for a client. */
        const val NO_SEEDING = "2"

        const val READY_POLL_ATTEMPTS = 10
        const val READY_POLL_INTERVAL_MS = 1_500L

        val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm")
    }
}
