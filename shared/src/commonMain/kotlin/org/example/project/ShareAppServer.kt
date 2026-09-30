package org.example.project

import io.ktor.server.cio.CIO
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.request.receiveText
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Servidor HTTP local compartido por Android y escritorio. */
class ShareAppServer(
    private val port: Int = DEFAULT_PORT,
) {
    private var stopEngine: (() -> Unit)? = null
    private val _incomingRequests = MutableStateFlow<List<IncomingTextRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingTextRequest>> = _incomingRequests.asStateFlow()
    private val _receivedTexts = MutableStateFlow<List<ReceivedText>>(emptyList())
    val receivedTexts: StateFlow<List<ReceivedText>> = _receivedTexts.asStateFlow()
    private val _incomingFileRequests = MutableStateFlow<List<IncomingFileRequest>>(emptyList())
    val incomingFileRequests: StateFlow<List<IncomingFileRequest>> = _incomingFileRequests.asStateFlow()
    private val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFile>> = _receivedFiles.asStateFlow()
    private val decisions = mutableMapOf<String, String>()
    private val requestSenders = mutableMapOf<String, String>()
    private val requestFileNames = mutableMapOf<String, String>()
    private val fileSizes = mutableMapOf<String, Long>()

    val isRunning: Boolean
        get() = stopEngine != null

    fun decideTextRequest(id: String, accepted: Boolean) {
        synchronized(decisions) {
            if (decisions[id] != "WAITING") return
            decisions[id] = if (accepted) "ACCEPTED" else "REJECTED"
        }
        _incomingRequests.update { requests -> requests.filterNot { it.id == id } }
    }

    fun decideFileRequest(id: String, accepted: Boolean) {
        synchronized(decisions) {
            if (decisions[id] != "WAITING") return
            decisions[id] = if (accepted) "ACCEPTED" else "REJECTED"
        }
        _incomingFileRequests.update { requests -> requests.filterNot { it.id == id } }
    }

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
                post("/transfer/text/request") {
                    val id = call.request.queryParameters["id"]
                    val sender = call.request.queryParameters["sender"]
                    val count = call.request.queryParameters["characters"]?.toIntOrNull()
                    if (id.isNullOrBlank() || sender.isNullOrBlank() || count == null || count !in 0..MAX_TEXT_CHARACTERS) {
                        call.respondText("Solicitud no válida", status = HttpStatusCode.BadRequest)
                    } else {
                        synchronized(decisions) {
                            decisions[id] = "WAITING"
                            requestSenders[id] = sender
                        }
                        _incomingRequests.update { current -> current.filterNot { it.id == id } + IncomingTextRequest(id, sender, count) }
                        call.respondText("WAITING", status = HttpStatusCode.Accepted)
                    }
                }
                post("/transfer/text/decision") {
                    val id = call.request.queryParameters["id"]
                    val accepted = call.request.queryParameters["accepted"]?.toBooleanStrictOrNull()
                    val isPending = id != null && synchronized(decisions) { decisions[id] == "WAITING" }
                    if (id == null || accepted == null || !isPending) {
                        call.respondText("Solicitud no encontrada", status = HttpStatusCode.NotFound)
                    } else {
                        decideTextRequest(id, accepted)
                        val state = if (accepted) "ACCEPTED" else "REJECTED"
                        call.respondText(state)
                    }
                }
                get("/transfer/text/status") {
                    val id = call.request.queryParameters["id"]
                    val state = id?.let { synchronized(decisions) { decisions[it] } }
                    if (state == null) call.respondText("NOT_FOUND", status = HttpStatusCode.NotFound)
                    else call.respondText(state)
                }
                post("/transfer/text/content") {
                    val id = call.request.queryParameters["id"]
                    val state = id?.let { synchronized(decisions) { decisions[it] } }
                    if (id == null || state != "ACCEPTED") {
                        call.respondText("El receptor no ha aceptado", status = HttpStatusCode.Forbidden)
                    } else {
                        val text = call.receiveText()
                        if (text.length > MAX_TEXT_CHARACTERS) {
                            call.respondText("Texto demasiado largo", status = HttpStatusCode.PayloadTooLarge)
                        } else {
                            val sender = synchronized(decisions) { requestSenders[id] ?: "Dispositivo ShareApp" }
                            _receivedTexts.update { current -> listOf(ReceivedText(id, sender, text)) + current }
                            synchronized(decisions) { decisions[id] = "COMPLETED" }
                            call.respondText("COMPLETED", status = HttpStatusCode.Created)
                        }
                    }
                }
                post("/transfer/file/request") {
                    val id = call.request.queryParameters["id"]
                    val sender = call.request.queryParameters["sender"]
                    val name = call.request.queryParameters["name"]?.takeIf { it.isNotBlank() }?.let { it.substringAfterLast('/').substringAfterLast('\\') }
                    val size = call.request.queryParameters["size"]?.toLongOrNull()
                    if (id.isNullOrBlank() || sender.isNullOrBlank() || name == null || size == null || size !in 0..MAX_SHARE_FILE_BYTES.toLong()) {
                        call.respondText("Solicitud no válida", status = HttpStatusCode.BadRequest)
                    } else {
                        synchronized(decisions) {
                            decisions[id] = "WAITING"
                            requestSenders[id] = sender
                            requestFileNames[id] = name
                            fileSizes[id] = size
                        }
                        _incomingFileRequests.update { current -> current.filterNot { it.id == id } + IncomingFileRequest(id, sender, name, size) }
                        call.respondText("WAITING", status = HttpStatusCode.Accepted)
                    }
                }
                post("/transfer/file/decision") {
                    val id = call.request.queryParameters["id"]
                    val accepted = call.request.queryParameters["accepted"]?.toBooleanStrictOrNull()
                    val pending = id != null && synchronized(decisions) { decisions[id] == "WAITING" && requestFileNames.containsKey(id) }
                    if (id == null || accepted == null || !pending) call.respondText("Solicitud no encontrada", status = HttpStatusCode.NotFound)
                    else {
                        decideFileRequest(id, accepted)
                        call.respondText(if (accepted) "ACCEPTED" else "REJECTED")
                    }
                }
                post("/transfer/file/content") {
                    try {
                        val id = call.request.queryParameters["id"]
                        val state = id?.let { synchronized(decisions) { decisions[it] } }
                        val expectedSize = id?.let { key -> synchronized(decisions) { fileSizes[key] } }
                        val announcedSize = call.request.headers["Content-Length"]?.toLongOrNull()
                        when {
                            id == null || state != "ACCEPTED" ->
                                call.respondText("El receptor no ha aceptado", status = HttpStatusCode.Forbidden)
                            announcedSize != null && announcedSize > MAX_SHARE_FILE_BYTES.toLong() ->
                                call.respondText("Archivo demasiado grande: Content-Length=$announcedSize", status = HttpStatusCode.PayloadTooLarge)
                            else -> {
                                val bytes = call.receive<ByteArray>()
                                when {
                                    bytes.size > MAX_SHARE_FILE_BYTES ->
                                        call.respondText("Archivo demasiado grande: llegaron ${bytes.size} bytes", status = HttpStatusCode.PayloadTooLarge)
                                    expectedSize != null && bytes.size.toLong() != expectedSize ->
                                        call.respondText("Tamaño incompleto: se esperaban $expectedSize bytes y llegaron ${bytes.size}.", status = HttpStatusCode.BadRequest)
                                    else -> {
                                        val (sender, name) = synchronized(decisions) {
                                            (requestSenders[id] ?: "Dispositivo ShareApp") to (requestFileNames[id] ?: "archivo")
                                        }
                                        _receivedFiles.update { current -> listOf(ReceivedFile(id, sender, name, bytes)) + current }
                                        synchronized(decisions) { decisions[id] = "COMPLETED" }
                                        call.respondText("COMPLETED", status = HttpStatusCode.Created)
                                    }
                                }
                            }
                        }
                    } catch (failure: Exception) {
                        call.respondText(
                            "Error procesando archivo (${failure::class.simpleName}): ${failure.message ?: "sin detalle"}",
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
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
        const val MAX_TEXT_CHARACTERS = 100_000
        const val MAX_SHARE_FILE_BYTES = org.example.project.MAX_SHARE_FILE_BYTES
    }
}
