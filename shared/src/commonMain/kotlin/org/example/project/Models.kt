package org.example.project

/** Un ShareApp encontrado en la red local. */
data class PeerDevice(
    /** Identificador estable durante esta sesión de descubrimiento. */
    val id: String,
    val name: String,
    /** Dirección IP local o nombre de host resuelto por el descubrimiento. */
    val host: String,
    val port: Int,
)

/** Solicitud pendiente: el contenido no se envía hasta que el receptor acepte. */
data class IncomingTextRequest(
    val id: String,
    val senderName: String,
    val characterCount: Int,
)

data class ReceivedText(
    val id: String,
    val senderName: String,
    val text: String,
)

data class PickedFile(val name: String, val bytes: ByteArray)

data class IncomingFileRequest(val id: String, val senderName: String, val fileName: String, val sizeBytes: Long)

data class ReceivedFile(val id: String, val senderName: String, val fileName: String, val bytes: ByteArray)

const val MAX_SHARE_FILE_BYTES = 25 * 1024 * 1024

/** Una transferencia de texto o archivo entre este dispositivo y otro. */
data class Transfer(
    val id: String,
    val fileName: String,
    val peerName: String,
    val direction: TransferDirection,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.WAITING,
    val errorMessage: String? = null,
) {
    /** Progreso entre 0 y 1, listo para usar en un indicador de Compose. */
    val progress: Float
        get() = when {
            status == TransferStatus.COMPLETED -> 1f
            totalBytes <= 0L -> 0f
            else -> (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
}

enum class TransferDirection {
    SENDING,
    RECEIVING,
}

enum class TransferStatus {
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
}
