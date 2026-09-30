package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Ink = androidx.compose.ui.graphics.Color(0xFF17211F)
private val Muted = androidx.compose.ui.graphics.Color(0xFF78827E)
private val Canvas = androidx.compose.ui.graphics.Color(0xFFF5F7F5)
private val Green = androidx.compose.ui.graphics.Color(0xFF187B63)
private val PaleGreen = androidx.compose.ui.graphics.Color(0xFFE6F3EE)
private val Line = androidx.compose.ui.graphics.Color(0xFFE8ECE9)

private data class PeerCheckState(
    val peer: PeerDevice,
    val isChecking: Boolean,
    val message: String? = null,
    val error: String? = null,
)

@Composable
fun App(peerDiscovery: PeerDiscovery, localServer: ShareAppServer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val devices by peerDiscovery.devices.collectAsState()
    val isSearching by peerDiscovery.isSearching.collectAsState()
    val incomingRequests by localServer.incomingRequests.collectAsState()
    val receivedTexts by localServer.receivedTexts.collectAsState()
    val incomingFileRequests by localServer.incomingFileRequests.collectAsState()
    val receivedFiles by localServer.receivedFiles.collectAsState()
    var peerCheck by remember { mutableStateOf<PeerCheckState?>(null) }
    var textPeer by remember { mutableStateOf<PeerDevice?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var isSendingText by remember { mutableStateOf(false) }
    var transferNotice by remember { mutableStateOf<String?>(null) }
    var shownTextId by remember { mutableStateOf<String?>(null) }
    var fileSendPeer by remember { mutableStateOf<PeerDevice?>(null) }
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var fileToSave by remember { mutableStateOf<ReceivedFile?>(null) }
    var shownFileId by remember { mutableStateOf<String?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val peerApi = remember { PeerApiClient() }
    val launchFilePicker = ShareAppFilePicker { pickedFile = it }
    val readClipboardText = ShareAppClipboardReader()
    val writeClipboardText = ShareAppClipboardWriter()
    val settingsController = rememberShareAppSettings()
    val settings = settingsController.settings
    val chooseSaveFolder = ShareAppDirectoryPicker { folder ->
        if (folder != null) settingsController.update { it.copy(saveFolder = folder) }
    }
    val openSavedLocation = rememberOpenSavedLocation()
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var deviceNameDraft by remember { mutableStateOf(settings.deviceName) }
    var showAutoAcceptWarning by remember { mutableStateOf(false) }
    var selectedReceivedFile by remember { mutableStateOf<ReceivedFile?>(null) }
    var selectedReceivedText by remember { mutableStateOf<ReceivedText?>(null) }
    var savedFileLocations by remember { mutableStateOf<Map<String, SavedFileLocation>>(emptyMap()) }

    LaunchedEffect(settings.deviceName) { peerDiscovery.updateDeviceName(settings.deviceName) }
    LaunchedEffect(settings.requireApproval) { localServer.setRequireApproval(settings.requireApproval) }

    LaunchedEffect(receivedTexts.firstOrNull()?.id) {
        receivedTexts.firstOrNull()?.let { received ->
            if (!writeClipboardText(received.text)) {
                transferNotice = "El texto llegó, pero no se pudo copiar al portapapeles."
            }
        }
    }

    LaunchedEffect(pickedFile, fileSendPeer) {
        val file = pickedFile
        val peer = fileSendPeer
        if (file != null && peer != null) {
            try {
                transferNotice = peerApi.sendFile(peer, "Este dispositivo", file)
            } catch (error: Exception) {
                transferNotice = error.message ?: "No se pudo enviar el archivo."
            } finally {
                pickedFile = null
                fileSendPeer = null
            }
        } else if (file == null && peer != null) {
            // El selector puede cancelarse; no se inicia ninguna transferencia.
        }
    }

    DisposableEffect(peerApi) {
        onDispose { peerApi.close() }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = Canvas,
            surface = androidx.compose.ui.graphics.Color.White,
            onSurface = Ink,
            onSurfaceVariant = Muted,
            outlineVariant = Line
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Green),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ShareApp", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Compartir en tu red local", color = Muted, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(7.dp))
                    Text(if (localServer.isRunning) "En línea" else "Desconectado", color = if (localServer.isRunning) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                    when (selectedTab) {
                        0 -> DevicesScreen(
                            devices = devices,
                            isSearching = isSearching,
                            onDeviceClick = { peer ->
                                checkJob?.cancel()
                                peerCheck = PeerCheckState(peer = peer, isChecking = true)
                                checkJob = coroutineScope.launch {
                                    try {
                                        val message = peerApi.checkHealth(peer)
                                        peerCheck = PeerCheckState(peer, isChecking = false, message = message)
                                    } catch (_: Exception) {
                                        peerCheck = PeerCheckState(
                                            peer = peer,
                                            isChecking = false,
                                            error = "No se pudo conectar. Comprueba que ShareApp sigue abierto y está en la misma Wi‑Fi."
                                        )
                                    }
                                }
                            },
                            onSearch = {
                                if (isSearching) peerDiscovery.stopSearching() else peerDiscovery.startSearching()
                            }
                        )
                        1 -> TransfersScreen(
                            receivedTexts = receivedTexts,
                            receivedFiles = receivedFiles,
                            onFileClick = { file ->
                                val location = savedFileLocations[file.id]
                                if (location == null) selectedReceivedFile = file
                                else if (!openSavedLocation(location)) transferNotice = "No se pudo abrir el archivo. Comprueba que sigue disponible."
                            },
                            onTextClick = { selectedReceivedText = it },
                        )
                        else -> SettingsScreen(
                            settings = settings,
                            onDeviceNameClick = { deviceNameDraft = settings.deviceName; showDeviceNameDialog = true },
                            onChooseFolder = chooseSaveFolder,
                            onResetFolder = { settingsController.update { it.copy(saveFolder = null) } },
                            onRequireApprovalChange = { required ->
                                if (required) settingsController.update { it.copy(requireApproval = true) }
                                else showAutoAcceptWarning = true
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Dispositivos", "Transferencias", "Ajustes").forEachIndexed { index, label ->
                        val active = selectedTab == index
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .clickable { selectedTab = index }
                                .background(if (active) PaleGreen else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(listOf("⌂", "⇄", "⚙")[index], color = if (active) Green else Muted, fontSize = 18.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(label, color = if (active) Green else Muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
          }
        }

        peerCheck?.let { result ->
            AlertDialog(
                onDismissRequest = {
                    checkJob?.cancel()
                    peerCheck = null
                },
                title = { Text(if (result.isChecking) "Conectando…" else result.peer.name) },
                text = {
                    Text(
                        when {
                            result.isChecking -> "Comprobando la conexión con ${result.peer.name}."
                            result.message != null -> "Conexión correcta. ${result.message}"
                            else -> result.error.orEmpty()
                        }
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (!result.isChecking && result.message != null) {
                            TextButton(onClick = { textPeer = result.peer; textDraft = ""; peerCheck = null }) { Text("Enviar texto") }
                            TextButton(onClick = {
                                val clipboardText = readClipboardText()
                                peerCheck = null
                                if (clipboardText.isNullOrEmpty()) {
                                    transferNotice = "El portapapeles no contiene texto. Copia un texto e inténtalo de nuevo."
                                } else {
                                    coroutineScope.launch {
                                        try {
                                            transferNotice = peerApi.sendText(result.peer, "Portapapeles · este dispositivo", clipboardText)
                                        } catch (error: Exception) {
                                            transferNotice = error.message ?: "No se pudo enviar el portapapeles."
                                        }
                                    }
                                }
                            }) { Text("Enviar portapapeles") }
                            TextButton(onClick = { fileSendPeer = result.peer; peerCheck = null; launchFilePicker() }) { Text("Enviar archivo") }
                        }
                        TextButton(onClick = { checkJob?.cancel(); peerCheck = null }) {
                            Text(if (result.isChecking) "Cancelar" else "Cerrar")
                        }
                    }
                }
            )
        }

        textPeer?.let { peer ->
            AlertDialog(
                onDismissRequest = { if (!isSendingText) textPeer = null },
                title = { Text("Enviar texto a ${peer.name}") },
                text = {
                    Column {
                        Text("Primero se pedirá permiso. El texto se enviará solo si el otro dispositivo acepta.")
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = textDraft,
                            onValueChange = { if (it.length <= ShareAppServer.MAX_TEXT_CHARACTERS) textDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text("Escribe o pega un texto…") },
                            supportingText = { Text("${textDraft.length}/${ShareAppServer.MAX_TEXT_CHARACTERS}") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = textDraft.isNotBlank() && !isSendingText, onClick = {
                        isSendingText = true
                        coroutineScope.launch {
                            try {
                                transferNotice = peerApi.sendText(peer, "Este dispositivo", textDraft)
                            } catch (error: Exception) {
                                transferNotice = error.message ?: "No se pudo enviar el texto."
                            } finally {
                                isSendingText = false
                                textPeer = null
                            }
                        }
                    }) { Text(if (isSendingText) "Esperando aceptación…" else "Solicitar envío") }
                },
                dismissButton = { TextButton(enabled = !isSendingText, onClick = { textPeer = null }) { Text("Cancelar") } }
            )
        }

        incomingRequests.firstOrNull()?.let { request ->
            AlertDialog(
                onDismissRequest = { localServer.decideTextRequest(request.id, false) },
                title = { Text("Solicitud de texto") },
                text = { Text("${request.senderName} quiere enviarte un texto de ${request.characterCount} caracteres. ¿Aceptas recibirlo?") },
                confirmButton = { TextButton(onClick = { localServer.decideTextRequest(request.id, true) }) { Text("Aceptar") } },
                dismissButton = { TextButton(onClick = { localServer.decideTextRequest(request.id, false) }) { Text("Rechazar") } }
            )
        }

        incomingFileRequests.firstOrNull()?.let { request ->
            AlertDialog(
                onDismissRequest = { localServer.decideFileRequest(request.id, false) },
                title = { Text("Solicitud de archivo") },
                text = { Text("${request.senderName} quiere enviarte ${request.fileName} (${formatFileSize(request.sizeBytes)}). ¿Aceptas recibirlo?") },
                confirmButton = { TextButton(onClick = { localServer.decideFileRequest(request.id, true) }) { Text("Aceptar") } },
                dismissButton = { TextButton(onClick = { localServer.decideFileRequest(request.id, false) }) { Text("Rechazar") } }
            )
        }

        receivedFiles.firstOrNull()?.takeIf { it.id != shownFileId }?.let { file ->
            AlertDialog(
                onDismissRequest = { shownFileId = file.id },
                title = { Text("Archivo recibido") },
                text = { Text("${file.fileName} de ${file.senderName} se guardará en ${if (settings.saveFolder == null) "Descargas/ShareApp" else "la carpeta seleccionada"}.") },
                confirmButton = { TextButton(onClick = { shownFileId = file.id; fileToSave = file; selectedTab = 1 }) { Text("Guardar archivo") } },
                dismissButton = { TextButton(onClick = { shownFileId = file.id }) { Text("Después") } }
            )
        }

        fileToSave?.let { file ->
            SaveReceivedFile(file, settings.saveFolder, onSaved = { location ->
                savedFileLocations = savedFileLocations + (file.id to location)
                transferNotice = "Archivo guardado en ${location.displayPath}"
                fileToSave = null
            }, onError = { message -> transferNotice = message; fileToSave = null })
        }

        selectedReceivedFile?.let { file ->
            val location = savedFileLocations[file.id]
            AlertDialog(
                onDismissRequest = { selectedReceivedFile = null },
                title = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Recibido de ${file.senderName}", color = Muted)
                        Text(formatFileSize(file.bytes.size.toLong()), color = Muted)
                        Text(location?.displayPath ?: "Aún no se ha guardado en el dispositivo.", color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (location == null) fileToSave = file
                        else if (!openSavedLocation(location)) transferNotice = "No se pudo abrir el archivo. Comprueba que sigue disponible."
                        else selectedReceivedFile = null
                    }) { Text(if (location == null) "Guardar archivo" else "Abrir ubicación") }
                },
                dismissButton = { TextButton(onClick = { selectedReceivedFile = null }) { Text("Cerrar") } },
            )
        }

        selectedReceivedText?.let { text ->
            AlertDialog(
                onDismissRequest = { selectedReceivedText = null },
                title = { Text("Texto recibido") },
                text = { Column { Text("De ${text.senderName}", color = Muted); Spacer(Modifier.height(8.dp)); Text(text.text) } },
                confirmButton = { TextButton(onClick = { writeClipboardText(text.text); selectedReceivedText = null }) { Text("Copiar otra vez") } },
                dismissButton = { TextButton(onClick = { selectedReceivedText = null }) { Text("Cerrar") } },
            )
        }

        if (showDeviceNameDialog) {
            AlertDialog(
                onDismissRequest = { showDeviceNameDialog = false },
                title = { Text("Nombre del dispositivo") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = deviceNameDraft,
                        onValueChange = { deviceNameDraft = it.take(38) },
                        singleLine = true,
                        label = { Text("Nombre") },
                        supportingText = { Text("${deviceNameDraft.length}/38") },
                    )
                },
                confirmButton = { TextButton(onClick = {
                    val cleaned = deviceNameDraft.trim()
                    if (cleaned.isNotEmpty()) settingsController.update { it.copy(deviceName = cleaned) }
                    showDeviceNameDialog = false
                }) { Text("Guardar") } },
                dismissButton = { TextButton(onClick = { showDeviceNameDialog = false }) { Text("Cancelar") } },
            )
        }

        if (showAutoAcceptWarning) {
            AlertDialog(
                onDismissRequest = { showAutoAcceptWarning = false },
                title = { Text("¿Desactivar la confirmación?") },
                text = { Text("ShareApp aceptará automáticamente textos y archivos de dispositivos en tu red local. Actívalo solo en una red de confianza.") },
                confirmButton = { TextButton(onClick = {
                    settingsController.update { it.copy(requireApproval = false) }
                    showAutoAcceptWarning = false
                }) { Text("Aceptar automáticamente") } },
                dismissButton = { TextButton(onClick = { showAutoAcceptWarning = false }) { Text("Mantener confirmación") } },
            )
        }

        receivedTexts.firstOrNull()?.takeIf { it.id != shownTextId }?.let { received ->
            AlertDialog(
                onDismissRequest = { shownTextId = received.id },
                title = { Text("Texto recibido de ${received.senderName}") },
                text = { Column { Text("Se ha copiado automáticamente al portapapeles."); Spacer(Modifier.height(8.dp)); Text(received.text) } },
                confirmButton = { TextButton(onClick = { shownTextId = received.id; selectedTab = 1 }) { Text("Ver transferencias") } },
                dismissButton = { TextButton(onClick = { shownTextId = received.id }) { Text("Cerrar") } }
            )
        }

        transferNotice?.let { notice ->
            AlertDialog(onDismissRequest = { transferNotice = null }, title = { Text("ShareApp") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = { transferNotice = null }) { Text("Aceptar") } })
        }
    }
}

@Composable
private fun DevicesScreen(
    devices: List<PeerDevice>,
    isSearching: Boolean,
    onDeviceClick: (PeerDevice) -> Unit,
    onSearch: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(18.dp))
        Text("Comparte cerca.", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
        Spacer(Modifier.height(5.dp))
        Text("Envía archivos y textos entre tus dispositivos.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(PaleGreen), contentAlignment = Alignment.Center) {
                    Text("⌁", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Red local", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Dispositivos de tu red local", color = Muted, fontSize = 12.sp)
                }
                Text("●", color = Green, fontSize = 10.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Dispositivos", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${devices.size} cerca", color = Muted, fontSize = 12.sp)
        }
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (isSearching) "Buscando ShareApp…" else "Aún no hay dispositivos", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("Abre ShareApp en otro equipo conectado a esta Wi‑Fi.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                items(devices, key = { it.id }) { device -> DeviceCard(device) { onDeviceClick(device) } }
            }
        }
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            Text(if (isSearching) "Buscando dispositivos…" else "Buscar dispositivos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DeviceCard(device: PeerDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(PaleGreen), contentAlignment = Alignment.Center) {
                Text(device.name.take(1).uppercase(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("Disponible · ${device.host}:${device.port}", color = Muted, fontSize = 12.sp)
            }
            Text("›", color = Muted, fontSize = 25.sp)
        }
    }
}

@Composable
private fun TransfersScreen(
    receivedTexts: List<ReceivedText>,
    receivedFiles: List<ReceivedFile>,
    onFileClick: (ReceivedFile) -> Unit,
    onTextClick: (ReceivedText) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(18.dp))
        Text("Transferencias", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Actividad reciente entre tus dispositivos.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        if (receivedTexts.isEmpty() && receivedFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(PaleGreen), contentAlignment = Alignment.Center) {
                        Text("⇄", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Todo empieza aquí", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Cuando recibas algo, aparecerá en esta lista.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(receivedFiles, key = { it.id }) { received ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onFileClick(received) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("↓", color = Green, fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(received.fileName, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${received.senderName} · ${formatFileSize(received.bytes.size.toLong())}", color = Muted, fontSize = 12.sp)
                            }
                            Text("›", color = Muted, fontSize = 24.sp)
                        }
                    }
                }
                items(receivedTexts, key = { it.id }) { received ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onTextClick(received) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Texto de ${received.senderName}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(received.text, color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Ver contenido", color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

@Composable
private fun SettingsScreen(
    settings: ShareAppSettings,
    onDeviceNameClick: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(18.dp))
        Text("Ajustes", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Tu espacio de intercambio.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("DISPOSITIVO")
        SettingsCard {
            SettingsActionRow(
                icon = "S",
                title = "Nombre visible",
                value = settings.deviceName,
                action = "Editar",
                onClick = onDeviceNameClick,
            )
        }
        Spacer(Modifier.height(15.dp))
        SettingsSectionTitle("ARCHIVOS")
        SettingsCard {
            SettingsActionRow(
                icon = "↓",
                title = "Carpeta de destino",
                value = settings.saveFolder?.let { if (it.startsWith("content://")) "Carpeta personalizada" else it } ?: "Descargas / ShareApp",
                action = "Cambiar",
                onClick = onChooseFolder,
            )
            if (settings.saveFolder != null) {
                androidx.compose.material3.HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = onResetFolder, modifier = Modifier.align(Alignment.End)) { Text("Restaurar ubicación predeterminada") }
            }
        }
        Spacer(Modifier.height(15.dp))
        SettingsSectionTitle("PRIVACIDAD")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PaleGreen), contentAlignment = Alignment.Center) {
                    Text("✓", color = Green, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Preguntar antes de recibir", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(if (settings.requireApproval) "Cada transferencia requiere tu aprobación." else "Se aceptan transferencias automáticamente en esta red.", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = settings.requireApproval, onCheckedChange = onRequireApprovalChange)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("ShareApp · Solo red local", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsActionRow(icon: String, title: String, value: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PaleGreen), contentAlignment = Alignment.Center) {
            Text(icon, color = Green, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(value, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
