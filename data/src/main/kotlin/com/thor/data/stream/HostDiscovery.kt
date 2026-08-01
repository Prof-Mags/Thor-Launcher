package com.thor.data.stream

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.thor.core.common.log.ThorLog
import com.thor.core.model.StreamHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds GameStream hosts announcing themselves on the network.
 *
 * Sunshine publishes `_nvstream._tcp`, the same service type NVIDIA's own
 * GameStream used, so a machine running it is already telling the network it is
 * there. Asking is better than making the user find their PC's address: the
 * common case needs no configuration at all, and an address typed in by hand is
 * one that breaks the next time DHCP moves it.
 *
 * Manual entry is still offered — a host on another subnet, or a network that
 * drops multicast, will never be announced — but it is the fallback rather than
 * the route.
 */
@Singleton
class HostDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /**
     * Hosts seen so far, emitted as they are resolved.
     *
     * A flow rather than a one-shot scan because discovery is not an event: a PC
     * that is switched on while the user is looking at the screen should appear
     * on it. Collection stops the browse, so nothing is left listening when the
     * section is closed.
     *
     * Emits the accumulated set each time, rather than one host per emission, so
     * the screen never has to remember what it has already been told.
     */
    fun hosts(): Flow<List<StreamHost>> = callbackFlow {
        val manager = nsdManager ?: run {
            ThorLog.w(TAG, "No NSD service on this device; discovery unavailable")
            send(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val found = linkedMapOf<String, StreamHost>()

        /*
         * Resolution is serialised through one in-flight request.
         *
         * `NsdManager.resolveService` fails with `FAILURE_ALREADY_ACTIVE` if a
         * second resolve starts before the first finishes, and a network with
         * several hosts announces them within milliseconds of each other — so a
         * naive resolve-on-found loses every host after the first.
         */
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true

            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        ThorLog.w(TAG, "Could not resolve ${info.serviceName} ($errorCode)")
                        resolving = false
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val address = info.hostAddress()
                        if (address != null) {
                            found[address] = StreamHost(
                                name = info.serviceName.orEmpty(),
                                address = address,
                                discovered = true,
                            )
                            trySend(found.values.toList())
                        }
                        resolving = false
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) =
                ThorLog.d(TAG) { "Looking for $serviceType" }

            override fun onServiceFound(info: NsdServiceInfo) {
                /*
                 * Matched loosely, on purpose.
                 *
                 * This compared the reported type against the requested one for
                 * equality, and devices do not agree on how to spell it: the
                 * same service arrives as `_nvstream._tcp`, `_nvstream._tcp.`
                 * and `_nvstream._tcp.local.` depending on the ROM. Any
                 * mismatch dropped every host silently — discovery ran, found
                 * the PC, and threw it away before resolving it.
                 *
                 * There is nothing to guard against anyway: this listener is
                 * registered for one service type, so anything arriving here was
                 * asked for.
                 */
                if (!info.serviceType.orEmpty().contains(SERVICE_NAME, ignoreCase = true)) {
                    ThorLog.d(TAG) { "Ignoring ${info.serviceType}" }
                    return
                }
                pending.addLast(info)
                resolveNext()
            }

            /*
             * A host going away is deliberately *not* removed.
             *
             * mDNS goodbyes are unreliable and a sleeping PC simply stops
             * answering, so treating silence as removal makes hosts flicker off
             * the screen while the user is reading them. Whether a host is
             * usable is settled by asking it, not by whether it is still
             * shouting.
             */
            override fun onServiceLost(info: NsdServiceInfo) =
                ThorLog.d(TAG) { "Lost ${info.serviceName}" }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                ThorLog.w(TAG, "Discovery failed to start ($errorCode)")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        /*
         * Held for as long as discovery runs, and released with it.
         *
         * mDNS is multicast, and Wi-Fi hardware filters multicast out of the
         * host's receive path to save power unless something asks it not to.
         * `NsdManager` does not take this lock for you: without it discovery
         * starts, reports no error, and finds nothing at all — on a network
         * where the PC is announcing itself perfectly and any other device can
         * see it.
         *
         * It costs battery, which is exactly why it is scoped to the flow rather
         * than to the process: it is held while the user is looking at the
         * Stream section and at no other time.
         */
        val multicast = runCatching {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock(MULTICAST_TAG)?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()

        if (multicast == null) ThorLog.w(TAG, "No multicast lock; discovery may find nothing")

        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            ThorLog.w(TAG, "Could not start discovery", error)
            close()
        }

        awaitClose {
            runCatching { manager.stopServiceDiscovery(listener) }
            runCatching { multicast?.takeIf { it.isHeld }?.release() }
        }
    }

    private companion object {
        const val TAG = "Stream"

        /**
         * What GameStream hosts publish.
         *
         * Sunshine kept NVIDIA's service type rather than inventing one, which
         * is why a client written for GameStream finds it without knowing it
         * exists.
         */
        const val SERVICE_TYPE = "_nvstream._tcp"

        /** Matched loosely against whatever spelling the ROM reports. */
        const val SERVICE_NAME = "nvstream"

        const val MULTICAST_TAG = "thor-stream-discovery"

        /**
         * The address, across the API change that deprecated the old accessor.
         *
         * `host` is deprecated from API 34 in favour of `hostAddresses`, and the
         * launcher runs on 29 upward — so both are needed, and the new one is
         * preferred where it exists.
         */
        fun NsdServiceInfo.hostAddress(): String? = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hostAddresses.firstOrNull()?.hostAddress
            } else {
                @Suppress("DEPRECATION")
                (host as? InetAddress)?.hostAddress
            }
        }.getOrNull()
    }
}
