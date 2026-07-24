package com.journal.glasses

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import com.journal.glasses.keys.KeyAction
import com.journal.glasses.keys.KeyReceiver
import com.journal.glasses.keys.KeyType
import com.journal.glasses.keys.toAction
import com.journal.glasses.protocol.CapsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main ViewModel for the glasses-side Journal app.
 *
 * Responsibilities:
 * - Initialize CXRServiceBridge: setStatusListener + subscribe to phone commands.
 * - Handle key events → map to journal events → send to phone.
 * - Phone triggers actual audio streaming via SDK's startAudioStream() API;
 *   this app only sends key events, not audio data.
 * - Expose UI state for the minimal Compose StatusScreen.
 */
class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val cxrBridge = CXRServiceBridge()

    lateinit var keyReceiver: KeyReceiver
        private set

    // ── UI State ──
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

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
        val ret = cxrBridge.subscribe(CapsProtocol.CHANNEL_PHONE_CMD, phoneCmdCallback)
        Log.i(TAG, "subscribe(${CapsProtocol.CHANNEL_PHONE_CMD}) = $ret (0=success, -1=err, -2=dup)")

        // Confirm subscription: send subscribe_ok event to phone
        if (ret == 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                cxrBridge.sendMessage(CapsProtocol.CHANNEL_JOURNAL_EVENT, Caps().apply {
                    write("subscribe_ok")
                    writeInt64(System.currentTimeMillis())
                    write("channel=${CapsProtocol.CHANNEL_PHONE_CMD}")
                })
                Log.i(TAG, "Sent subscribe_ok confirmation to phone")
            }, 1000)
        }
    }

    private val phoneCmdCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, value: ByteArray?) {
            Log.i(TAG, "phoneCmdCallback: name=$name, argsSize=${args?.size()}, valueSize=${value?.size}")
            if (args == null || args.size() == 0) {
                Log.w(TAG, "phoneCmdCallback: empty args")
                return
            }
            handlePhoneCmd(args)
        }
    }

    private fun handlePhoneCmd(args: Caps) {
        val offset = if (args.size() >= 2 && args.at(0).string == CapsProtocol.CHANNEL_RETURN_KEY) 1 else 0
        if (args.size() <= offset) return
        val cmd = args.at(offset).string ?: return
        val text = if (args.size() > offset + 1) args.at(offset + 1).string ?: "" else ""

        when (cmd) {
            CapsProtocol.PhoneCmd.SHOW_STATUS,
            CapsProtocol.PhoneCmd.UPDATE_DISPLAY -> {
                _displayText.value = text
                Log.i(TAG, "display text updated: '$text'")
            }
            CapsProtocol.PhoneCmd.VIBRATE -> {
                Log.d(TAG, "vibrate requested (not implemented)")
            }
        }
    }

    // ── Key Events ──

    private fun initKeyReceiver() {
        keyReceiver = KeyReceiver { keyType -> onKeyEvent(keyType) }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerKeyReceiver(activity: Activity) {
        val filter = IntentFilter().apply {
            KeyType.entries.forEach { addAction(it.action) }
            priority = 100
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(keyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(keyReceiver, filter)
        }
    }

    fun unregisterKeyReceiver(activity: Activity) {
        runCatching { activity.unregisterReceiver(keyReceiver) }
    }

    private fun onKeyEvent(keyType: KeyType) {
        Log.i(TAG, "onKeyEvent: $keyType")
        when (keyType) {
            KeyType.CLICK -> {
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
                _displayText.value = "📷 Photo"
            }
            KeyType.DOUBLE_CLICK -> {
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
                _displayText.value = "🎤 Quick note"
            }
            KeyType.LONG_PRESS -> {
                val now = System.currentTimeMillis()
                if (_isRecording.value) {
                    _isRecording.value = false
                    _displayText.value = ""
                    sendJournalEvent(KeyAction(CapsProtocol.EventType.AGENT_TALK_STOP, now))
                } else {
                    _isRecording.value = true
                    _displayText.value = "🎤 Agent talk"
                    sendJournalEvent(KeyAction(CapsProtocol.EventType.AGENT_TALK_START, now))
                }
            }
            KeyType.ACTION_TWO_FINGER_SWIPE_FORWARD,
            KeyType.ACTION_TWO_FINGER_SWIPE_BACK -> {
                val action = keyType.toAction() ?: return
                sendJournalEvent(action)
            }
            else -> {}
        }
    }

    // ── KeyEvent path (from Activity.onKeyDown) ──

    fun handleKeyCode(keyCode: Int, repeatCount: Int = 0) {
        Log.i(TAG, "handleKeyCode: keyCode=$keyCode repeat=$repeatCount")
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_CAMERA -> {
                sendJournalEvent(KeyAction("take_photo", System.currentTimeMillis()))
                _displayText.value = "📷 Photo"
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                onKeyEvent(KeyType.CLICK)
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> onKeyEvent(KeyType.LONG_PRESS)
            android.view.KeyEvent.KEYCODE_FORWARD -> {
                onKeyEvent(KeyType.DOUBLE_CLICK)
                _displayText.value = "🎤 Quick note"
            }
            else -> Log.i(TAG, "handleKeyCode: unmapped keyCode=$keyCode")
        }
    }

    // ── Messaging ──

    private fun sendJournalEvent(action: KeyAction) {
        try {
            val caps = Caps().apply {
                write(action.eventType)
                writeInt64(action.timestamp)
                if (action.metadata.isNotEmpty()) write(action.metadata)
            }
            cxrBridge.sendMessage(CapsProtocol.CHANNEL_JOURNAL_EVENT, caps)
            Log.d(TAG, "sendJournalEvent: ${action.eventType} at ${action.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "sendJournalEvent failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
