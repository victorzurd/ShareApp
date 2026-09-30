package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

private data class DemoTransfer(val name: String, val detail: String, val progress: Float, val sending: Boolean)
private data class PeerCheckState(
    val peer: PeerDevice,
    val isChecking: Boolean,
    val message: String? = null,
    val error: String? = null,
)

@Composable
fun App(peerDiscovery: PeerDiscovery) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val devices by peerDiscovery.devices.collectAsState()
    val isSearching by peerDiscovery.isSearching.collectAsState()
    var peerCheck by remember { mutableStateOf<PeerCheckState?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val peerApi = remember { PeerApiClient() }

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
            Column(modifier = Modifier.fillMaxSize()) {
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
                    Text("En línea", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        1 -> TransfersScreen()
                        else -> SettingsScreen()
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
                    TextButton(onClick = {
                        checkJob?.cancel()
                        peerCheck = null
                    }) {
                        Text(if (result.isChecking) "Cancelar" else "Cerrar")
                    }
                }
            )
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
        Text("Envía archivos directamente entre tus dispositivos.", color = Muted, fontSize = 14.sp)
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
                    Text("Conectado a Casa · privada", color = Muted, fontSize = 12.sp)
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
private fun TransfersScreen() {
    val transfers = listOf(
        DemoTransfer("Fotos vacaciones.zip", "A MacBook de Alex · 68 %", 0.68f, true),
        DemoTransfer("documento.pdf", "Recibido · completado", 1f, false)
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(18.dp))
        Text("Transferencias", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Actividad reciente entre tus dispositivos.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(transfers) { transfer -> TransferCard(transfer) }
        }
    }
}

@Composable
private fun TransferCard(transfer: DemoTransfer) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(PaleGreen), contentAlignment = Alignment.Center) {
                    Text(if (transfer.sending) "↑" else "↓", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(transfer.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(transfer.detail, color = Muted, fontSize = 12.sp)
                }
                if (!transfer.sending) Text("✓", color = Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (transfer.sending) {
                Spacer(Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Line)) {
                    Box(modifier = Modifier.fillMaxWidth(transfer.progress).height(5.dp).clip(CircleShape).background(Green))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(18.dp))
        Text("Ajustes", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Personaliza cómo aparece tu dispositivo.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        SettingRow("Nombre del dispositivo", "Pixel 8")
        SettingRow("Guardar archivos en", "Descargas")
        SettingRow("Aceptar transferencias", "Preguntar siempre")
        Spacer(Modifier.height(18.dp))
        Text("ShareApp · Transferencia local y privada", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(value, color = Muted, fontSize = 12.sp)
            }
            Text("›", color = Muted, fontSize = 23.sp)
        }
    }
}
