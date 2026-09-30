package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ShareAppSettingsController(
    initial: ShareAppSettings,
    private val persist: (ShareAppSettings) -> Unit,
) {
    var settings by mutableStateOf(initial)
        private set

    fun update(transform: (ShareAppSettings) -> ShareAppSettings) {
        transform(settings).also { updated ->
            settings = updated
            persist(updated)
        }
    }
}

@Composable
expect fun rememberShareAppSettings(): ShareAppSettingsController

@Composable
expect fun ShareAppDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit

@Composable
expect fun rememberOpenSavedLocation(): (SavedFileLocation) -> Boolean
