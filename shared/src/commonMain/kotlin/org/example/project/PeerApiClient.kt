package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Cliente HTTP compartido para comprobar y consultar dispositivos ShareApp. */
class PeerApiClient {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = FILE_TRANSFER_TIMEOUT_MILLIS
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

    suspend fun sendText(peer: PeerDevice, senderName: String, text: String): String {
        require(text.length <= MAX_TEXT_CHARACTERS) { "El texto supera el límite de $MAX_TEXT_CHARACTERS caracteres." }
        val requestId = newRequestId()
        val baseUrl = peerBaseUrl(peer)
        val requestResponse = client.post("$baseUrl/transfer/text/request") {
            url { parameters.append("id", requestId); parameters.append("sender", senderName); parameters.append("characters", text.length.toString()) }
        }
        if (requestResponse.status != HttpStatusCode.Accepted) error("No se pudo solicitar permiso al dispositivo.")

        waitForAcceptance(baseUrl, requestId)
        val response = client.post("$baseUrl/transfer/text/content") {
            url { parameters.append("id", requestId) }
            contentType(ContentType.Text.Plain)
            setBody(text)
        }
        if (response.status != HttpStatusCode.Created) error("No se pudo entregar el texto.")
        return "Texto enviado correctamente."
    }

    suspend fun sendFile(peer: PeerDevice, senderName: String, file: PickedFile): String {
        require(file.bytes.size <= MAX_SHARE_FILE_BYTES) { "El archivo supera el límite de 25 MB." }
        val requestId = newRequestId()
        val baseUrl = peerBaseUrl(peer)
        val requestResponse = client.post("$baseUrl/transfer/file/request") {
            url {
                parameters.append("id", requestId)
                parameters.append("sender", senderName)
                parameters.append("name", file.name)
                parameters.append("size", file.bytes.size.toString())
            }
        }
        if (requestResponse.status != HttpStatusCode.Accepted) error("No se pudo solicitar permiso al dispositivo.")
        waitForAcceptance(baseUrl, requestId)
        val response = client.post("$baseUrl/transfer/file/content") {
            url { parameters.append("id", requestId) }
            contentType(ContentType.Application.OctetStream)
            setBody(file.bytes)
            timeout { requestTimeoutMillis = FILE_TRANSFER_TIMEOUT_MILLIS }
        }
        if (response.status != HttpStatusCode.Created) {
            val detail = response.bodyAsText().trim().take(300)
            error("El receptor respondió HTTP ${response.status.value}${if (detail.isNotEmpty()) ": $detail" else "."}")
        }
        return "${file.name} enviado correctamente."
    }

    private suspend fun waitForAcceptance(baseUrl: String, requestId: String) {
        repeat(120) {
            delay(500)
            val statusResponse = client.get("$baseUrl/transfer/text/status") {
                url { parameters.append("id", requestId) }
            }
            val state = statusResponse.bodyAsText().trim()
            when (state) {
                "ACCEPTED" -> {
                    return
                }
                "REJECTED" -> error("El receptor rechazó la solicitud.")
                "NOT_FOUND" -> error("La solicitud ya no está disponible.")
            }
        }
        error("No hubo respuesta en 60 segundos. Vuelve a intentarlo.")
    }

    private fun newRequestId() = "${Random.nextLong().toULong().toString(16)}${Random.nextLong().toULong().toString(16)}"

    private fun peerBaseUrl(peer: PeerDevice): String {
        val host = if (peer.host.contains(':') && !peer.host.startsWith('[')) "[${peer.host}]" else peer.host
        return "http://$host:${peer.port}"
    }

    fun close() = client.close()

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
        const val HEALTH_RESPONSE = "ShareApp disponible"
        const val MAX_TEXT_CHARACTERS = 100_000
        const val FILE_TRANSFER_TIMEOUT_MILLIS = 120_000L
    }
}
