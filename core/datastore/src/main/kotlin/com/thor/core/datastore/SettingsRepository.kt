package com.thor.core.datastore

import androidx.datastore.core.DataStore
import com.thor.core.common.log.ThorLog
import com.thor.core.model.AccessibilitySettings
import com.thor.core.model.AudioSettings
import com.thor.core.model.CloudSettings
import com.thor.core.model.ControlSettings
import com.thor.core.model.DeveloperSettings
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DockSettings
import com.thor.core.model.GridSpec
import com.thor.core.model.IconPack
import com.thor.core.model.LibrarySettings
import com.thor.core.model.MetadataSettings
import com.thor.core.model.RetroAchievementsSettings
import com.thor.core.model.MediaSettings
import com.thor.core.model.StreamSettings
import com.thor.core.model.MouseSettings
import com.thor.core.model.PerformanceSettings
import com.thor.core.model.RecordingSettings
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.ThorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for user configuration.
 *
 * Every mutator is a targeted `update…` call rather than a "write the whole
 * settings object" API, so two concurrent edits from different screens can't
 * clobber one another — DataStore serialises the read-modify-write for us.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<ThorSettings>,
) {

    /**
     * The full settings tree.
     *
     * IO errors are recovered rather than propagated: a launcher that crashes
     * because its preferences file is briefly unreadable is worse than one that
     * starts with defaults.
     */
    val settings: Flow<ThorSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                ThorLog.e("Settings", "Failed to read settings; using defaults", throwable)
                emit(ThorSettings.DEFAULT)
            } else {
                throw throwable
            }
        }
        .distinctUntilChanged()

    // Narrowed projections. Screens collect only the slice they render, so a
    // change to, say, cloud settings does not recompose the grid.
    val personalization: Flow<PersonalizationSettings> =
        settings.map { it.personalization }.distinctUntilChanged()
    /**
     * Coerced on read as well as on write.
     *
     * Writes go through [updateGrid], which clamps — but a settings file
     * produced by a restore, an import or an older schema bypasses that path
     * entirely, and the renderer divides by [GridSpec.columns] and passes
     * [GridSpec.labelLines] to `Text`. Both throw on a zero, so the invariant is
     * enforced at the boundary rather than trusted.
     */
    val grid: Flow<GridSpec> = settings.map { it.grid.coerced() }.distinctUntilChanged()
    val dock: Flow<DockSettings> = settings.map { it.dock }.distinctUntilChanged()
    val library: Flow<LibrarySettings> = settings.map { it.library }.distinctUntilChanged()
    val iconPacks: Flow<List<IconPack>> = settings.map { it.iconPacks }.distinctUntilChanged()
    val mouse: Flow<MouseSettings> = settings.map { it.mouse }.distinctUntilChanged()
    val media: Flow<MediaSettings> = settings.map { it.media }.distinctUntilChanged()
    val stream: Flow<StreamSettings> = settings.map { it.stream }.distinctUntilChanged()
    val metadata: Flow<MetadataSettings> = settings.map { it.metadata }.distinctUntilChanged()
    val retroAchievements: Flow<RetroAchievementsSettings> =
        settings.map { it.retroAchievements }.distinctUntilChanged()
    val controls: Flow<ControlSettings> = settings.map { it.controls }.distinctUntilChanged()
    val display: Flow<DisplaySettings> = settings.map { it.display }.distinctUntilChanged()
    val audio: Flow<AudioSettings> = settings.map { it.audio }.distinctUntilChanged()
    val performance: Flow<PerformanceSettings> =
        settings.map { it.performance }.distinctUntilChanged()
    val recording: Flow<RecordingSettings> =
        settings.map { it.recording }.distinctUntilChanged()
    val accessibility: Flow<AccessibilitySettings> =
        settings.map { it.accessibility }.distinctUntilChanged()
    val cloud: Flow<CloudSettings> = settings.map { it.cloud }.distinctUntilChanged()
    val developer: Flow<DeveloperSettings> =
        settings.map { it.developer }.distinctUntilChanged()

    suspend fun current(): ThorSettings = settings.first()

    suspend fun updatePersonalization(transform: (PersonalizationSettings) -> PersonalizationSettings) {
        edit { it.copy(personalization = transform(it.personalization)) }
    }

    suspend fun updateGrid(transform: (GridSpec) -> GridSpec) {
        edit { it.copy(grid = transform(it.grid).coerced()) }
    }

    suspend fun updateDock(transform: (DockSettings) -> DockSettings) {
        edit { it.copy(dock = transform(it.dock)) }
    }

    suspend fun updateLibrary(transform: (LibrarySettings) -> LibrarySettings) {
        edit { it.copy(library = transform(it.library)) }
    }

    suspend fun updateIconPacks(transform: (List<IconPack>) -> List<IconPack>) {
        edit { it.copy(iconPacks = transform(it.iconPacks)) }
    }

    suspend fun updateMouse(transform: (MouseSettings) -> MouseSettings) {
        edit { it.copy(mouse = transform(it.mouse)) }
    }

    suspend fun updateStream(transform: (StreamSettings) -> StreamSettings) {
        edit { it.copy(stream = transform(it.stream)) }
    }

    suspend fun updateMedia(transform: (MediaSettings) -> MediaSettings) {
        edit { it.copy(media = transform(it.media)) }
    }

    suspend fun updateMetadata(transform: (MetadataSettings) -> MetadataSettings) {
        edit { it.copy(metadata = transform(it.metadata)) }
    }

    suspend fun updateRetroAchievements(
        transform: (RetroAchievementsSettings) -> RetroAchievementsSettings,
    ) {
        edit { it.copy(retroAchievements = transform(it.retroAchievements)) }
    }

    suspend fun updateControls(transform: (ControlSettings) -> ControlSettings) {
        edit { it.copy(controls = transform(it.controls)) }
    }

    suspend fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) {
        edit { it.copy(display = transform(it.display)) }
    }

    suspend fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        edit { it.copy(audio = transform(it.audio)) }
    }

    suspend fun updatePerformance(transform: (PerformanceSettings) -> PerformanceSettings) {
        edit { it.copy(performance = transform(it.performance)) }
    }

    suspend fun updateRecording(transform: (RecordingSettings) -> RecordingSettings) {
        edit { it.copy(recording = transform(it.recording)) }
    }

    suspend fun updateAccessibility(transform: (AccessibilitySettings) -> AccessibilitySettings) {
        edit { it.copy(accessibility = transform(it.accessibility)) }
    }

    suspend fun updateCloud(transform: (CloudSettings) -> CloudSettings) {
        edit { it.copy(cloud = transform(it.cloud)) }
    }

    suspend fun updateDeveloper(transform: (DeveloperSettings) -> DeveloperSettings) {
        edit { it.copy(developer = transform(it.developer)) }
    }

    /** Records that the walkthrough has been seen, or clears it to show it again. */
    suspend fun setTutorialCompleted(completed: Boolean) {
        edit { it.copy(tutorialCompleted = completed) }
    }

    /** Records that the first-run permission list has been shown. */
    suspend fun setPermissionsPromptSeen(seen: Boolean) {
        edit { it.copy(permissionsPromptSeen = seen) }
    }

    /** Records that the edit-mode gestures have been explained. */
    suspend fun setEditModeTutorialSeen(seen: Boolean) {
        edit { it.copy(editModeTutorialSeen = seen) }
    }


    /** Records that an extension's own short walkthrough has been played. */
    suspend fun setExtensionTourSeen(id: String) {
        edit { it.copy(seenExtensionTours = it.seenExtensionTours + id) }
    }

    /** Adds or removes an optional part of the launcher, by extension id. */
    suspend fun setExtensionEnabled(id: String, enabled: Boolean) {
        edit {
            val next = if (enabled) it.enabledExtensions + id else it.enabledExtensions - id
            it.copy(enabledExtensions = next)
        }
    }

    /** Replaces everything — used by restore and by settings import. */
    suspend fun replaceAll(settings: ThorSettings) {
        edit { settings.copy(schemaVersion = ThorSettings.CURRENT_SCHEMA_VERSION) }
    }

    /** Resets to shipped defaults. */
    suspend fun resetToDefaults() {
        edit { ThorSettings.DEFAULT }
    }

    private suspend fun edit(transform: (ThorSettings) -> ThorSettings) {
        try {
            dataStore.updateData(transform)
        } catch (e: IOException) {
            ThorLog.e("Settings", "Failed to persist settings change", e)
        }
    }
}
