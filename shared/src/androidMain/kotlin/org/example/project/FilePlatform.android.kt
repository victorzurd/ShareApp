package org.example.project

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ShareAppFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestCallback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) latestCallback.value(null) else runCatching {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "archivo"
            } ?: "archivo"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo.")
            require(bytes.size <= MAX_SHARE_FILE_BYTES) { "El archivo supera el límite de 25 MB." }
            PickedFile(name, bytes)
        }.onSuccess { latestCallback.value(it) }.onFailure { latestCallback.value(null) }
    }
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

@Composable
actual fun SaveReceivedFile(file: ReceivedFile, onSaved: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(file.id) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.RELATIVE_PATH, "Download/ShareApp")
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI
                else MediaStore.Files.getContentUri("external")
            val uri = resolver.insert(collection, values) ?: error("No se pudo crear el archivo en Descargas.")
            resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: error("No se pudo guardar el archivo.")
            "Descargas/ShareApp/${file.fileName}"
        }.onSuccess(onSaved).onFailure { onError(it.message ?: "No se pudo guardar el archivo.") }
    }
}

@Composable
actual fun ShareAppClipboardReader(): () -> String? {
    val context = LocalContext.current
    return remember(context) {
        {
            runCatching {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip ?: return@runCatching null
                val item = clip.getItemAt(0)
                item.text?.toString()
            }.getOrNull()
        }
    }
}

@Composable
actual fun ShareAppClipboardWriter(): (String) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            runCatching {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ShareApp", text))
                true
            }.getOrDefault(false)
        }
    }
}

