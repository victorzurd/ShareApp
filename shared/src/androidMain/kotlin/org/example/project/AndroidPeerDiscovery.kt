package org.example.project

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

actual fun createPeerDiscovery(platformContext: Any?): PeerDiscovery {
    val context = platformContext as? Context
        ?: error("AndroidPeerDiscovery necesita un Context de Android")
    return AndroidPeerDiscovery(context.applicationContext)
}

private class AndroidPeerDiscovery(context: Context) : PeerDiscovery {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val localName = "ShareApp Android ${UUID.randomUUID().toString().take(6)}"
    private val mutableDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    private val mutableSearching = MutableStateFlow(false)
    private var registeredName: String? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override val devices: StateFlow<List<PeerDevice>> = mutableDevices.asStateFlow()
    override val isSearching: StateFlow<Boolean> = mutableSearching.asStateFlow()

    override fun startAdvertising(port: Int) {
        if (registrationListener != null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredName = info.serviceName
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: SecurityException) {
            registrationListener = null
        }
    }

    @Suppress("DEPRECATION")
    override fun startSearching() {
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                mutableSearching.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceName == registeredName || service.serviceName == localName) return
                try {
                    manager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val peer = PeerDevice(
                                id = info.serviceName,
                                name = info.serviceName,
                                host = host,
                                port = info.port,
                            )
                            mutableDevices.update { current ->
                                (current.filterNot { it.id == peer.id } + peer).sortedBy { it.name }
                            }
                        }
                    })
                } catch (_: SecurityException) {
                    mutableSearching.value = false
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                mutableDevices.update { devices -> devices.filterNot { it.id == service.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                mutableSearching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mutableSearching.value = false
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                mutableSearching.value = false
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            mutableSearching.value = true
        } catch (_: SecurityException) {
            mutableSearching.value = false
            discoveryListener = null
        }
    }

    override fun stopSearching() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            mutableSearching.value = false
        } catch (_: SecurityException) {
            mutableSearching.value = false
        }
    }

    override fun stop() {
        stopSearching()
        registrationListener?.let { listener ->
            try {
                manager.unregisterService(listener)
            } catch (_: IllegalArgumentException) {
                // La registración pudo fallar o aún no haberse completado.
            } catch (_: SecurityException) {
                // Android puede revocar el permiso mientras la app está abierta.
            }
        }
        registrationListener = null
        registeredName = null
        mutableDevices.value = emptyList()
    }

    private companion object {
        const val SERVICE_TYPE = "_shareapp._tcp."
    }
}
