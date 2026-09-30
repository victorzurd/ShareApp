package org.example.project

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts

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
actual fun SaveReceivedFile(file: ReceivedFile, folder: String?, onSaved: (SavedFileLocation) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(file.id) {
        runCatching {
            val resolver = context.contentResolver
            val safeName = java.io.File(file.fileName).name
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(safeName.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            if (folder == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val directory = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "ShareApp")
                if (!directory.exists() && !directory.mkdirs()) error("No se pudo crear la carpeta de descarga.")
                val target = java.io.File(directory, safeName)
                target.writeBytes(file.bytes)
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.shareapp.files",
                    target,
                )
                SavedFileLocation(contentUri.toString(), target.absolutePath)
            } else {
                val uri = if (folder != null) {
                    val treeUri = Uri.parse(folder)
                    val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
                    DocumentsContract.createDocument(resolver, parentUri, mimeType, safeName)
                        ?: error("No se pudo crear el archivo en la carpeta elegida.")
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/ShareApp")
                    }
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("No se pudo crear el archivo en Descargas.")
                }
                resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: error("No se pudo guardar el archivo.")
                SavedFileLocation(uri.toString(), if (folder == null) "Descargas/ShareApp/$safeName" else "Carpeta elegida/$safeName")
            }
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

@Composable
actual fun rememberShareAppSettings(): ShareAppSettingsController {
    val context = LocalContext.current
    return remember(context) {
        val preferences = context.getSharedPreferences("shareapp_settings", android.content.Context.MODE_PRIVATE)
        val initial = ShareAppSettings(
            deviceName = preferences.getString("device_name", "Android") ?: "Android",
            saveFolder = preferences.getString("save_folder", null),
            requireApproval = preferences.getBoolean("require_approval", true),
        )
        ShareAppSettingsController(initial) { value ->
            preferences.edit()
                .putString("device_name", value.deviceName)
                .putBoolean("require_approval", value.requireApproval)
                .apply { if (value.saveFolder == null) remove("save_folder") else putString("save_folder", value.saveFolder) }
                .apply()
        }
    }
}

@Composable
actual fun ShareAppDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) callback.value(null) else runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            uri.toString()
        }.onSuccess { callback.value(it) }.onFailure { callback.value(null) }
    }
    return remember(launcher) { { launcher.launch(null) } }
}

@Composable
actual fun rememberOpenSavedLocation(): (SavedFileLocation) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { location ->
            runCatching {
                val uri = Uri.parse(location.token)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}

