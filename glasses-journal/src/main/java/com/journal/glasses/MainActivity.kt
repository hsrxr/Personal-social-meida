package com.journal.glasses

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
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

    companion object {
        private const val TAG = "MainActivity"
    }

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

    /**
     * Intercept all hardware key events from the glasses.
     *
     * This catches camera button, temple button, and touchpad events.
     * Returns true to consume the event and prevent system default handling.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(TAG, "onKeyDown: keyCode=$keyCode repeat=${event?.repeatCount}")
        viewModel.handleKeyCode(keyCode, event?.repeatCount ?: 0)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("MainActivity", "onKeyUp: keyCode=$keyCode")
        return super.onKeyUp(keyCode, event)
    }
}
