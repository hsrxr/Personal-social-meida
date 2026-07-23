package com.journal.glasses

import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import com.journal.glasses.audio.GlassesAudioCapture
import com.journal.glasses.keys.KeyAction
import com.journal.glasses.keys.KeyReceiver
import com.journal.glasses.keys.KeyType
import com.journal.glasses.protocol.CapsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main ViewModel for the glasses-side Journal app.
 *
 * Responsibilities:
 * - Initialize CXRServiceBridge: setStatusListener + subscribe to phone commands.
 * - Handle key events from [KeyReceiver] → map to actions → send to phone.
 * - Manage GlassesAudioCapture for VAD-gated audio recording.
 * - Expose UI state for the minimal Compose StatusScreen.
 */
class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // CXR-S bridge
    private val cxrBridge = CXRServiceBridge()

    // Audio capture
    private val audioCapture = GlassesAudioCapture(cxrBridge)

    // Key receiver
    lateinit var keyReceiver: KeyReceiver
        private set

    // ── UI State ──
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Track talk state for LONG_PRESS toggle
    private var isTalkActive = false

    init {
        initCxrBridge()
        initKeyReceiver()
    }

    // ── CXR Bridge ──

    private fun initCxrBridge() {
        cxrBridge.setStatusListener(object : CXRServiceBridge.StatusListener {
            override fun onConnected(p0: String?, p1: String?, p2: Int) {
                Log.i(TAG, "CXR bridge connected")
                _isConnected.value = true
            }

            override fun onDisconnected() {
                Log.w(TAG, "CXR bridge disconnected")
                _isConnected.value = false
            }

            override fun onConnecting(p0: String?, p1: String?, p2: Int) {
                Log.d(TAG, "CXR bridge connecting")
            }

            override fun onARTCStatus(p0: Float, p1: Boolean) {}
            override fun onRokidAccountChanged(p0: String?) {}
            override fun onAudioNoise(p0: Float) {}
        })

        // Subscribe to phone commands
        cxrBridge.subscribe(CapsProtocol.CHANNEL_PHONE_CMD, phoneCmdCallback)
    }

    private val phoneCmdCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, value: ByteArray?) {
            if (name != CapsProtocol.CHANNEL_PHONE_CMD || args == null) return
            handlePhoneCmd(args)
        }
    }

    /**
     * Parses phone_cmd messages. Expected fields:
     * [0] cmd (String): "show_status" | "vibrate" | "update_display"
     * [1] text (String, optional): display text.
     */
    private fun handlePhoneCmd(args: Caps) {
        if (args.size() < 1) return
        val cmd = args.at(0).string ?: return
        val text = if (args.size() > 1) args.at(1).string ?: "" else ""

        when (cmd) {
            CapsProtocol.PhoneCmd.SHOW_STATUS -> {
                _displayText.value = text
            }
            CapsProtocol.PhoneCmd.UPDATE_DISPLAY -> {
                _displayText.value = text
            }
            CapsProtocol.PhoneCmd.VIBRATE -> {
                // Vibration not implemented in this minimal version
                Log.d(TAG, "vibrate requested (not implemented)")
            }
        }
    }

    // ── Key Events ──

    private fun initKeyReceiver() {
        keyReceiver = KeyReceiver { keyType -> onKeyEvent(keyType) }
    }

    /**
     * Registers the key receiver with the Activity.
     * Call from Activity.onCreate.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerKeyReceiver(activity: Activity) {
        activity.registerReceiver(keyReceiver, IntentFilter().apply {
            KeyType.entries.forEach { addAction(it.action) }
            priority = 100
        })
    }

    /**
     * Unregisters the key receiver from the Activity.
     * Call from Activity.onDestroy.
     */
    fun unregisterKeyReceiver(activity: Activity) {
        runCatching { activity.unregisterReceiver(keyReceiver) }
    }

    private fun onKeyEvent(keyType: KeyType) {
        Log.d(TAG, "onKeyEvent: $keyType")

        when (keyType) {
            KeyType.CLICK -> {
                // Mark moment: send event to phone
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
                _displayText.value = "⭐ Moment marked"
            }

            KeyType.DOUBLE_CLICK -> {
                // Quick voice note: start audio capture
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
                startTalk()
            }

            KeyType.LONG_PRESS -> {
                // Toggle agent talk
                if (isTalkActive) {
                    stopTalk()
                    sendJournalEvent(KeyAction(CapsProtocol.EventType.AGENT_TALK_STOP, System.currentTimeMillis()))
                } else {
                    sendJournalEvent(KeyAction(CapsProtocol.EventType.AGENT_TALK_START, System.currentTimeMillis()))
                    startTalk()
                }
            }

            KeyType.ACTION_TWO_FINGER_SWIPE_FORWARD,
            KeyType.ACTION_TWO_FINGER_SWIPE_BACK -> {
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
            }

            else -> { /* unmapped key, ignore */ }
        }
    }

    // ── Audio ──

    private fun startTalk() {
        isTalkActive = true
        audioCapture.startRecording()
        _isRecording.value = true
        _displayText.value = "🎤 Listening..."
    }

    private fun stopTalk() {
        isTalkActive = false
        audioCapture.stopRecording()
        _isRecording.value = false
        _displayText.value = ""
    }

    // ── Messaging ──

    /**
     * Sends a journal event Caps message to the phone.
     * Fields: [0] eventType (String), [1] timestamp (Long), [2] metadata (String, optional).
     */
    private fun sendJournalEvent(action: KeyAction) {
        try {
            val caps = Caps().apply {
                write(action.eventType)
                write(action.timestamp)
                if (action.metadata.isNotEmpty()) write(action.metadata)
            }
            cxrBridge.sendMessage(CapsProtocol.CHANNEL_JOURNAL_EVENT, caps)
            Log.d(TAG, "sendJournalEvent: ${action.eventType} at ${action.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "sendJournalEvent failed", e)
        }
    }

    // ── Lifecycle ──

    override fun onCleared() {
        super.onCleared()
        audioCapture.release()
        if (isTalkActive) stopTalk()
    }
}
