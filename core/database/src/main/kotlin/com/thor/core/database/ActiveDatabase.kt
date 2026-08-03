package com.thor.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.thor.core.common.log.ThorLog
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.common.profile.ProfileMigrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The library database of whoever is signed in.
 *
 * Each profile owns a database file, so switching profiles means switching Room
 * instances. Instances are cached rather than rebuilt: Room tolerates being
 * asked twice for the same file, but two live instances over one file defeats
 * its invalidation tracking, and switching back and forth is the normal case.
 *
 * Nothing is closed on a switch. A closed database throws for anything still
 * holding a reference — a scan finishing, a flow being torn down — and the cost
 * of an idle open handle is a file descriptor.
 */
class ActiveDatabase(
    private val context: Context,
    private val migrator: ProfileMigrator,
    profileIds: Flow<String>,
    scope: CoroutineScope,
) {

    private val instances = mutableMapOf<String, ThorDatabase>()

    val current: StateFlow<ThorDatabase?> = profileIds
        .filter(String::isNotEmpty)
        .distinctUntilChanged()
        .map(::databaseFor)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The open database, waiting for one if the profile is not resolved yet.
     *
     * Blocking is safe here in practice and unavoidable in principle: the DAO
     * proxy has to satisfy non-suspending calls, and there is no database to
     * give them until a profile exists. In every path but the very first frame
     * of a cold start the value is already present.
     */
    fun require(): ThorDatabase = current.value ?: runBlocking {
        current.filterNotNull().let { flow ->
            var resolved: ThorDatabase? = null
            kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                flow.collect { resolved = it; return@collect }
            }
            resolved ?: error("No profile database available")
        }
    }

    @Synchronized
    private fun databaseFor(profileId: String): ThorDatabase = instances.getOrPut(profileId) {
        // Room creates an empty database for a path with no file, so a legacy
        // library moved in afterwards would sit beside a fresh empty one that
        // wins. The move has to happen first.
        migrator.adoptLegacyData(profileId)
        ThorLog.i(TAG, "Opening library for profile $profileId")
        Room.databaseBuilder(
            context = context,
            klass = ThorDatabase::class.java,
            name = ProfileFiles.database(context, profileId).path,
        )
            .addMigrations(*ThorMigrations.ALL)
            // WAL lets the grid keep reading while a library scan writes, which is
            // what stops a scan from stuttering the UI on a large ROM set.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    private companion object {
        const val TAG = "ActiveDatabase"
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
