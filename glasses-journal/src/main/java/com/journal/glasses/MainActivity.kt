package com.journal.glasses

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.journal.glasses.display.StatusScreen

/**
 * Entry Activity for the glasses-side Journal app.
 *
 * This is the target of CUSTOMAPP.appStart from the phone side.
 * Must match SessionManager.glassesMainActivity.
 *
 * Lifecycle:
 * - onCreate: register key receiver, start CXR bridge.
 * - onDestroy: unregister key receiver.
 */
class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while this app is foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.registerKeyReceiver(this)

        setContent {
            val isConnected by viewModel.isConnected.collectAsState()
            val displayText by viewModel.displayText.collectAsState()
            val isRecording by viewModel.isRecording.collectAsState()

            StatusScreen(
                isConnected = isConnected,
                displayText = displayText,
                isRecording = isRecording,
            )
        }
    }

    override fun onDestroy() {
        viewModel.unregisterKeyReceiver(this)
        super.onDestroy()
    }
}
