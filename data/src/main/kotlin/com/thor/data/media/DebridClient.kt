package com.thor.data.media

import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.DebridService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What every debrid service has to be able to answer.
 *
 * Three questions, and they are the same three whichever account the user holds:
 * is this usable, do you already have these files, and turn this magnet into a
 * URL. Everything the services differ about — how they authenticate, how they
 * number the files inside a torrent, how long they make you wait before they
 * will admit what is in one — stays behind this and out of the repository, which
 * is what makes adding a second service a new file rather than a second branch
 * in every caller.
 */
interface DebridClient {

    /** Which service this is, for anything that has to name it to the user. */
    val service: DebridService

    suspend fun isConfigured(): Boolean

    suspend fun checkConnection(): DebridStatus

    /**
     * What the service holds, of [infoHashes].
     *
     * Null means it would not say — a failed request, a shape that could not be
     * read — which is not the same answer as "none of them" and must not be
     * turned into one: `cachedOnly` is on by default and drops everything marked
     * not-cached, so a service having a bad minute would otherwise empty the
     * source list rather than merely order it worse.
     */
    suspend fun cachedHashes(infoHashes: Collection<String>): CacheAvailability?

    /**
     * Turns a magnet into something the player can open.
     *
     * @param fileIndex the file the source named, indexed as the torrent itself
     *   indexes them — from zero. Services number their own file lists
     *   differently and each implementation maps it.
     * @param instantFileIds a set the service said is instantly available and
     *   must be requested together; empty where the service does not name one.
     * @param preferLargest pick the biggest video when nothing was named, which
     *   is right for a film and wrong for a season pack — callers with an
     *   episode in hand pass its index instead.
     */
    suspend fun resolve(
        magnetUri: String,
        fileIndex: Int? = null,
        instantFileIds: List<Int> = emptyList(),
        preferLargest: Boolean = true,
    ): ResolvedStream
}

/**
 * What a service said about a batch of torrent hashes.
 *
 * Three sets rather than one, because "asked and told no" and "never got an
 * answer" have to stay apart all the way to the ranking — see [DebridClient
 * .cachedHashes]. [variantsByHash] is only filled by services that name the
 * files inside a cached torrent; one that simply says yes or no leaves it empty,
 * and a hash being in [cachedHashes] is the whole of the claim.
 */
data class CacheAvailability(
    /** Hashes the service actually answered about. */
    val checkedHashes: Set<String>,
    /** Of those, the ones it already holds. */
    val cachedHashes: Set<String>,
    /** File-ID sets that must be selected together, where the service names them. */
    val variantsByHash: Map<String, List<CachedFileVariant>> = emptyMap(),
)

/** One complete set of file IDs a service says is instantly available. */
data class CachedFileVariant(val fileIds: List<Int>)

/**
 * Whichever service the user chose, asked as though there were only one.
 *
 * A router rather than a Hilt binding, because the choice is a setting and can
 * change between two calls — a binding is decided once when the graph is built,
 * which would mean a switch in settings did nothing until the launcher was
 * restarted. Read per call instead: a debrid request is a network round trip and
 * one more read of a settings flow is not what makes it slow.
 */
@Singleton
class DebridGateway @Inject constructor(
    private val realDebrid: RealDebridClient,
    private val torBox: TorBoxClient,
    private val settings: SettingsRepository,
) {

    private suspend fun active(): DebridClient =
        when (settings.media.first().debridService) {
            DebridService.REAL_DEBRID -> realDebrid
            DebridService.TORBOX -> torBox
        }

    /** What to call the selected service when reporting to the user. */
    suspend fun serviceName(): String = active().service.label

    suspend fun isConfigured(): Boolean = active().isConfigured()

    suspend fun checkConnection(): DebridStatus = active().checkConnection()

    suspend fun cachedHashes(infoHashes: Collection<String>): CacheAvailability? =
        active().cachedHashes(infoHashes)

    suspend fun resolve(
        magnetUri: String,
        fileIndex: Int? = null,
        instantFileIds: List<Int> = emptyList(),
        preferLargest: Boolean = true,
    ): ResolvedStream = active().resolve(magnetUri, fileIndex, instantFileIds, preferLargest)
}
