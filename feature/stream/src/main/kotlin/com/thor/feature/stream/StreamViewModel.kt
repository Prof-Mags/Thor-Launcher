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
import com.thor.feature.stream.couch.STREAM_COUCH_COLUMNS
import com.thor.feature.stream.couch.STREAM_HELP_SECTIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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

    /**
     * Takes a PC off the list.
     *
     * Offered for every machine, including the ones the network announced. A
     * discovered host reappears while it is still announcing itself, and that is
     * the honest behaviour rather than a reason to withhold the control: what is
     * being forgotten is the saved entry and its pairing, not the computer.
     */
    FORGET,
}

/**
 * The computers page's own controls, above the wall rather than on a PC.
 *
 * A list rather than two buttons drawn where they fit, because the controller
 * walks it: a control the pointer can press and the pad cannot is a control that
 * does not exist for whoever is sitting down. Refresh drops out when there are no
 * PCs — a cursor stop that cannot do anything is worse than one fewer stop.
 */
enum class StreamHeaderAction {
    /** Opens the section's instructions. The shortcut the reference puts here. */
    HELP,

    /** Re-asks every PC how it is. */
    REFRESH,
}

/**
 * Which page Couch Mode's Stream section is showing.
 *
 * Only the television layout has pages. The handheld arrangement shows both
 * things at once — the PCs on the top panel, the address field on the bottom —
 * because it has two panels to put them on. One screen does not, and a form
 * squeezed into the corner of a dashboard is a form nobody can read from a sofa.
 */
enum class StreamCouchPage {
    COMPUTERS,
    ADD_HOST,
    HELP,
}

/** Where the controller is on Couch Mode's computers page. */
enum class StreamCouchZone {
    /**
     * The destinations down the side.
     *
     * Reached by pressing left off the first column, which is where a television
     * interface keeps its side navigation. Before this the rail was a map the
     * pointer could click and the controller could not, so a page listed there
     * and nowhere else — help — was a page no viewer with a pad could open.
     */
    RAIL,

    /**
     * The page's own controls, along the top.
     *
     * Between the wall and the navigation bar, so pressing up walks out of the
     * screen the way it was walked into: the top row of cards, then the header,
     * then the shell. Before this the header was above the first card and
     * unreachable from it, which put Refresh and Help on a screen designed to be
     * driven from a sofa and left them to the pointer.
     */
    HEADER,

    /** The wall of PCs. */
    GRID,

    /**
     * The tile at the end of the wall that adds one.
     *
     * A place on the wall rather than a button somewhere, because that is what
     * makes it reachable: the cursor walks into it after the last machine, which
     * is exactly where somebody notices one is missing. It is not a host, so it
     * cannot be [GRID] with a cursor value — [StreamUiState.cursor] indexes the
     * list of PCs and there is no PC here.
     */
    ADD,

    /** The selected PC's buttons, along the foot. */
    ACTIONS,
}

/**
 * Which control on Couch Mode's add-a-PC form the controller is on.
 *
 * A short list rather than a cursor over a generated one: this form has two
 * fields and a button, and it will not grow — the protocol takes an address, the
 * name is the user's own label for it, and the pairing PIN travels the other way
 * (Loki shows one, Sunshine is told it), so there is nothing here to type it in.
 */
enum class StreamAddField {
    NAME,
    ADDRESS,
    SUBMIT,
}

/**
 * Where a row move lands on a grid [columns] wide holding [count] cards.
 *
 * Null means the move leaves the grid, which is the answer the caller acts on:
 * upward it hands the press back to the shell so the navigation bar is still
 * reachable from the top row, downward it moves to the selected PC's buttons.
 *
 * A move onto a short final row lands on its last card rather than nowhere. The
 * alternative — refusing because the cell directly below happens to be empty —
 * makes the last PC in a ragged grid reachable from one column only, which from
 * a sofa reads as the cursor being stuck.
 */
internal fun streamGridTarget(cursor: Int, count: Int, columns: Int, rows: Int): Int? {
    if (count <= 0 || columns <= 0) return null
    val target = cursor + rows * columns
    if (target in 0 until count) return target
    if (rows > 0 && cursor / columns < (count - 1) / columns) return count - 1
    return null
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
    /** The label the user is giving the PC being added, if any. */
    val newName: String = "",
    /** Which page the television layout is on; ignored by the handheld panels. */
    val page: StreamCouchPage = StreamCouchPage.COMPUTERS,
    /** Where the controller is on the television's computers page. */
    val zone: StreamCouchZone = StreamCouchZone.GRID,
    /** Which destination the rail's own cursor is on, while it holds one. */
    val railFocus: StreamCouchPage = StreamCouchPage.COMPUTERS,
    /** Which of the page's own controls the controller is on, while it holds one. */
    val headerCursor: Int = 0,
    /** Which section of the help page is being read. */
    val helpCursor: Int = 0,
    /** Which control on the television's add-a-PC form the controller is on. */
    val addField: StreamAddField = StreamAddField.ADDRESS,
    /**
     * Monotonic request to raise the keyboard for [addField].
     *
     * A counter rather than a flag, because the same field can be asked for
     * twice in a row — open the form, type nothing, press A again — and a
     * boolean that is already true says nothing the second time.
     */
    val keyboardRequest: Long = 0L,
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
                    StreamHostAction.FORGET,
                )
                online?.paired == true -> listOf(
                    StreamHostAction.START_STREAM,
                    StreamHostAction.REFRESH,
                    StreamHostAction.FORGET,
                )
                online != null -> listOf(
                    StreamHostAction.REFRESH,
                    StreamHostAction.PAIR,
                    StreamHostAction.FORGET,
                )
                // Last, and never where Confirm lands: the cursor arrives on the
                // first action, and a machine that is merely asleep must not be
                // one press from being forgotten.
                else -> listOf(StreamHostAction.REFRESH, StreamHostAction.FORGET)
            }
        }

    val focusedHostAction: StreamHostAction?
        get() = hostActions.getOrNull(actionCursor.coerceIn(0, (hostActions.size - 1).coerceAtLeast(0)))

    /**
     * Exactly the controls drawn above the wall, in the order they are drawn.
     *
     * Refresh is only there when there is something to refresh. Discovery runs
     * on its own, so on an empty page the button would re-ask a list of nothing
     * — and both the cursor and the pointer would find a control that visibly
     * does nothing at all.
     */
    val headerActions: List<StreamHeaderAction>
        get() = if (hosts.isEmpty()) {
            listOf(StreamHeaderAction.HELP)
        } else {
            listOf(StreamHeaderAction.HELP, StreamHeaderAction.REFRESH)
        }

    val focusedHeaderAction: StreamHeaderAction?
        get() = headerActions.getOrNull(
            headerCursor.coerceIn(0, (headerActions.size - 1).coerceAtLeast(0)),
        )
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
     *
     * @param announce whether to show the check while it is happening. True for a
     *   press of Refresh, where the user has asked a question and is owed the sight
     *   of it being asked. False for the periodic re-check that runs behind the
     *   screen: that one flipped every badge to "Checking" and back on a timer, so
     *   a page nobody had touched appeared to be reloading itself every few
     *   seconds. The answer still lands when it arrives; only the churn goes.
     */
    fun refresh(host: StreamHost, announce: Boolean = true) {
        if (announce) {
            _uiState.update {
                it.copy(statuses = it.statuses + (host.address to HostStatus.Checking))
            }
        }

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

    /**
     * Re-asks every PC how it is.
     *
     * Not while a handshake or a launch is in flight. Both are conversations
     * already under way with a host, and re-asking mid-exchange rewrites the
     * badge the user is watching for the answer to a different question.
     */
    fun refreshAll(announce: Boolean = true) {
        val state = _uiState.value
        if (state.connecting || state.pairing != PairingState.Idle) return
        state.hosts.forEach { refresh(it, announce = announce) }
    }

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
                    // Choosing a PC is a statement about where the cursor is, so
                    // the television's cursor comes back to the wall of machines
                    // from wherever it was. Tapping a card while the controller
                    // sat on the button row below would otherwise select a
                    // machine and light none of them.
                    zone = StreamCouchZone.GRID,
                )
            }
        } else if (state.zone != StreamCouchZone.GRID) {
            _uiState.update { it.copy(zone = StreamCouchZone.GRID) }
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
            StreamHostAction.FORGET -> _uiState.value.selected?.let(::forget)
        }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(newAddress = value) }
    }

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(newName = value) }
    }

    /**
     * Opens the television's add-a-PC form, already typing.
     *
     * The keyboard comes up on the address rather than waiting to be asked for.
     * Somebody who has just chosen "add a PC" is there to type an address; the
     * name above it is a label they may not want at all, and is one press up.
     */
    fun openAddHost() {
        _uiState.update {
            it.copy(
                page = StreamCouchPage.ADD_HOST,
                railFocus = StreamCouchPage.ADD_HOST,
                zone = StreamCouchZone.GRID,
                addField = StreamAddField.ADDRESS,
                keyboardRequest = it.keyboardRequest + 1L,
            )
        }
    }

    fun closeAddHost() {
        _uiState.update {
            it.copy(page = StreamCouchPage.COMPUTERS, railFocus = StreamCouchPage.COMPUTERS)
        }
    }

    /** Opens the section's own instructions, from the rail or a pointer. */
    fun openHelp() {
        _uiState.update {
            it.copy(
                page = StreamCouchPage.HELP,
                railFocus = StreamCouchPage.HELP,
                zone = StreamCouchZone.GRID,
                helpCursor = 0,
            )
        }
    }

    /** Reads down the help page a section at a time. */
    fun moveHelp(delta: Int) = focusHelpSection(_uiState.value.helpCursor + delta)

    /** Puts the help cursor on one section, for a pointer that clicked it. */
    fun focusHelpSection(index: Int) {
        _uiState.update {
            it.copy(
                helpCursor = index.coerceIn(0, STREAM_HELP_SECTIONS.lastIndex.coerceAtLeast(0)),
            )
        }
    }

    /** Sends the controller to the rail, or to whichever destination it names. */
    fun focusRail(page: StreamCouchPage) {
        _uiState.update {
            it.copy(zone = StreamCouchZone.RAIL, railFocus = page)
        }
    }

    /** Sends the controller to the page's own controls, above the wall. */
    fun focusHeader(index: Int = _uiState.value.headerCursor) {
        _uiState.update {
            it.copy(
                zone = StreamCouchZone.HEADER,
                headerCursor = index.coerceIn(0, (it.headerActions.size - 1).coerceAtLeast(0)),
            )
        }
    }

    /** Runs the same operation as a tap on the corresponding header button. */
    fun performHeaderAction(action: StreamHeaderAction) = when (action) {
        StreamHeaderAction.HELP -> openHelp()
        StreamHeaderAction.REFRESH -> refreshAll()
    }

    fun openPage(page: StreamCouchPage) = when (page) {
        StreamCouchPage.COMPUTERS -> closeAddHost()
        StreamCouchPage.ADD_HOST -> openAddHost()
        StreamCouchPage.HELP -> openHelp()
    }

    /** Moves through the form's fields, and asks for the keyboard on one. */
    fun focusAddField(field: StreamAddField) {
        _uiState.update { it.copy(addField = field) }
    }

    fun requestKeyboard() {
        _uiState.update { it.copy(keyboardRequest = it.keyboardRequest + 1L) }
    }

    /** Saves the typed address, for a PC the network never announced. */
    fun addTypedHost() {
        val address = _uiState.value.newAddress.trim()
        if (address.isBlank()) return
        val name = _uiState.value.newName.trim()

        viewModelScope.launchSafely(TAG) {
            repository.addHost(address, name)
            // Back to the list, because that is where the PC now is. Leaving the
            // form up with its fields cleared reads as the address having been
            // rejected.
            _uiState.update {
                it.copy(
                    newAddress = "",
                    newName = "",
                    page = StreamCouchPage.COMPUTERS,
                )
            }
            refresh(StreamHost(address = address, name = name))
        }
    }

    fun forget(host: StreamHost) {
        viewModelScope.launchSafely(TAG) { repository.removeHost(host.address) }
        /*
         * Its answer goes with it.
         *
         * Statuses are keyed by address, so a machine re-added at the same one
         * would arrive wearing whatever the old entry last reported — "offline"
         * on a PC the user has just gone and switched on, with nothing on screen
         * to say the reading is from before they removed it.
         */
        _uiState.update {
            it.copy(
                statuses = it.statuses - host.address,
                zone = StreamCouchZone.GRID,
            )
        }
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
     * @param couch whether the television layout is on screen, which is a
     *   different set of controls rather than the same one rearranged: the PCs
     *   are a grid there instead of a column, so Left and Right move between
     *   machines rather than between the selected machine's buttons, and adding
     *   one is a page rather than a field that is always visible.
     * @return true when the section consumed it. Everything else is left for the
     *   shell, so the nav bar, Home and the overlays keep working — a section
     *   that swallowed every press would be a room with no door.
     */
    fun handleCommand(command: ControllerCommand, couch: Boolean = false): Boolean =
        if (couch) handleCouchCommand(command) else handleHandheldCommand(command)

    private fun handleHandheldCommand(command: ControllerCommand): Boolean = when (command) {
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

        ControllerCommand.SEARCH -> {
            requestKeyboard()
            true
        }

        else -> false
    }

    // ---- Couch Mode ---------------------------------------------------------

    private fun handleCouchCommand(command: ControllerCommand): Boolean {
        // The rail is drawn beside every page and takes the controller from all
        // of them, so it is asked before the page is.
        if (_uiState.value.zone == StreamCouchZone.RAIL) return handleRailCommand(command)
        // Only the computers page has a header, and every way of leaving that
        // page puts the cursor back on the wall, so this zone implies it.
        if (_uiState.value.zone == StreamCouchZone.HEADER) return handleHeaderCommand(command)

        when (_uiState.value.page) {
            StreamCouchPage.ADD_HOST -> return handleAddHostCommand(command)
            StreamCouchPage.HELP -> return handleHelpCommand(command)
            StreamCouchPage.COMPUTERS -> Unit
        }

        val state = _uiState.value
        val onActions = state.zone == StreamCouchZone.ACTIONS
        // The machines, and then the tile that adds one.
        val cells = state.hosts.size + 1
        val cell = if (state.zone == StreamCouchZone.ADD) state.hosts.size else state.cursor

        return when (command) {
            /*
             * Sideways is between machines, and only between buttons once the
             * cursor is on them.
             *
             * Always consumed while there are PCs on screen, including at the
             * ends of a row. There is nothing to the left of the first card but
             * the rail, which is a map rather than a destination, and handing
             * the press to the shell from there moves something the user cannot
             * see.
             */
            // Left off the first column is the rail, which is where a television
            // interface keeps the way out of the page it is on.
            ControllerCommand.NAVIGATE_LEFT -> when {
                onActions -> { moveAction(-1); true }
                state.hosts.isEmpty() -> { focusRail(state.page); true }
                cell % STREAM_COUCH_COLUMNS == 0 -> { focusRail(state.page); true }
                else -> moveToCell(cell - 1, cells)
            }

            ControllerCommand.NAVIGATE_RIGHT -> when {
                onActions -> { moveAction(1); true }
                state.hosts.isEmpty() -> false
                else -> moveToCell(cell + 1, cells)
            }

            // Up off the top row is the page's own controls, and up off those is
            // the navigation bar — so the way out of the screen is the way in,
            // one row at a time, rather than a jump over a header the cursor
            // could never land on.
            ControllerCommand.NAVIGATE_UP -> when {
                onActions -> { setZone(StreamCouchZone.GRID); true }
                else -> streamGridTarget(cell, cells, STREAM_COUCH_COLUMNS, rows = -1)
                    ?.let { moveToCell(it, cells) }
                    ?: run { focusHeader(); true }
            }

            ControllerCommand.NAVIGATE_DOWN -> when {
                // Down from the buttons is the bottom of the screen. Declined
                // rather than consumed, so whatever the shell does with it is
                // the same thing it does everywhere else.
                onActions -> false
                else -> {
                    val target = streamGridTarget(cell, cells, STREAM_COUCH_COLUMNS, rows = 1)
                    when {
                        target != null -> moveToCell(target, cells)
                        // The band is a stop whenever it has a control on it,
                        // and with no PC chosen the one it has is "add a PC" —
                        // which is the whole of what an empty page can offer.
                        state.hostActions.isNotEmpty() || state.selected == null -> {
                            setZone(StreamCouchZone.ACTIONS)
                            true
                        }
                        else -> false
                    }
                }
            }

            /*
             * A on a PC does the obvious thing to it, which depends on the PC.
             *
             * "Select a computer to start streaming" is what the screen says, so
             * a ready machine streams. One that has never been paired pairs,
             * because that is the step between it and streaming, and one that is
             * not answering is asked again — the question this screen exists for.
             */
            ControllerCommand.CONFIRM -> {
                when {
                    state.hosts.isEmpty() -> openAddHost()
                    state.zone == StreamCouchZone.ADD -> openAddHost()
                    onActions -> state.focusedHostAction?.let(::performHostAction)
                    else -> confirmSelectedHost()
                }
                true
            }

            /*
             * Y is the highlighted PC's second action, exactly as it is on the
             * handheld, and this screen does not get to redefine it.
             *
             * It briefly did — adding a PC needed a way in, and Y was the button
             * going spare. It was not going spare. An unpaired host needs pairing
             * before it can be streamed at all, Y is how that is done everywhere
             * else in the launcher, and a viewer whose machines had come unpaired
             * pressed it and got a form for a PC they already had. Adding one is
             * reached from the tile at the end of the wall instead, which is
             * where the cursor already ends up.
             */
            ControllerCommand.CONTEXT_MENU -> {
                if (state.canStream) stopHostSession() else pair()
                true
            }

            // The search key is the launcher's "type something", and the only
            // thing there is to type here is an address.
            ControllerCommand.SEARCH -> {
                openAddHost()
                true
            }

            ControllerCommand.BACK -> {
                if (state.pairing != PairingState.Idle) {
                    cancelPairing()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    /**
     * The rail, while the controller is in it.
     *
     * Up and down walk the destinations, right goes back to the page, and A opens
     * whatever is under the cursor. Back does what right does rather than leaving
     * the section: the rail is a step into the screen, so the way out of it is the
     * way it was entered.
     */
    private fun handleRailCommand(command: ControllerCommand): Boolean {
        val pages = StreamCouchPage.entries
        val current = pages.indexOf(_uiState.value.railFocus)

        return when (command) {
            ControllerCommand.NAVIGATE_UP -> {
                focusRail(pages[(current - 1).coerceIn(0, pages.lastIndex)])
                true
            }

            ControllerCommand.NAVIGATE_DOWN -> {
                focusRail(pages[(current + 1).coerceIn(0, pages.lastIndex)])
                true
            }

            ControllerCommand.NAVIGATE_RIGHT, ControllerCommand.BACK -> {
                setZone(StreamCouchZone.GRID)
                true
            }

            ControllerCommand.NAVIGATE_LEFT -> true

            ControllerCommand.CONFIRM -> {
                openPage(_uiState.value.railFocus)
                true
            }

            else -> false
        }
    }

    /**
     * The page's own controls, while the controller is on them.
     *
     * Up is declined, and that is the point of the zone rather than an omission:
     * the shell only ever sees the presses this screen turns down, so this is
     * what keeps the navigation bar one press above the top of the page.
     */
    private fun handleHeaderCommand(command: ControllerCommand): Boolean {
        val actions = _uiState.value.headerActions
        val current = _uiState.value.headerCursor.coerceIn(0, (actions.size - 1).coerceAtLeast(0))

        return when (command) {
            ControllerCommand.NAVIGATE_LEFT -> {
                // Left off the first control is the rail, exactly as it is from
                // the first column of the wall below.
                if (current <= 0) focusRail(_uiState.value.page) else focusHeader(current - 1)
                true
            }

            ControllerCommand.NAVIGATE_RIGHT -> {
                focusHeader((current + 1).coerceAtMost((actions.size - 1).coerceAtLeast(0)))
                true
            }

            ControllerCommand.NAVIGATE_DOWN, ControllerCommand.BACK -> {
                setZone(StreamCouchZone.GRID)
                true
            }

            ControllerCommand.NAVIGATE_UP -> false

            ControllerCommand.CONFIRM -> {
                _uiState.value.focusedHeaderAction?.let(::performHeaderAction)
                true
            }

            else -> false
        }
    }

    private fun handleHelpCommand(command: ControllerCommand): Boolean = when (command) {
        ControllerCommand.NAVIGATE_UP -> {
            moveHelp(-1)
            true
        }

        ControllerCommand.NAVIGATE_DOWN -> {
            moveHelp(1)
            true
        }

        // Left is the rail from here too, so the page has a way back that is not
        // only Back — the same press reaches the destinations from every page.
        ControllerCommand.NAVIGATE_LEFT -> {
            focusRail(StreamCouchPage.HELP)
            true
        }

        ControllerCommand.NAVIGATE_RIGHT -> true

        ControllerCommand.BACK -> {
            closeAddHost()
            true
        }

        else -> false
    }

    private fun handleAddHostCommand(command: ControllerCommand): Boolean = when (command) {
        ControllerCommand.NAVIGATE_UP -> {
            moveAddField(-1)
            true
        }

        ControllerCommand.NAVIGATE_DOWN -> {
            moveAddField(1)
            true
        }

        /*
         * Consumed and ignored. The form is a column, so sideways means nothing
         * here — and letting it through would scroll the grid this page is drawn
         * over, which is the one thing on screen the user is not looking at.
         */
        ControllerCommand.NAVIGATE_LEFT -> {
            focusRail(StreamCouchPage.ADD_HOST)
            true
        }

        ControllerCommand.NAVIGATE_RIGHT -> true

        ControllerCommand.CONFIRM -> {
            when (_uiState.value.addField) {
                StreamAddField.SUBMIT -> addTypedHost()
                else -> requestKeyboard()
            }
            true
        }

        ControllerCommand.CONTEXT_MENU, ControllerCommand.SEARCH -> {
            requestKeyboard()
            true
        }

        ControllerCommand.BACK -> {
            closeAddHost()
            true
        }

        else -> false
    }

    private fun setZone(zone: StreamCouchZone) {
        _uiState.update { it.copy(zone = zone) }
    }

    /**
     * Puts the cursor on one cell of the wall, which may be the tile at the end.
     *
     * Always reports true when there is a wall at all, including at its edges: a
     * press that stops at the end of a row has still been dealt with, and handing
     * it back to the shell would move something off screen instead.
     */
    private fun moveToCell(cell: Int, cells: Int): Boolean {
        if (cells <= 0) return false
        val target = cell.coerceIn(0, cells - 1)
        if (target >= _uiState.value.hosts.size) {
            setZone(StreamCouchZone.ADD)
        } else {
            // Set here as well as inside the selection, because a selection can
            // decline — it is held on the PC being paired until that finishes —
            // and a declined move must not leave the add tile lit as though the
            // cursor were still on it.
            setZone(StreamCouchZone.GRID)
            selectHost(target)
        }
        return true
    }

    private fun moveAddField(delta: Int) {
        val fields = StreamAddField.entries
        val current = fields.indexOf(_uiState.value.addField)
        val next = (current + delta).coerceIn(0, fields.lastIndex)
        _uiState.update { it.copy(addField = fields[next]) }
    }

    private fun confirmSelectedHost() {
        val actions = _uiState.value.hostActions
        when {
            StreamHostAction.START_STREAM in actions -> shareScreen()
            StreamHostAction.PAIR in actions -> pair()
            StreamHostAction.REFRESH in actions -> _uiState.value.selected?.let(::refresh)
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "Stream"
    }
}
