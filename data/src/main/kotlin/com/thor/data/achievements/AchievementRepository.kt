package com.thor.data.achievements

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.dao.AchievementDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.model.AchievementEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.Achievement
import com.thor.core.model.AchievementSummary
import com.thor.core.model.RetroAchievementsConsoles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Achievements, and which RetroAchievements game each local game is.
 *
 * The identification is the hard half and the reason this exists rather than
 * another `MetadataProvider`. RetroAchievements identifies a game by the hash of
 * the ROM, with rules that differ per console — headers skipped on some, tracks
 * parsed on others — and reimplementing that correctly for two dozen systems is
 * a project in itself, not a feature. This matches by normalised title within
 * the game's own console instead.
 *
 * That is a real limitation and worth naming: a hack, a translation patch or an
 * unusual regional title will not match, and a badly named file will match the
 * wrong game. What it gets right is the ordinary case — a correctly named ROM of
 * a game with an achievement set — which is nearly all of them. A resolved id is
 * stored on the game, so a wrong match is a thing the user could be given a way
 * to correct rather than something recomputed forever.
 */
@Singleton
class AchievementRepository @Inject constructor(
    private val client: RetroAchievementsClient,
    private val gameDao: GameDao,
    private val achievementDao: AchievementDao,
    private val settings: SettingsRepository,
    private val json: Json,
    @Dispatcher(ThorDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * A console's catalogue, held for the life of the process.
     *
     * These lists are large and change slowly — a console gains a handful of
     * achievement sets a month — and a sync touching six consoles would
     * otherwise download all six catalogues once per game.
     */
    private val catalogues = mutableMapOf<Int, Map<String, Int>>()

    fun observeFor(entryId: String): Flow<List<Achievement>> =
        achievementDao.observeFor(entryId).map { rows -> rows.map(AchievementEntity::toDomain) }

    /**
     * Matches every supported game to its achievement set and stores the counts.
     *
     * Two passes, because the site offers two shapes of answer and only one of
     * them is affordable in bulk: the catalogue gives an id per title, and one
     * batched call then reports progress for a hundred ids at a time. The
     * achievements themselves are left to [refresh], which is a request per game
     * and is made for the game being looked at.
     *
     * @param onProgress how many games have been dealt with, for a progress bar.
     * @return how many games ended up with an achievement set.
     */
    suspend fun syncLibrary(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(defaultDispatcher) {
            val account = settings.retroAchievements.first()
            if (!account.isActive) return@withContext 0

            val games = gameDao.getVisible()
                .filter { RetroAchievementsConsoles.isSupported(it.platformId) }
            if (games.isEmpty()) return@withContext 0

            // Resolved first, so the batched progress call below can be made
            // against ids rather than one game at a time.
            val byRemoteId = mutableMapOf<Int, MutableList<GameEntity>>()
            games.forEachIndexed { index, game ->
                resolveRemoteId(game)?.let { remoteId ->
                    byRemoteId.getOrPut(remoteId) { mutableListOf() } += game
                }
                onProgress(index + 1, games.size)
            }
            if (byRemoteId.isEmpty()) return@withContext 0

            var matched = 0
            byRemoteId.keys.chunked(RetroAchievementsClient.PROGRESS_BATCH).forEach { batch ->
                val progress = client.userProgress(batch)
                progress.forEach { (remoteId, remote) ->
                    if (remote.total <= 0) return@forEach
                    byRemoteId[remoteId]?.forEach { game ->
                        writeSummary(game, remoteId, remote, account.hardcoreOnly)
                        matched++
                    }
                }
            }

            settings.updateRetroAchievements {
                it.copy(lastSyncedEpochMs = System.currentTimeMillis())
            }
            matched
        }

    /**
     * Fetches one game's achievements in full and stores them.
     *
     * The only call that returns the achievements themselves, so it is made for
     * a game the user is actually looking at rather than for the library. Cheap
     * to call repeatedly: a game with no match resolves to nothing and returns
     * without a request.
     */
    suspend fun refresh(entryId: String): AchievementSummary? = withContext(defaultDispatcher) {
        val account = settings.retroAchievements.first()
        if (!account.isActive) return@withContext null

        val game = gameDao.getById(entryId) ?: return@withContext null
        val remoteId = resolveRemoteId(game) ?: return@withContext null
        val remote = client.gameProgress(remoteId) ?: return@withContext null

        val achievements = remote.achievementList(json)
        if (achievements.isEmpty()) return@withContext null

        val hardcoreOnly = account.hardcoreOnly
        val rows = achievements.map { entry ->
            val earnedAt = if (hardcoreOnly) entry.earnedHardcoreAt else entry.earnedAt
            AchievementEntity(
                id = "$entryId:${entry.id}",
                entryId = entryId,
                title = entry.title,
                description = entry.description,
                points = entry.points,
                badgeUri = entry.badgeName?.let(RetroAchievementsConsoles::badgeUrl),
                earnedEpochMs = parseEarned(earnedAt),
                isHardcore = entry.earnedHardcoreAt != null,
            )
        }
        achievementDao.clearFor(entryId)
        achievementDao.upsertAll(rows)

        val earnedRows = rows.filter { it.earnedEpochMs != null }
        val summary = AchievementSummary(
            gameProviderId = remoteId.toString(),
            earned = earnedRows.size,
            total = rows.size,
            earnedPoints = earnedRows.sumOf { it.points },
            totalPoints = rows.sumOf { it.points },
            isHardcore = hardcoreOnly,
            recentlyEarned = earnedRows
                .sortedByDescending { it.earnedEpochMs ?: 0L }
                .take(RECENT_LIMIT)
                .map(AchievementEntity::toDomain),
            // Cheapest first, which is RetroAchievements' own difficulty signal:
            // these are the ones actually within reach rather than the first few
            // the set happens to define.
            upcoming = rows
                .filter { it.earnedEpochMs == null }
                .sortedBy { it.points }
                .take(RECENT_LIMIT)
                .map(AchievementEntity::toDomain),
        )
        storeSummary(game, summary)
        summary
    }

    /** Forgets every stored match, so the next sync resolves from scratch. */
    suspend fun forgetMatches() = withContext(defaultDispatcher) {
        catalogues.clear()
        gameDao.getVisible()
            .filter { it.metadata.achievements != null }
            .forEach { game ->
                achievementDao.clearFor(game.id)
                gameDao.upsert(game.copy(metadata = game.metadata.copy(achievements = null)))
            }
    }

    /**
     * The site's id for a game, from its stored match or from the catalogue.
     *
     * A stored id is trusted without checking. It was either resolved by this
     * method against a catalogue that has only grown since, or corrected by
     * hand — and re-resolving a title every sync is how a match the user fixed
     * gets silently replaced by the wrong one again.
     */
    private suspend fun resolveRemoteId(game: GameEntity): Int? {
        game.metadata.achievements?.gameProviderId?.toIntOrNull()?.let { return it }

        val consoleId = RetroAchievementsConsoles.consoleFor(game.platformId) ?: return null
        val catalogue = catalogues.getOrPut(consoleId) {
            client.gameList(consoleId)
                .filter { it.achievementCount > 0 }
                .associateBy({ TitleNormalizer.sortKey(it.title) }, { it.id })
        }
        if (catalogue.isEmpty()) return null

        return catalogue[TitleNormalizer.sortKey(game.title)]
    }

    /** Writes the counts from a batched progress response onto the game. */
    private suspend fun writeSummary(
        game: GameEntity,
        remoteId: Int,
        remote: RemoteProgress,
        hardcoreOnly: Boolean,
    ) {
        val earned = if (hardcoreOnly) remote.earnedHardcore else remote.earned
        val earnedPoints = if (hardcoreOnly) remote.earnedPointsHardcore else remote.earnedPoints

        /*
         * The achievements themselves are not touched here.
         *
         * This pass only knows counts, and overwriting a set fetched by
         * [refresh] with nothing would empty a panel the user had already
         * opened. Whatever rows exist stay, and the summary above them is
         * brought up to date.
         */
        val existing = game.metadata.achievements
        storeSummary(
            game,
            AchievementSummary(
                gameProviderId = remoteId.toString(),
                earned = earned,
                total = remote.total,
                earnedPoints = earnedPoints,
                totalPoints = remote.totalPoints,
                isHardcore = hardcoreOnly,
                recentlyEarned = existing?.recentlyEarned.orEmpty(),
                upcoming = existing?.upcoming.orEmpty(),
            ),
        )
    }

    private suspend fun storeSummary(game: GameEntity, summary: AchievementSummary) {
        if (game.metadata.achievements == summary) return
        gameDao.upsert(game.copy(metadata = game.metadata.copy(achievements = summary)))
    }

    /**
     * The site's timestamps, which are local to it and carry no zone.
     *
     * Parsed as UTC rather than as the device's zone: read as local time, an
     * achievement earned an hour ago can land in the future, and "recently
     * earned" then sorts it above things genuinely more recent. An hour of drift
     * on a date nobody reads to the minute is the better error.
     */
    private fun parseEarned(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat(EARNED_FORMAT, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(value)
                ?.time
        }.getOrElse {
            ThorLog.w(TAG, "Unreadable achievement date: $value")
            null
        }
    }

    private companion object {
        const val TAG = "Achievements"
        const val EARNED_FORMAT = "yyyy-MM-dd HH:mm:ss"

        /** How many earned achievements the summary carries for the panel. */
        const val RECENT_LIMIT = 5
    }
}

private fun AchievementEntity.toDomain(): Achievement = Achievement(
    id = id,
    title = title,
    description = description,
    points = points,
    badgeUri = badgeUri,
    earnedEpochMs = earnedEpochMs,
    isHardcore = isHardcore,
)
