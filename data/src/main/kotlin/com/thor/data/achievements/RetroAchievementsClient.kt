package com.thor.data.achievements

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.data.network.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How a call to the site went, when the caller has to tell the user. */
sealed interface RetroAchievementsStatus {
    data object Connected : RetroAchievementsStatus
    data object NotConfigured : RetroAchievementsStatus
    data object InvalidCredentials : RetroAchievementsStatus
    data class Unreachable(val detail: String) : RetroAchievementsStatus
    data class Error(val detail: String) : RetroAchievementsStatus
}

/**
 * The RetroAchievements web API.
 *
 * Only the four calls this launcher actually needs, and deliberately no more.
 * The site exposes several dozen; every one of them is another shape to keep
 * working against an API that has no versioning, and the ones here cover the
 * whole feature: who the user is, what a console's games are called, how far
 * they have got in a batch of them, and what a single game's achievements are.
 *
 * Credentials go in the query string, which is the site's own scheme — `z` is
 * the username and `y` the web API key. That key is not the account password and
 * can be reset from the account's settings page, which is the only reason asking
 * for it is reasonable.
 */
@Singleton
class RetroAchievementsClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) {

    /** Whether the account answers at all. */
    suspend fun checkConnection(): RetroAchievementsStatus {
        val account = settings.retroAchievements.first()
        if (!account.isConfigured) return RetroAchievementsStatus.NotConfigured

        val url = endpoint("API_GetUserProfile.php", account.username, account.apiKey)
            ?.newBuilder()
            ?.addQueryParameter("u", account.username)
            ?.build()
            ?: return RetroAchievementsStatus.Error("Malformed URL")

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                when {
                    /*
                     * A 200 with an empty body is what a wrong key gets.
                     *
                     * The site does not answer 401. It returns an empty document,
                     * or `{"User": null}`, and a client that only checks the code
                     * reports a typo'd key as a working connection that finds
                     * nothing — which sends the user looking at their library
                     * instead of at the field they mistyped.
                     */
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        if (body.contains("\"User\"")) {
                            RetroAchievementsStatus.Connected
                        } else {
                            RetroAchievementsStatus.InvalidCredentials
                        }
                    }

                    response.code == 401 || response.code == 403 ->
                        RetroAchievementsStatus.InvalidCredentials

                    else -> RetroAchievementsStatus.Error("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            RetroAchievementsStatus.Unreachable(e.message ?: "No connection")
        }
    }

    /**
     * Every game on a console that has achievements.
     *
     * `f=1` is the filter that matters: without it this returns the site's whole
     * catalogue for the console, most of which has no achievement set and none
     * of which can ever match anything worth showing. On a large console the
     * difference is thousands of entries.
     */
    suspend fun gameList(consoleId: Int): List<RemoteGame> {
        val account = settings.retroAchievements.first()
        if (!account.isConfigured) return emptyList()

        val url = endpoint("API_GetGameList.php", account.username, account.apiKey)
            ?.newBuilder()
            ?.addQueryParameter("i", consoleId.toString())
            ?.addQueryParameter("f", "1")
            ?.build()
            ?: return emptyList()

        return request(url, "game list for console $consoleId")
            ?.let { body -> runCatching { json.decodeFromString<List<RemoteGame>>(body) }.getOrNull() }
            .orEmpty()
    }

    /**
     * How far the user has got in each of [gameIds].
     *
     * Batched because the alternative is one request per game, and a library of
     * a few hundred would be a few hundred requests against a service run on
     * donations. The site accepts a comma-separated list; callers are expected
     * to chunk to [PROGRESS_BATCH].
     */
    suspend fun userProgress(gameIds: List<Int>): Map<Int, RemoteProgress> {
        val account = settings.retroAchievements.first()
        if (!account.isConfigured || gameIds.isEmpty()) return emptyMap()

        val url = endpoint("API_GetUserProgress.php", account.username, account.apiKey)
            ?.newBuilder()
            ?.addQueryParameter("u", account.username)
            ?.addQueryParameter("i", gameIds.joinToString(","))
            ?.build()
            ?: return emptyMap()

        val body = request(url, "progress for ${gameIds.size} games") ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, RemoteProgress>>(body)
                .mapNotNull { (id, progress) -> id.toIntOrNull()?.let { it to progress } }
                .toMap()
        }.getOrElse {
            ThorLog.w(TAG, "Could not read progress response", it)
            emptyMap()
        }
    }

    /**
     * One game's achievements, with this user's progress against each.
     *
     * The expensive call, and the only one that returns the achievements
     * themselves — so it is made for the game being looked at rather than for
     * the library.
     */
    suspend fun gameProgress(gameId: Int): RemoteGameProgress? {
        val account = settings.retroAchievements.first()
        if (!account.isConfigured) return null

        val url = endpoint("API_GetGameInfoAndUserProgress.php", account.username, account.apiKey)
            ?.newBuilder()
            ?.addQueryParameter("u", account.username)
            ?.addQueryParameter("g", gameId.toString())
            ?.build()
            ?: return null

        val body = request(url, "achievements for game $gameId") ?: return null
        return runCatching { json.decodeFromString<RemoteGameProgress>(body) }.getOrElse {
            ThorLog.w(TAG, "Could not read achievements for $gameId", it)
            null
        }
    }

    private fun endpoint(path: String, user: String, key: String) =
        "$BASE_URL/$path".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("z", user)
            ?.addQueryParameter("y", key)
            ?.build()

    private suspend fun request(url: okhttp3.HttpUrl, what: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "RetroAchievements returned ${response.code} for $what")
                null
            } else {
                response.body?.string()?.takeIf(String::isNotBlank)
            }
        }
    } catch (e: IOException) {
        ThorLog.w(TAG, "Could not reach RetroAchievements for $what", e)
        null
    }

    companion object {
        private const val TAG = "RetroAchievements"
        private const val BASE_URL = "https://retroachievements.org/API"

        /**
         * How many games one progress request asks about.
         *
         * The site does not document a ceiling; this is well inside what it
         * answers reliably and keeps each URL a sane length.
         */
        const val PROGRESS_BATCH = 100
    }
}

/** One game in a console's catalogue. */
@Serializable
data class RemoteGame(
    @SerialName("ID") val id: Int,
    @SerialName("Title") val title: String = "",
    @SerialName("NumAchievements") val achievementCount: Int = 0,
    @SerialName("ImageIcon") val imagePath: String? = null,
)

/** The counts for one game, from the batched progress call. */
@Serializable
data class RemoteProgress(
    @SerialName("NumPossibleAchievements") val total: Int = 0,
    @SerialName("PossibleScore") val totalPoints: Int = 0,
    @SerialName("NumAchieved") val earned: Int = 0,
    @SerialName("ScoreAchieved") val earnedPoints: Int = 0,
    @SerialName("NumAchievedHardcore") val earnedHardcore: Int = 0,
    @SerialName("ScoreAchievedHardcore") val earnedPointsHardcore: Int = 0,
)

/** One game's full achievement set, with this user's progress against it. */
@Serializable
data class RemoteGameProgress(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("NumAchievements") val total: Int = 0,
    @SerialName("NumAwardedToUser") val earned: Int = 0,
    @SerialName("NumAwardedToUserHardcore") val earnedHardcore: Int = 0,
    /**
     * Keyed by achievement id, which is why this is a raw element.
     *
     * The site returns an object whose keys are the ids rather than a list, and
     * an empty set comes back as `[]` — an array where an object is declared,
     * which fails to deserialise as a map. Decoded by hand in [achievementList]
     * so that one quirk cannot take the whole response with it.
     */
    @SerialName("Achievements") val achievements: JsonElement? = null,
) {
    fun achievementList(json: Json): List<RemoteAchievement> {
        val element = achievements as? JsonObject ?: return emptyList()
        return element.values.mapNotNull { entry ->
            runCatching { json.decodeFromJsonElement(RemoteAchievement.serializer(), entry) }
                .getOrNull()
        }
    }
}

/**
 * A single achievement.
 *
 * The dates arrive as `"YYYY-MM-DD HH:MM:SS"` strings in the site's own zone and
 * are absent entirely when unearned, so they are kept as strings here and parsed
 * where a timestamp is actually wanted.
 */
@Serializable
data class RemoteAchievement(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeName") val badgeName: String? = null,
    @SerialName("DateEarned") val earnedAt: String? = null,
    @SerialName("DateEarnedHardcore") val earnedHardcoreAt: String? = null,
    @SerialName("DisplayOrder") val displayOrder: Int = 0,
)

/** Pulls a string field out of a raw object, for the few places one is needed. */
internal fun JsonObject.stringOrNull(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf(String::isNotBlank)
