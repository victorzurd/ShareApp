package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import java.io.IOException
import java.util.UUID

actual fun createPeerDiscovery(platformContext: Any?): PeerDiscovery = JvmPeerDiscovery()

private class JvmPeerDiscovery : PeerDiscovery {
    private val localName = "ShareApp Desktop ${UUID.randomUUID().toString().take(6)}"
    private val mutableDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    private val mutableSearching = MutableStateFlow(false)
    private var jmdns: JmDNS? = null
    private var registeredService: ServiceInfo? = null
    private var serviceListener: ServiceListener? = null

    override val devices: StateFlow<List<PeerDevice>> = mutableDevices.asStateFlow()
    override val isSearching: StateFlow<Boolean> = mutableSearching.asStateFlow()

    @Synchronized
    override fun startAdvertising(port: Int) {
        if (registeredService != null) return
        try {
            val dns = getOrCreateJmDns()
            val info = ServiceInfo.create(SERVICE_TYPE, localName, port, "ShareApp P2P")
            dns.registerService(info)
            registeredService = info
        } catch (_: IOException) {
            // Se podrá volver a intentar cuando el usuario vuelva a iniciar ShareApp.
        }
    }

    @Synchronized
    override fun startSearching() {
        if (serviceListener != null) return
        try {
            val dns = getOrCreateJmDns()
            val listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    if (event.name != registeredService?.name) {
                        dns.requestServiceInfo(event.type, event.name, 1_000)
                    }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info ?: return
                    if (info.name == registeredService?.name) return
                    val host = info.inet4Addresses.firstOrNull()?.hostAddress
                        ?: info.inetAddresses.firstOrNull()?.hostAddress
                        ?: return
                    val peer = PeerDevice(
                        id = info.name,
                        name = info.name,
                        host = host,
                        port = info.port,
                    )
                    mutableDevices.update { current ->
                        (current.filterNot { it.id == peer.id } + peer).sortedBy { it.name }
                    }
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    mutableDevices.update { devices -> devices.filterNot { it.id == event.name } }
                }
            }
            serviceListener = listener
            dns.addServiceListener(SERVICE_TYPE, listener)
            mutableSearching.value = true
        } catch (_: IOException) {
            mutableSearching.value = false
            serviceListener = null
        }
    }

    @Synchronized
    override fun stopSearching() {
        val listener = serviceListener ?: return
        serviceListener = null
        jmdns?.removeServiceListener(SERVICE_TYPE, listener)
        mutableSearching.value = false
    }

    @Synchronized
    override fun stop() {
        stopSearching()
        val dns = jmdns
        if (dns != null) {
            registeredService?.let { runCatching { dns.unregisterService(it) } }
            runCatching { dns.close() }
        }
        registeredService = null
        jmdns = null
        mutableDevices.value = emptyList()
    }

    private fun getOrCreateJmDns(): JmDNS = jmdns ?: JmDNS.create().also { jmdns = it }

    private companion object {
        const val SERVICE_TYPE = "_shareapp._tcp.local."
    }
}
