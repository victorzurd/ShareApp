package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val localServer = ShareAppServer().also { it.start() }

    application {
        Window(
            onCloseRequest = {
                localServer.stop()
                exitApplication()
            },
            title = "ShareApp",
        ) {
            App()
        }
    }
}
