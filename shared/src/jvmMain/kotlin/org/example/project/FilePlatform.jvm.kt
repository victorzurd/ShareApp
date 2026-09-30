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
actual fun SaveReceivedFile(file: ReceivedFile, onSaved: (String) -> Unit, onError: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(file.id) {
        runCatching {
            val downloads = File(System.getProperty("user.home"), "Downloads/ShareApp")
            downloads.mkdirs()
            val destination = File(downloads, File(file.fileName).name)
            destination.writeBytes(file.bytes)
            destination.absolutePath
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
