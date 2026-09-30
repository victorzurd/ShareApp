package org.example.project

import androidx.compose.runtime.Composable

@Composable
expect fun ShareAppFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit

@Composable
expect fun SaveReceivedFile(file: ReceivedFile, onSaved: (String) -> Unit, onError: (String) -> Unit)

@Composable
expect fun ShareAppClipboardReader(): () -> String?

@Composable
expect fun ShareAppClipboardWriter(): (String) -> Boolean
