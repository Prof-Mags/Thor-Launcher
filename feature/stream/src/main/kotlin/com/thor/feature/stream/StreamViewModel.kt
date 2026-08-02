package com.thor.feature.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.model.ControllerCommand
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamApp
import com.thor.core.model.StreamHost
import com.thor.data.stream.LaunchFailure
import com.thor.data.stream.LaunchStage
import com.thor.data.stream.PairingState
import com.thor.data.stream.StreamRepository
import com.thor.data.stream.StreamSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Something the section needs the shell to do.
 *
 * One-shot, and therefore not state: opening the stream window is an event, and
 * a value left in state would re-open it on the next recomposition or after a
 * rotation.
 */
sealed interface StreamEffect {
    /** The host has agreed to a session; show it. */
    data object OpenSession : StreamEffect
}

/** Actions that can occupy the selected-PC button row on the bottom display. */
enum class StreamHostAction {
    START_STREAM,
    STOP_SESSION,
    REFRESH,
    PAIR,
    CANCEL_PAIRING,
}

/** What the Stream section is showing, as one value both panels read. */
data class StreamUiState(
    val hosts: List<StreamHost> = emptyList(),
    /** Keyed by address, because that is what identifies a host before it answers. */
    val statuses: Map<String, HostStatus> = emptyMap(),
    val cursor: Int = 0,
    /** Horizontal controller cursor within the selected PC's visible actions. */
    val actionCursor: Int = 0,
    /** How pairing with the highlighted PC is going. */
    val pairing: PairingState = PairingState.Idle,
    /** What is in the "add a PC by address" field. */
    val newAddress: String = "",
    /**
     * Set while the PC is being asked to share its screen.
     *
     * Its own field rather than a screen of its own, because the host list stays
     * visible underneath and says which machine is being waited on.
     */
    val connecting: Boolean = false,
    /** Which step is running, so a stall says where it stalled. */
    val stage: LaunchStage? = null,
    val error: String? = null,
) {
    val selected: StreamHost? get() = hosts.getOrNull(cursor)

    fun statusOf(host: StreamHost): HostStatus =
        statuses[host.address] ?: HostStatus.Unknown

    /** Whether the highlighted PC is in a state where its screen can be shown. */
    val canStream: Boolean
        get() = (selected?.let(::statusOf) as? HostStatus.Online)?.paired == true

    /** How many PCs are online and paired, for the panel's summary line. */
    val readyCount: Int
        get() = hosts.count { (statusOf(it) as? HostStatus.Online)?.paired == true }

    /** Exactly the controls currently rendered for the highlighted PC. */
    val hostActions: List<StreamHostAction>
        get() {
            val host = selected ?: return emptyList()
            val status = statusOf(host)
            val online = status as? HostStatus.Online
            val pairingActive = pairing is PairingState.AwaitingPin ||
                pairing is PairingState.Verifying

            return when {
                connecting -> emptyList()
                pairingActive -> listOf(StreamHostAction.CANCEL_PAIRING)
                online?.paired == true && online.currentGame != null -> listOf(
                    StreamHostAction.START_STREAM,
                    StreamHostAction.STOP_SESSION,
                )
                online?.paired == true -> listOf(
                    StreamHostAction.START_STREAM,
                    StreamHostAction.REFRESH,
                )
                online != null -> listOf(
                    StreamHostAction.REFRESH,
                    StreamHostAction.PAIR,
                )
                else -> listOf(StreamHostAction.REFRESH)
            }
        }

    val focusedHostAction: StreamHostAction?
        get() = hostActions.getOrNull(actionCursor.coerceIn(0, (hostActions.size - 1).coerceAtLeast(0)))
}

/**
 * The Stream section.
 *
 * One view model for both panels, as the Movies section is: the top screen lists
 * the PCs and the bottom describes the highlighted one, which are two views of a
 * single cursor rather than two screens with their own state.
 */
@HiltViewModel
class StreamViewModel @Inject constructor(
    private val repository: StreamRepository,
    private val sessions: StreamSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    /**
     * Effects, buffered and never dropped.
     *
     * `extraBufferCapacity` rather than a replay: the shell may not be
     * collecting at the instant a slow connect finishes, and losing that event
     * would leave the user looking at a host list while a session runs unwatched
     * on the PC. Replaying it instead would re-open the window on every rotation.
     */
    private val _effects = MutableSharedFlow<StreamEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<StreamEffect> = _effects.asSharedFlow()

    val clientName: StateFlow<String> = repository.clientName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "Loki",
    )

    init {
        /*
         * Discovery runs for as long as the section is subscribed, not once when
         * it opens: a PC switched on while the user is looking at the screen
         * should appear on it, and one that was already announcing should not
         * have to be waited for twice.
         */
        viewModelScope.launchSafely(TAG) {
            repository.hosts.collect { hosts ->
                _uiState.update { state ->
                    state.copy(
                        hosts = hosts,
                        cursor = state.cursor.coerceIn(0, (hosts.size - 1).coerceAtLeast(0)),
                    )
                }
                // Newly seen hosts are asked about immediately; the list is short
                // and the answer is what makes the row worth reading.
                hosts.filterNot { it.address in _uiState.value.statuses }
                    .forEach(::refresh)
            }
        }
    }

    /**
     * Asks one host how it is, and records the answer.
     *
     * Per host rather than in a batch, so a PC that is asleep delays only its own
     * row — the others answer in the time it takes them.
     */
    fun refresh(host: StreamHost) {
        _uiState.update { it.copy(statuses = it.statuses + (host.address to HostStatus.Checking)) }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _uiState.update {
                    it.copy(
                        statuses = it.statuses + (
                            host.address to HostStatus.Offline(error.message ?: "Failed")
                            ),
                    )
                }
            },
        ) {
            val status = repository.status(host)
            _uiState.update { it.copy(statuses = it.statuses + (host.address to status)) }

            // A host that answers is worth keeping, so it is still listed when it
            // is asleep and no longer announcing itself.
            if (status is HostStatus.Online && host.discovered) repository.remember(host)
            if (status is HostStatus.Online) repository.setPaired(host.address, status.paired)
        }
    }

    fun refreshAll() = _uiState.value.hosts.forEach(::refresh)

    fun move(delta: Int) {
        val state = _uiState.value
        if (state.hosts.isEmpty()) return
        selectHost((state.cursor + delta).coerceIn(0, state.hosts.lastIndex))
    }

    /** Selects a host from controller movement or a direct tap on its card. */
    fun selectHost(index: Int) {
        val state = _uiState.value
        if (state.hosts.isEmpty()) return

        // The PIN and verification belong to the PC that started the exchange.
        // Keep that host selected until the user completes it or cancels with B.
        if (state.pairing is PairingState.AwaitingPin ||
            state.pairing is PairingState.Verifying
        ) {
            return
        }

        val next = index.coerceIn(0, state.hosts.lastIndex)
        if (next != state.cursor) {
            _uiState.update {
                it.copy(
                    cursor = next,
                    actionCursor = 0,
                    pairing = PairingState.Idle,
                    error = null,
                )
            }
        }
    }

    /** Moves through only the actions that are actually visible on the bottom panel. */
    fun moveAction(delta: Int) {
        val state = _uiState.value
        val actions = state.hostActions
        if (actions.isEmpty()) return
        val current = state.actionCursor.coerceIn(0, actions.lastIndex)
        _uiState.update { it.copy(actionCursor = (current + delta).mod(actions.size)) }
    }

    /** Runs the same operation used by the corresponding touch button. */
    fun performHostAction(action: StreamHostAction) {
        when (action) {
            StreamHostAction.START_STREAM -> shareScreen()
            StreamHostAction.STOP_SESSION -> stopHostSession()
            StreamHostAction.REFRESH -> _uiState.value.selected?.let(::refresh)
            StreamHostAction.PAIR -> pair()
            StreamHostAction.CANCEL_PAIRING -> cancelPairing()
        }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(newAddress = value) }
    }

    /** Saves the typed address, for a PC the network never announced. */
    fun addTypedHost() {
        val address = _uiState.value.newAddress.trim()
        if (address.isBlank()) return

        viewModelScope.launchSafely(TAG) {
            repository.addHost(address)
            _uiState.update { it.copy(newAddress = "") }
            refresh(StreamHost(address = address))
        }
    }

    fun forget(host: StreamHost) {
        viewModelScope.launchSafely(TAG) { repository.removeHost(host.address) }
    }

    /**
     * Pairs with the highlighted PC.
     *
     * The PIN appears the moment there is one, because the user has to carry it
     * to the PC and type it into Sunshine while the handshake waits — a code
     * shown afterwards would belong to an exchange that had already timed out.
     */
    fun pair() {
        val host = _uiState.value.selected ?: return
        if (_uiState.value.pairing is PairingState.AwaitingPin) return

        _uiState.update { it.copy(pairing = PairingState.Verifying) }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _uiState.update {
                    it.copy(pairing = PairingState.Failed("pairing", error.message ?: "Failed"))
                }
            },
        ) {
            val result = repository.pair(host) { pin ->
                _uiState.update { it.copy(pairing = PairingState.AwaitingPin(pin)) }
            }
            _uiState.update { it.copy(pairing = result) }
            if (result is PairingState.Paired) refresh(host)
        }
    }

    /**
     * Abandons an attempt, and tells the host so.
     *
     * A handshake left half-finished leaves Sunshine waiting, and the next
     * attempt is refused at the first step because another pairing is already in
     * progress — which reads as a broken host rather than an unfinished
     * conversation.
     */
    fun cancelPairing() {
        val host = _uiState.value.selected
        _uiState.update { it.copy(pairing = PairingState.Idle) }
        if (host != null) viewModelScope.launchSafely(TAG) { repository.cancelPairing(host) }
    }

    /**
     * Shows the highlighted PC's screen.
     *
     * The whole desktop rather than a chosen game, which is the difference
     * between this and a games launcher. A desktop is already running, so the
     * host answers at once — where starting a game means waiting for the game,
     * and everything that can go wrong while a game starts.
     *
     * Whatever is then run on the PC appears here, so nothing is given up by not
     * choosing beforehand.
     */
    fun shareScreen() {
        val host = _uiState.value.selected ?: return
        if (!_uiState.value.canStream || _uiState.value.connecting) return

        _uiState.update { it.copy(connecting = true, error = null) }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _uiState.update {
                    it.copy(
                        connecting = false,
                        stage = null,
                        error = error.message ?: "The PC would not share its screen.",
                    )
                }
            },
        ) {
            sessions.prepare(host, desktopOf(host)) { stage ->
                _uiState.update { it.copy(stage = stage) }
            }
            _uiState.update { it.copy(connecting = false, stage = null) }
            _effects.emit(StreamEffect.OpenSession)
        }
    }

    /**
     * The host's desktop entry, as the host itself names it.
     *
     * Asked for rather than assumed, because the id is the host's own and is not
     * a constant — Sunshine numbers its entries and a user who has reordered or
     * renamed them changes what that number is. The list is still read; it is
     * simply no longer shown.
     */
    private suspend fun desktopOf(host: StreamHost): StreamApp {
        val apps = repository.apps(host)
            ?: throw LaunchFailure(
                "The PC would not say what it can stream. Check Sunshine is still " +
                    "running, then try again.",
            )

        return apps.firstOrNull(StreamApp::isDesktop)
            /*
             * Falls back to whatever the host lists first.
             *
             * "Desktop" is Sunshine's own default entry and is present on any
             * untouched install, but it can be renamed or removed. Refusing
             * outright in that case would be refusing a PC that is perfectly able
             * to stream something.
             */
            ?: apps.firstOrNull()
            ?: throw LaunchFailure(
                "This PC has nothing configured to stream. Add a Desktop entry in " +
                    "Sunshine.",
            )
    }

    /**
     * Tells the host to stop streaming, rather than merely disconnecting.
     *
     * Offered because leaving a session keeps it alive on the PC — the right
     * default, but it leaves no way to close one from here short of walking to
     * the machine.
     */
    fun stopHostSession() {
        val host = _uiState.value.selected ?: return

        viewModelScope.launchSafely(TAG) {
            sessions.quit(host)
            refresh(host)
        }
    }

    /**
     * Routes one controller command into the section.
     *
     * @return true when the section consumed it. Everything else is left for the
     *   shell, so the nav bar, Home and the overlays keep working — a section
     *   that swallowed every press would be a room with no door.
     */
    fun handleCommand(command: ControllerCommand): Boolean = when (command) {
        ControllerCommand.NAVIGATE_UP -> { move(-1); true }
        ControllerCommand.NAVIGATE_DOWN -> { move(1); true }
        ControllerCommand.NAVIGATE_LEFT -> {
            if (_uiState.value.hostActions.isEmpty()) false else {
                moveAction(-1)
                true
            }
        }
        ControllerCommand.NAVIGATE_RIGHT -> {
            if (_uiState.value.hostActions.isEmpty()) false else {
                moveAction(1)
                true
            }
        }

        /*
         * Y is the secondary action on whatever is highlighted, and which one
         * that is depends on the PC.
         *
         * An unpaired host needs pairing; a paired one is already set up, and the
         * useful second thing to do with it is to stop a session it is running —
         * which is otherwise only possible by walking to the machine.
         */
        ControllerCommand.CONTEXT_MENU -> {
            if (_uiState.value.canStream) stopHostSession() else pair()
            true
        }

        // Back abandons a pairing attempt before it leaves the section, so an
        // exchange is never left half-finished on the host.
        ControllerCommand.BACK -> {
            if (_uiState.value.pairing != PairingState.Idle) {
                cancelPairing()
                true
            } else {
                false
            }
        }

        /*
         * Confirm commits the typed address when there is one, then shows the
         * screen, and otherwise re-asks the highlighted PC.
         *
         * Ordered that way because a half-typed address is an unfinished
         * instruction and finishing it is what the user is in the middle of.
         * Refreshing is the idle meaning of the button: for a PC that is not
         * ready, "is this machine actually reachable" is the question this screen
         * exists to answer.
         */
        ControllerCommand.CONFIRM -> {
            when {
                _uiState.value.newAddress.isNotBlank() -> addTypedHost()
                else -> _uiState.value.focusedHostAction?.let(::performHostAction)
            }
            true
        }

        else -> false
    }

    private companion object {
        const val TAG = "Stream"
    }
}
