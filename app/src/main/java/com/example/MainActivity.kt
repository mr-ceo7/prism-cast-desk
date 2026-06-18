package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.service.StreamService
import com.example.ui.ScreenStreamViewModel
import com.example.ui.components.MainAppLayout
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenStreamViewModel by viewModels()

    // Handles the response from the OS screen casting authorization prompt
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, StreamService::class.java).apply {
                action = StreamService.ACTION_START_PROJECTION
                putExtra(StreamService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(StreamService.EXTRA_DATA_INTENT, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopScreenCasting()
            Toast.makeText(this, "Screen stream broadcasting permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    // Handles permissions including post notifications and microphone audio capture
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordGranted) {
            Toast.makeText(
                this,
                "Warning: Audio sharing is disabled (Microphone/System Record permission not granted).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Guide Android to whitelist this application from low-power Doze restrictions
        requestIgnoreBatteryOptimization()

        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        permissionLauncher.launch(neededPermissions.toTypedArray())

        setContent {
            // Force dynamicTheme off and darkTheme on to lock our custom low-light Cyber Slate Theme
            MyApplicationTheme(darkTheme = true) {
                MainAppLayout(
                    viewModel = viewModel,
                    onStartCasting = { triggerScreenCasting() },
                    onStopCasting = { stopScreenCasting() }
                )
            }
        }
    }

    private fun triggerScreenCasting() {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = manager.createScreenCaptureIntent()
            captureLauncher.launch(captureIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "System projection API is busy or unsupported.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopScreenCasting() {
        try {
            val stopIntent = Intent(this, StreamService::class.java).apply {
                action = StreamService.ACTION_STOP
            }
            startService(stopIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                stopService(Intent(this, StreamService::class.java))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}

