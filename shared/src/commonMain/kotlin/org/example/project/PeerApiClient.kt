package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/** Cliente HTTP compartido para comprobar y consultar dispositivos ShareApp. */
class PeerApiClient {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    suspend fun checkHealth(peer: PeerDevice): String {
        val host = if (peer.host.contains(':') && !peer.host.startsWith('[')) {
            "[${peer.host}]"
        } else {
            peer.host
        }
        val response = client.get("http://$host:${peer.port}/health") {
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
        }

        if (response.status != HttpStatusCode.OK) {
            error("El dispositivo respondió con HTTP ${response.status.value}.")
        }

        val body = response.bodyAsText().trim()
        if (body != HEALTH_RESPONSE) {
            error("El dispositivo no respondió como un servidor ShareApp.")
        }
        return body
    }

    fun close() = client.close()

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
        const val HEALTH_RESPONSE = "ShareApp disponible"
    }
}
