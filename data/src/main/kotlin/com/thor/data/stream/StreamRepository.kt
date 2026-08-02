package com.thor.data.stream

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamApp
import com.thor.core.model.StreamHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Stream section's hosts.
 *
 * Two sources that have to read as one list: machines the user typed in, which
 * persist, and machines the network announced, which do not. Merging them here
 * means the screen shows "your PCs" rather than two lists the user has to
 * reconcile — and a host that is both saved and currently announcing is one
 * entry, not two.
 */
@Singleton
class StreamRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val discovery: HostDiscovery,
    private val client: StreamHostClient,
    private val pairing: PairingClient,
    private val launcher: StreamLauncher,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Saved hosts first, then anything discovered that is not already saved.
     *
     * Matched on address rather than on name or id: a discovered host has no id
     * until it has answered, and its announced name is whatever the PC calls
     * itself rather than what the user called it. The address is the only thing
     * both forms are guaranteed to have.
     */
    val hosts: Flow<List<StreamHost>> = combine(
        settings.stream,
        /*
         * Starts with nothing found, and that is not a formality.
         *
         * `combine` produces nothing at all until *every* source has emitted at
         * least once, and discovery only emits when a service resolves. On a
         * network where nothing is announced — a VPN, another subnet, or simply
         * no PC switched on — it never emits, so this whole flow never emitted,
         * so the screen showed an empty list however many hosts were saved.
         *
         * The symptom was precise and misleading: adding a PC by address wrote
         * it to settings and cleared the box, exactly as though it had worked,
         * and the list stayed empty because it was still waiting for a
         * discovery result that was never coming.
         */
        discovery.hosts().onStart { emit(emptyList()) },
    ) { streamSettings, discovered ->
        val saved = streamSettings.hosts.filter(StreamHost::isUsable)
        if (!streamSettings.discoverAutomatically) return@combine saved

        val savedAddresses = saved.mapTo(mutableSetOf(), StreamHost::address)
        saved + discovered.filterNot { it.address in savedAddresses }
    }

    /** What the host will list THOR as, for the pairing screen to show. */
    val clientName: Flow<String> = settings.stream.map { it.clientName }

    suspend fun status(host: StreamHost): HostStatus = withContext(ioDispatcher) {
        client.status(host, clientId())
    }

    /**
     * Pairs with a host, reporting the PIN as soon as there is one to show.
     *
     * The PIN has to reach the screen before the handshake waits on it — the user
     * carries it to the PC and types it into Sunshine, and a PIN that appeared
     * only after the exchange finished would be a code for a conversation that
     * had already failed.
     */
    suspend fun pair(host: StreamHost, onPin: (String) -> Unit): PairingState =
        withContext(ioDispatcher) {
            val name = settings.stream.first().clientName.ifBlank { "Loki" }
            val result = pairing.pair(host, clientId(), name, onPin)
            if (result is PairingState.Paired) setPaired(host.address, true)
            result
        }

    /** The host's library, or null when it could not be asked. */
    suspend fun apps(host: StreamHost): List<StreamApp>? = withContext(ioDispatcher) {
        client.apps(host, clientId())
    }

    /**
     * The identity THOR presents to hosts.
     *
     * Public because artwork is fetched by the image loader rather than through
     * this class, and every GameStream request has to name the client asking —
     * a host answers `/appasset` per client, exactly as it answers `/applist`.
     */
    suspend fun identity(): String = withContext(ioDispatcher) { clientId() }

    suspend fun cancelPairing(host: StreamHost) = withContext(ioDispatcher) {
        pairing.cancel(host, clientId())
    }

    /**
     * Asks a host to start streaming [app], and returns the session it agreed to.
     *
     * Slow by nature — the host has to launch the game before it answers — and
     * throws [LaunchFailure] with something worth showing when it will not.
     */
    suspend fun launch(
        host: StreamHost,
        app: StreamApp,
        onStage: (LaunchStage) -> Unit = {},
    ): LaunchedSession = withContext(ioDispatcher) {
        launcher.start(
            host = host,
            app = app,
            clientId = clientId(),
            quality = settings.stream.first().quality,
            onStage = onStage,
        )
    }

    /** Tells a host to end whatever it is streaming, whoever started it. */
    suspend fun stopStreaming(host: StreamHost): Boolean = withContext(ioDispatcher) {
        launcher.quit(host, clientId())
    }

    /**
     * Saves a host the user typed in.
     *
     * Keyed by address so adding the same machine twice edits it rather than
     * duplicating it — which is what happens otherwise when someone re-types an
     * address to correct a typo in the name.
     */
    suspend fun addHost(address: String, name: String = "") {
        val trimmed = address.trim()
        if (trimmed.isBlank()) return

        settings.updateStream { current ->
            val existing = current.hosts.firstOrNull { it.address == trimmed }
            val updated = (existing ?: StreamHost(address = trimmed)).copy(
                name = name.ifBlank { existing?.name.orEmpty() },
                discovered = false,
            )
            current.copy(
                hosts = current.hosts.filterNot { it.address == trimmed } + updated,
            )
        }
    }

    /**
     * Keeps a discovered host, so it survives the network going quiet.
     *
     * Discovery is not persistence: a PC that is asleep stops announcing, and a
     * host the user has actually set up should still be listed then — with
     * whatever THOR knows about it — rather than vanishing until it wakes.
     */
    suspend fun remember(host: StreamHost) = addHost(host.address, host.name)

    suspend fun removeHost(address: String) {
        settings.updateStream { current ->
            current.copy(hosts = current.hosts.filterNot { it.address == address })
        }
    }

    suspend fun setPaired(address: String, paired: Boolean) {
        settings.updateStream { current ->
            current.copy(
                hosts = current.hosts.map { host ->
                    if (host.address == address) host.copy(paired = paired) else host
                },
            )
        }
    }

    /**
     * The client id, generated on first use and kept thereafter.
     *
     * Pairing is bound to it, so a client that announced a new one each time
     * would be an unknown device on every launch and the user would be entering
     * PINs forever.
     */
    private suspend fun clientId(): String {
        val current = settings.stream.first()
        current.clientId.takeIf(String::isNotBlank)?.let { return it }

        val generated = StreamHostClient.newClientId()
        settings.updateStream { it.copy(clientId = generated) }
        return generated
    }
}
