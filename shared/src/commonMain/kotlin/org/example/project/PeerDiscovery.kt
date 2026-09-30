package org.example.project

import kotlinx.coroutines.flow.StateFlow

/** Descubrimiento y anuncio de ShareApp, implementados por cada plataforma. */
interface PeerDiscovery {
    val devices: StateFlow<List<PeerDevice>>
    val isSearching: StateFlow<Boolean>

    fun startAdvertising(port: Int)
    fun updateDeviceName(name: String)
    fun startSearching()
    fun stopSearching()
    fun stop()
}

/** Android recibe Context; escritorio no necesita contexto de plataforma. */
expect fun createPeerDiscovery(platformContext: Any? = null): PeerDiscovery
