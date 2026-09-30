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
