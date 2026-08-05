package com.thor.data.achievements

import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.log.ThorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the achievement sync is doing. */
sealed interface AchievementSyncState {
    data object Idle : AchievementSyncState

    data class Running(val done: Int, val total: Int) : AchievementSyncState {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total.toFloat()
    }

    /** @param matched games that ended up with an achievement set. */
    data class Completed(val matched: Int) : AchievementSyncState

    data class Failed(val reason: String) : AchievementSyncState
}

/**
 * Runs the achievement sync, and says so while it does.
 *
 * On the application scope rather than a screen's, like the library and metadata
 * syncs it sits beside: this walks a whole library against a remote service, and
 * leaving the settings page — which is where it is started from — should not
 * cancel it half way and leave some games matched and others not.
 */
@Singleton
class AchievementSyncManager @Inject constructor(
    private val repository: AchievementRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<AchievementSyncState>(AchievementSyncState.Idle)
    val state: StateFlow<AchievementSyncState> = _state.asStateFlow()

    private var runningJob: Job? = null

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** Starts a sync, or does nothing if one is already running. */
    fun sync() {
        if (isRunning) return
        runningJob = scope.launch {
            _state.value = AchievementSyncState.Running(done = 0, total = 0)
            runCatching {
                repository.syncLibrary { done, total ->
                    _state.value = AchievementSyncState.Running(done, total)
                }
            }.onSuccess { matched ->
                _state.value = AchievementSyncState.Completed(matched)
            }.onFailure { error ->
                ThorLog.w(TAG, "Achievement sync failed", error)
                _state.value = AchievementSyncState.Failed(
                    error.message ?: "Could not reach RetroAchievements",
                )
            }
        }
    }

    fun cancel() {
        runningJob?.cancel()
        runningJob = null
        _state.value = AchievementSyncState.Idle
    }

    /** Clears a finished state, so the row stops reporting the last run. */
    fun acknowledge() {
        if (!isRunning) _state.value = AchievementSyncState.Idle
    }

    private companion object {
        const val TAG = "Achievements"
    }
}
