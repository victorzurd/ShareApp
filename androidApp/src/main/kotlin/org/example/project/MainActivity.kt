package org.example.project

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var localServer: ShareAppServer? = null
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
            App(peerDiscovery)
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
        localServer?.stop()
        localServer = null
        peerDiscovery.stop()
        super.onStop()
    }

    private fun startLocalServer() {
        if (localServer != null) return
        localServer = ShareAppServer().also { it.start() }
        peerDiscovery.startAdvertising(ShareAppServer.DEFAULT_PORT)
    }
}

