package com.journal.glasses.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.journal.glasses.protocol.CapsProtocol

/**
 * System broadcast actions for Rokid glasses keys and touchpad events.
 */
enum class KeyType(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    BUTTON_DOWN("com.android.action.ACTION_SPRITE_BUTTON_DOWN"),
    BUTTON_UP("com.android.action.ACTION_SPRITE_BUTTON_UP"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    AI_START("com.android.action.ACTION_AI_START"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
    ACTION_TWO_FINGER_SINGLE_TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
    ACTION_TWO_FINGER_DOUBLE_TAP("com.android.action.ACTION_TWO_FINGER_DOUBLE_TAP"),
    ACTION_TWO_FINGER_SWIPE_FORWARD("com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"),
    ACTION_TWO_FINGER_SWIPE_BACK("com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"),
    ACTION_SETTINGS_KEY("com.android.action.ACTION_SETTINGS_KEY"),
}

/**
 * Listener for key events from the BroadcastReceiver.
 */
fun interface KeyEventListener {
    fun onKeyEvent(keyType: KeyType)
}

/**
 * BroadcastReceiver that listens for all Rokid glass key/touchpad system broadcasts.
 * Calls [KeyEventListener.onKeyEvent] and aborts the broadcast to prevent duplicate handling.
 */
class KeyReceiver(
    private val listener: KeyEventListener,
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "KeyReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val keyType = KeyType.entries.find { it.action == action } ?: run {
            Log.w(TAG, "onReceive: unknown action=$action")
            return
        }
        Log.i(TAG, "onReceive: $keyType")
        listener.onKeyEvent(keyType)
        abortBroadcast()
    }
}

// ─── Key → Business Action Mapping ────────────────────────────────────────────

/**
 * Maps raw [KeyType] to a business action ready for Caps serialization.
 */
data class KeyAction(
    val eventType: String,
    val timestamp: Long,
    val metadata: String = "",
)

/**
 * Key → Action mapping per the product spec:
 * | Key             | KeyType                     | Business Action           |
 * |-----------------|-----------------------------|---------------------------|
 * | Click temple    | CLICK                       | Take photo 📷           |
 * | Double-click    | DOUBLE_CLICK                | Quick voice note          |
 * | Long press      | LONG_PRESS                  | Start/stop agent talk     |
 * | Swipe forward   | ACTION_TWO_FINGER_SWIPE_FWD | Play today's summary TTS  |
 * | Swipe back      | ACTION_TWO_FINGER_SWIPE_BACK| Skip / back               |
 */
fun KeyType.toAction(): KeyAction? {
    val now = System.currentTimeMillis()
    return when (this) {
        KeyType.CLICK -> KeyAction(CapsProtocol.EventType.TAKE_PHOTO, now)
        KeyType.DOUBLE_CLICK -> KeyAction(CapsProtocol.EventType.QUICK_NOTE_START, now)
        KeyType.LONG_PRESS -> KeyAction(CapsProtocol.EventType.AGENT_TALK_START, now)
        KeyType.ACTION_TWO_FINGER_SWIPE_FORWARD -> KeyAction(CapsProtocol.EventType.PLAY_SUMMARY, now)
        KeyType.ACTION_TWO_FINGER_SWIPE_BACK -> KeyAction(CapsProtocol.EventType.SKIP, now)
        KeyType.BUTTON_DOWN, KeyType.BUTTON_UP,
        KeyType.AI_START,
        KeyType.ACTION_TWO_FINGER_SINGLE_TAP,
        KeyType.ACTION_TWO_FINGER_DOUBLE_TAP,
        KeyType.ACTION_SETTINGS_KEY -> null
    }
}
