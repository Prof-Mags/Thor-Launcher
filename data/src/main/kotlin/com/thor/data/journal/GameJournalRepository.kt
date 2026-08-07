package com.thor.data.journal

import android.content.Context
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.profile.ActiveProfileId
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.database.dao.GameNoteDao
import com.thor.core.database.model.GameNoteEntity
import com.thor.core.model.GameJournal
import com.thor.core.model.GameNote
import com.thor.core.model.Screenshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The half of a game's record that the user wrote.
 *
 * Two stores behind one door, because they answer one question — what have I
 * recorded about this game — and every surface that asks wants both. The note is
 * a row, because it is edited in place and has to be observed; the screenshots
 * are files, because a file *is* the record and a table beside them would be a
 * second source of truth that can disagree with the disk.
 *
 * Everything is inside the active profile's directory, which is what makes both
 * survive a backup and a restore without either feature knowing that backups
 * exist; see [ProfileFiles.screenshots].
 */
@Singleton
class GameJournalRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val noteDao: GameNoteDao,
    @ActiveProfileId private val profileIds: Flow<String>,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Everything recorded about one game.
     *
     * The note arrives as a flow because it is edited on one surface and read on
     * two others. The screenshots are re-listed whenever the note changes or a
     * capture lands, rather than watched with a file observer: a directory of at
     * most a few dozen files is cheap to list, and a `FileObserver` held per
     * visible game would be a watcher per cell on a grid.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(entryId: String): Flow<GameJournal> = profileIds.flatMapLatest { profileId ->
        combine(
            noteDao.observe(entryId),
            captureSignal,
        ) { note, _ ->
            GameJournal(
                note = note?.toDomain(),
                screenshots = listScreenshots(profileId, entryId),
            )
        }
    }

    suspend fun noteFor(entryId: String): GameNote? = withContext(ioDispatcher) {
        noteDao.getById(entryId)?.toDomain()
    }

    /**
     * Writes a note, or removes it when the text is emptied.
     *
     * Blank deletes rather than storing an empty string, so "clear this note" is
     * the same gesture as writing one and there is no second command to find — the
     * editor's button reads Clear when the field has been emptied, and does this.
     * An empty row would also be a note that exists and says nothing.
     */
    suspend fun setNote(entryId: String, body: String, nowMs: Long) = withContext(ioDispatcher) {
        val trimmed = body.trim().take(GameNote.MAX_LENGTH)
        if (trimmed.isEmpty()) {
            noteDao.delete(entryId)
            return@withContext
        }
        noteDao.upsert(
            GameNoteEntity(entryId = entryId, body = trimmed, updatedAtEpochMs = nowMs),
        )
    }

    /**
     * Files a captured frame against a game.
     *
     * Named by the moment it was taken, so the directory sorts chronologically
     * without anything having to read the files — which is also what makes the
     * listing below cheap enough to redo rather than watch.
     */
    suspend fun addScreenshot(entryId: String, png: ByteArray, nowMs: Long): Screenshot? =
        withContext(ioDispatcher) {
            val profileId = profileIds.first()
            val directory = ProfileFiles.screenshots(appContext, profileId, entryId)
            if (!directory.exists() && !directory.mkdirs()) {
                ThorLog.w(TAG, "Could not create a screenshot directory for $entryId")
                return@withContext null
            }

            val file = File(directory, "$nowMs$EXTENSION")
            runCatching { file.writeBytes(png) }
                .onFailure { error ->
                    ThorLog.w(TAG, "Could not write a screenshot for $entryId: ${error.message}")
                    return@withContext null
                }

            notifyCapture()
            Screenshot(
                entryId = entryId,
                path = file.absolutePath,
                capturedAtEpochMs = nowMs,
                sizeBytes = file.length(),
            )
        }

    /**
     * Forgets everything recorded about a game.
     *
     * Called when a game is deleted on purpose, and *only* then. A rescan that
     * cannot find a ROM must not reach this: a moved file is exactly the case
     * where the note about where you got to is the last record of it, which is
     * also why `game_notes` has no foreign key onto `games`.
     */
    suspend fun forget(entryId: String) = withContext(ioDispatcher) {
        noteDao.delete(entryId)
        val profileId = profileIds.first()
        ProfileFiles.screenshots(appContext, profileId, entryId).deleteRecursively()
        notifyCapture()
    }

    private fun listScreenshots(profileId: String, entryId: String): List<Screenshot> {
        val directory = ProfileFiles.screenshots(appContext, profileId, entryId)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION) }
            ?.map { file ->
                Screenshot(
                    entryId = entryId,
                    path = file.absolutePath,
                    // From the name rather than from `lastModified`, which a copy
                    // between devices rewrites — the name is what a restore keeps.
                    capturedAtEpochMs = file.nameWithoutExtension.toLongOrNull()
                        ?: file.lastModified(),
                    sizeBytes = file.length(),
                )
            }
            // Newest first, matching every other list of things you did.
            ?.sortedByDescending(Screenshot::capturedAtEpochMs)
            .orEmpty()
    }

    /**
     * A tick whenever the files change under us.
     *
     * The note has a real flow behind it; the files do not, and combining against
     * a counter is what makes a fresh capture appear without a `FileObserver` per
     * visible game. Seeded so the first collection emits immediately rather than
     * waiting for a capture that may never come.
     */
    private val captureCount = MutableStateFlow(0)
    private val captureSignal: Flow<Int> = captureCount
    private fun notifyCapture() { captureCount.value += 1 }

    private companion object {
        const val TAG = "Journal"
        const val EXTENSION = ".png"
    }
}

private fun GameNoteEntity.toDomain(): GameNote = GameNote(
    entryId = entryId,
    body = body,
    updatedAtEpochMs = updatedAtEpochMs,
)
