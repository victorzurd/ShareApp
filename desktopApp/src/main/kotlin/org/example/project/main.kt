package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val localServer = ShareAppServer().also { it.start() }
    val peerDiscovery = createPeerDiscovery()
    peerDiscovery.startAdvertising(ShareAppServer.DEFAULT_PORT)

    application {
        Window(
            onCloseRequest = {
                localServer.stop()
                peerDiscovery.stop()
                exitApplication()
            },
            title = "ShareApp",
        ) {
            App(peerDiscovery)
        }
    }
}
