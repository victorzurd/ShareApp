package org.example.project

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val localServer = ShareAppServer()
    private lateinit var peerDiscovery: PeerDiscovery

    private val requestLocalNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocalServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        peerDiscovery = createPeerDiscovery(applicationContext)

        setContent {
            App(peerDiscovery, localServer)
        }
    }

    override fun onStart() {
        super.onStart()

        val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(localNetworkPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalNetworkPermission.launch(localNetworkPermission)
        } else {
            startLocalServer()
        }
    }

    override fun onStop() {
        localServer.stop()
        peerDiscovery.stop()
        super.onStop()
    }

    private fun startLocalServer() {
        if (localServer.isRunning) return
        localServer.start()
        peerDiscovery.startAdvertising(ShareAppServer.DEFAULT_PORT)
    }
}

