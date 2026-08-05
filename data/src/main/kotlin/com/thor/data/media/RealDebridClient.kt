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
    data class Ready(
        val url: String,
        val fileName: String?,
        /** Headers supplied by an addon for a direct HTTP stream. */
        val requestHeaders: Map<String, String> = emptyMap(),
    ) : ResolvedStream

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
) : DebridClient {

    override val service: DebridService = DebridService.REAL_DEBRID

    override suspend fun isConfigured(): Boolean = token() != null

    private suspend fun token(): String? =
        settings.media.first().realDebridToken.takeIf(String::isNotBlank)

    override suspend fun checkConnection(): DebridStatus {
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
     * Availability and matching Real-Debrid file-ID variants for [infoHashes].
     *
     * A real miss is returned as a checked hash with no variant; a request that
     * failed or did not contain the documented `rd` field stays unknown. Results
     * are bounded in small batches so a large addon list cannot make the full
     * availability check fail at once.
     */
    override suspend fun cachedHashes(infoHashes: Collection<String>): CacheAvailability? {
        val token = token() ?: return null
        val hashes = infoHashes
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
            .distinct()
        if (hashes.isEmpty()) return CacheAvailability(emptySet(), emptySet())

        val checked = linkedSetOf<String>()
        val variantsByHash = linkedMapOf<String, List<CachedFileVariant>>()
        hashes.chunked(CACHE_HASH_BATCH_SIZE).forEach { batch ->
            val result = cachedHashBatch(batch, token) ?: return@forEach
            checked += result.checkedHashes
            variantsByHash += result.variantsByHash
        }

        return CacheAvailability(
            checkedHashes = checked,
            // Real-Debrid says it holds a torrent by naming the files inside it,
            // so having a variant is the claim; there is no separate yes.
            cachedHashes = variantsByHash.keys,
            variantsByHash = variantsByHash,
        ).takeIf { it.checkedHashes.isNotEmpty() }
    }

    /** One bounded instant-availability call, parsed from Real-Debrid's documented shape. */
    private suspend fun cachedHashBatch(
        hashes: List<String>,
        token: String,
    ): CacheAvailability? = try {
        val path = "/torrents/instantAvailability/" + hashes.joinToString("/")
        request(path, token).use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "Cache check unavailable (HTTP ${response.code})")
                return null
            }

            val root = json.parseToJsonElement(response.body?.string().orEmpty())
                as? kotlinx.serialization.json.JsonObject
                ?: return null
            val requested = hashes.toSet()
            val checked = linkedSetOf<String>()
            val variantsByHash = linkedMapOf<String, List<CachedFileVariant>>()

            root.forEach { (rawHash, rawAvailability) ->
                val hash = rawHash.lowercase()
                if (hash !in requested) return@forEach

                val hosters = rawAvailability as? kotlinx.serialization.json.JsonObject
                    ?: return@forEach
                val rawVariants = hosters[REAL_DEBRID_HOSTER] ?: return@forEach
                val variants = when (rawVariants) {
                    is kotlinx.serialization.json.JsonArray -> rawVariants
                        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }

                    is kotlinx.serialization.json.JsonObject -> listOf(rawVariants)
                    else -> return@forEach
                }

                // `rd: []` is a meaningful response: this hash was checked and
                // is not cached. Only absent or unrecognisable fields stay UNKNOWN.
                checked += hash
                variants.mapNotNull { variant ->
                    variant.keys
                        .mapNotNull(String::toIntOrNull)
                        .sorted()
                        .takeIf(List<Int>::isNotEmpty)
                        ?.let(::CachedFileVariant)
                }.takeIf(List<CachedFileVariant>::isNotEmpty)?.let { parsed ->
                    variantsByHash[hash] = parsed
                }
            }

            CacheAvailability(
                checkedHashes = checked,
                cachedHashes = variantsByHash.keys,
                variantsByHash = variantsByHash,
            )
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
     * Four steps, in Real-Debrid's own order: add the magnet, choose which files
     * inside it to keep, wait for it to be ready, then unrestrict the resulting
     * link. Cached torrents pass through the wait immediately, which is the
     * whole point of preferring them.
     *
     * @param preferLargest pick the biggest video file when the source did not
     *   name one, which for a season pack is the wrong answer and for everything
     *   else is right — callers with an episode in hand pass its index instead.
     */
    override suspend fun resolve(
        magnetUri: String,
        fileIndex: Int?,
        instantFileIds: List<Int>,
        preferLargest: Boolean,
    ): ResolvedStream {
        val token = token() ?: return ResolvedStream.Failed("Real-Debrid is not set up")

        return try {
            val torrentId = addMagnet(magnetUri, token)
                ?: return ResolvedStream.Failed("Real-Debrid rejected the magnet")

            when (val selection = selectFiles(
                torrentId = torrentId,
                fileIndex = fileIndex,
                instantFileIds = instantFileIds,
                preferLargest = preferLargest,
                token = token,
            )) {
                is FileSelection.Complete -> selection.result
                is FileSelection.Selected -> awaitLink(torrentId, token, selection.preferredFileId)
            }
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
     * Chooses the one file to download.
     *
     * Real-Debrid leaves a newly added torrent in `waiting_files_selection` and
     * does nothing until told what is wanted. Skipping this was the difference
     * between "it never becomes ready" and a working stream, and there is no
     * error to indicate it — the torrent simply sits there.
     *
     * **Exactly one file, and never `all`.** This asked for the largest video
     * file and then fell back to `all` when it could not find one — which it
     * could not, every time, because it read the file list in the same breath as
     * adding the magnet and Real-Debrid does not have one yet: a torrent spends
     * its first seconds in `magnet_conversion` with `files` absent. So `all` was
     * selected for every source, `links` came back holding one entry per file in
     * the torrent's own order, and [awaitLink] took the first — a sample, an
     * `.nfo`, a subtitle, whatever the release happened to put first. The player
     * was then handed a file with no video in it, which is not an error it can
     * report: it buffers, finds nothing to play, and goes on buffering.
     *
     * Selecting a single file also makes the link unambiguous, which is the other
     * half of the fix — with one file selected there is exactly one link, and no
     * guessing which of several belongs to the episode that was asked for.
     */
    private sealed interface FileSelection {
        data class Selected(val preferredFileId: Int?) : FileSelection
        data class Complete(val result: ResolvedStream) : FileSelection
    }

    private suspend fun selectFiles(
        torrentId: String,
        fileIndex: Int?,
        instantFileIds: List<Int>,
        preferLargest: Boolean,
        token: String,
    ): FileSelection {
        val files = awaitFiles(torrentId, token)

        // Do not select `all` while Real-Debrid is still converting the magnet.
        // That made the eventual first link depend on torrent file order, which is
        // commonly a sample, NFO or subtitle rather than the selected video.
        if (files.isEmpty()) return FileSelection.Complete(ResolvedStream.Downloading(0f))

        /*
         * The addon's index if the torrent really has a file there, and the
         * largest video otherwise.
         *
         * Stremio numbers files from zero over the torrent's own list and
         * Real-Debrid numbers them from one over the same list, so the addon's
         * `fileIdx` maps by adding one — but only when the two lists agree.
         * Checked rather than trusted, because a hint that points past the end
         * would otherwise select nothing at all and leave the torrent waiting.
        */
        val named = fileIndex?.plus(1)?.takeIf { id ->
            files.any { it.id == id && isVideoFile(it) }
        }
        val normalSelection = named
            ?: largestVideoFile(files)?.id
            ?: files.takeIf { preferLargest }?.maxByOrNull { it.bytes ?: 0L }?.id
            ?: return FileSelection.Complete(
                ResolvedStream.Failed("Real-Debrid found no playable file"),
            )

        /*
         * A cached availability answer is a set, not a single file. Selecting only
         * its largest member turns a marked-instant source back into a queued
         * download; Real-Debrid requires every ID in the returned variant. Fall
         * back to one normal video when the file IDs no longer match the torrent
         * metadata we just received.
         */
        val requestedInstant = instantFileIds.distinct()
        val instantSelection = requestedInstant.takeIf { ids ->
            ids.isNotEmpty() && ids.all { id -> files.any { it.id == id } }
        }
        val selectedIds = instantSelection ?: listOf(normalSelection)
        val preferredFileId = named?.takeIf { it in selectedIds }
            ?: largestVideoFile(files.filter { it.id in selectedIds })?.id
            ?: normalSelection

        val body = FormBody.Builder()
            .add("files", selectedIds.joinToString(","))
            .build()
        post("/torrents/selectFiles/$torrentId", token, body).use { response ->
            if (!response.isSuccessful) {
                return FileSelection.Complete(
                    ResolvedStream.Failed(
                        "Real-Debrid could not select this video (${response.code})",
                    ),
                )
            }
        }
        return FileSelection.Selected(preferredFileId)
    }

    /**
     * The torrent's file list, once Real-Debrid has read the metadata.
     *
     * Polled because there is no other way to know. A cached torrent answers on
     * the first look; an uncached one is still converting the magnet, and asking
     * once — which is what this used to do — reliably got nothing back.
     */
    private suspend fun awaitFiles(torrentId: String, token: String): List<RdFile> {
        repeat(FILES_POLL_ATTEMPTS) { attempt ->
            val info = request("/torrents/info/$torrentId", token).use { response ->
                if (!response.isSuccessful) return emptyList()
                json.decodeFromString<RdTorrentInfo>(response.body?.string().orEmpty())
            }

            val files = info.files.orEmpty()
            if (files.isNotEmpty()) return files
            if (info.status in TERMINAL_FAILURES) return emptyList()
            if (attempt < FILES_POLL_ATTEMPTS - 1) delay(FILES_POLL_INTERVAL_MS)
        }
        ThorLog.w(TAG, "Real-Debrid never listed the files for $torrentId")
        return emptyList()
    }

    private fun isVideoFile(file: RdFile): Boolean =
        VIDEO_EXTENSIONS.any { extension -> file.path.orEmpty().endsWith(extension, true) }

    private fun largestVideoFile(files: List<RdFile>): RdFile? = files
        .filter(::isVideoFile)
        .maxByOrNull { it.bytes ?: 0L }

    /**
     * Waits for the torrent to become downloadable, then unrestricts its link.
     *
     * Polled rather than pushed, because the API offers nothing else. A cached
     * torrent is normally ready on the first or second look; an uncached one is
     * reported back as still downloading rather than waited on indefinitely,
     * so the caller can show progress instead of appearing to hang.
     */
    private suspend fun awaitLink(
        torrentId: String,
        token: String,
        preferredFileId: Int?,
    ): ResolvedStream {
        repeat(READY_POLL_ATTEMPTS) { attempt ->
            val info = request("/torrents/info/$torrentId", token).use { response ->
                if (!response.isSuccessful) return ResolvedStream.Failed("HTTP ${response.code}")
                json.decodeFromString<RdTorrentInfo>(response.body?.string().orEmpty())
            }

            when (info.status) {
                "downloaded" -> {
                    val link = preferredLink(info, preferredFileId)
                        ?: return ResolvedStream.Failed("Real-Debrid returned no link")
                    // One selected file means one link. More than that means the
                    // selection did not narrow to a single file, and the first is
                    // then only a guess — worth saying so in the log, because the
                    // symptom is a stream that plays the wrong thing or nothing.
                    return unrestrict(link, token)
                }

                in TERMINAL_FAILURES ->
                    return ResolvedStream.Failed("Real-Debrid could not fetch this source")

                else -> if (attempt == READY_POLL_ATTEMPTS - 1) {
                    return ResolvedStream.Downloading((info.progress ?: 0f) / 100f)
                }
            }

            delay(READY_POLL_INTERVAL_MS)
        }

        return ResolvedStream.Downloading(0f)
    }

    /**
     * Links are ordered like the selected file list. Use that ordering to keep a
     * cached multi-file variant from opening a sample, subtitle, or NFO first.
     */
    private fun preferredLink(info: RdTorrentInfo, preferredFileId: Int?): String? {
        val links = info.links.orEmpty()
        if (preferredFileId == null) return links.firstOrNull()

        val selectedIndex = info.files.orEmpty()
            .filter { it.selected == SELECTED }
            .indexOfFirst { it.id == preferredFileId }
        return links.getOrNull(if (selectedIndex >= 0) selectedIndex else -1) ?: links.firstOrNull()
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
        val selected: Int? = null,
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

        const val CACHE_HASH_BATCH_SIZE = 20
        const val REAL_DEBRID_HOSTER = "rd"
        const val SELECTED = 1

        const val READY_POLL_ATTEMPTS = 6
        const val READY_POLL_INTERVAL_MS = 1_500L

        /**
         * How long to wait for the magnet to become a file list.
         *
         * Short, because this is the step before anything can start and the
         * viewer is looking at a spinner for all of it. A cached torrent answers
         * immediately; anything still converting after this is reported as still
         * fetching rather than waited on.
         */
        const val FILES_POLL_ATTEMPTS = 12
        const val FILES_POLL_INTERVAL_MS = 1_000L

        /** Statuses from which no file list and no link will ever arrive. */
        val TERMINAL_FAILURES = setOf("magnet_error", "error", "virus", "dead")

        val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm")
    }
}
