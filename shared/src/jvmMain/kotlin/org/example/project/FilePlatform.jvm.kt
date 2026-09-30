package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.JFileChooser

@Composable
actual fun ShareAppFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val latestCallback = rememberUpdatedState(onPicked)
    return remember {
      {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            runCatching {
                val file = chooser.selectedFile
                require(file.length() <= MAX_SHARE_FILE_BYTES) { "El archivo supera el límite de 25 MB." }
                PickedFile(file.name, file.readBytes())
            }.onSuccess { latestCallback.value(it) }.onFailure { latestCallback.value(null) }
        } else latestCallback.value(null)
      }
    }
}

@Composable
actual fun SaveReceivedFile(file: ReceivedFile, folder: String?, onSaved: (SavedFileLocation) -> Unit, onError: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(file.id) {
        runCatching {
            val downloads = folder?.let(::File) ?: File(System.getProperty("user.home"), "Downloads/ShareApp")
            downloads.mkdirs()
            val destination = File(downloads, File(file.fileName).name)
            destination.writeBytes(file.bytes)
            SavedFileLocation(destination.absolutePath, destination.absolutePath)
        }.onSuccess(onSaved).onFailure { onError(it.message ?: "No se pudo guardar el archivo.") }
    }
}

@Composable
actual fun ShareAppClipboardReader(): () -> String? = remember {
    { runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull() }
}

@Composable
actual fun ShareAppClipboardWriter(): (String) -> Boolean = remember {
    { text ->
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)
    }
}

@Composable
actual fun rememberShareAppSettings(): ShareAppSettingsController = remember {
    val preferences = java.util.prefs.Preferences.userNodeForPackage(ShareAppSettingsController::class.java)
    val initial = ShareAppSettings(
        deviceName = preferences.get("device_name", "Ordenador"),
        saveFolder = preferences.get("save_folder", null),
        requireApproval = preferences.getBoolean("require_approval", true),
    )
    ShareAppSettingsController(initial) { value ->
        preferences.put("device_name", value.deviceName)
        preferences.putBoolean("require_approval", value.requireApproval)
        if (value.saveFolder == null) preferences.remove("save_folder") else preferences.put("save_folder", value.saveFolder)
    }
}

@Composable
actual fun ShareAppDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit {
    val latestCallback = rememberUpdatedState(onPicked)
    return remember {
        {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) latestCallback.value(chooser.selectedFile.absolutePath)
            else latestCallback.value(null)
        }
    }
}

@Composable
actual fun rememberOpenSavedLocation(): (SavedFileLocation) -> Boolean = remember {
    { location ->
        runCatching {
            val file = File(location.token)
            if (!file.exists()) false
            else if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                true
            } else if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                ProcessBuilder("open", "-R", file.absolutePath).start()
                true
            } else {
                java.awt.Desktop.getDesktop().open(file.parentFile)
                true
            }
        }.getOrDefault(false)
    }
}
