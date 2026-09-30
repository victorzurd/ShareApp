package org.example.project

import io.ktor.server.cio.CIO
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/** Servidor HTTP local compartido por Android y escritorio. */
class ShareAppServer(
    private val port: Int = DEFAULT_PORT,
) {
    private var stopEngine: (() -> Unit)? = null

    val isRunning: Boolean
        get() = stopEngine != null

    fun start() {
        if (stopEngine != null) return

        val engine = embeddedServer(
            factory = CIO,
            host = "0.0.0.0",
            port = port,
        ) {
            routing {
                get("/health") {
                    call.respondText("ShareApp disponible")
                }
            }
        }
        engine.start(wait = false)
        stopEngine = { engine.stop(gracePeriodMillis = 500, timeoutMillis = 1_000) }
    }

    fun stop() {
        stopEngine?.invoke()
        stopEngine = null
    }

    companion object {
        const val DEFAULT_PORT = 47852
    }
}
