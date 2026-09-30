package org.example.project

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private var localServer: ShareAppServer? = null

    private val requestLocalNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocalServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
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
        super.onStop()
    }

    private fun startLocalServer() {
        if (localServer != null) return
        localServer = ShareAppServer().also { it.start() }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
