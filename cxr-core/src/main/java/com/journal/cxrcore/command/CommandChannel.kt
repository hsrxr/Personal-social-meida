package com.journal.cxrcore.command

import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICustomCmdCbk
import com.journal.cxrcore.app.JournalApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bidirectional command channel between phone and glasses via CUSTOMAPP Caps protocol.
 *
 * Usage:
 * - Collect [inboundFlow] for events from glasses (key events, etc.).
 * - Call [sendToGlasses] to send commands to glasses.
 * - Call [release] when done.
 */
class CommandChannel {

    companion object {
        private const val TAG = "CommandChannel"
    }

    private val _inboundFlow = MutableSharedFlow<JournalEvent>(extraBufferCapacity = 4)
    val inboundFlow: SharedFlow<JournalEvent> = _inboundFlow.asSharedFlow()

    private var initialized = false

    private val cmdCallback = object : ICustomCmdCbk {
        override fun onCustomCmdResult(key: String?, payload: ByteArray?) {
            // Only process our agreed channel
            if (key != CapsProtocol.CHANNEL_JOURNAL_EVENT || payload == null) return
            val event = parseJournalEvent(payload) ?: return
            _inboundFlow.tryEmit(event)
        }
    }

    /**
     * Registers the custom command callback. Call once after session is built.
     */
    fun init() {
        if (initialized) return
        val link = readyLink() ?: return
        link.setCXRCustomCmdCbk(cmdCallback)
        initialized = true
    }

    /**
     * Sends a command to the glasses app.
     *
     * @param channel the Caps channel name.
     * @param builder lambda to populate the [Caps] payload.
     */
    fun sendToGlasses(channel: String, builder: Caps.() -> Unit) {
        val link = readyLink() ?: return
        val caps = Caps().apply(builder)
        link.sendCustomCmd(channel, caps)
    }

    /**
     * Convenience: sends a phone_cmd to the glasses display.
     */
    fun sendPhoneCmd(cmd: String, text: String = "") {
        sendToGlasses(CapsProtocol.CHANNEL_PHONE_CMD) {
            write(cmd)
            if (text.isNotEmpty()) write(text)
        }
    }

    /**
     * Clears the SDK callback. Call when channel is no longer needed.
     */
    fun release() {
        if (!initialized) return
        val link = readyLink() ?: return
        runCatching {
            link.setCXRCustomCmdCbk(object : ICustomCmdCbk {
                override fun onCustomCmdResult(key: String?, payload: ByteArray?) {}
            })
        }
        initialized = false
    }

    // --- Internal ---

    private fun readyLink(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()

    /**
     * Parses a journal event Caps payload with field order:
     * [0] eventType (String), [1] timestamp (Long), [2] metadata (String, optional).
     */
    private fun parseJournalEvent(payload: ByteArray): JournalEvent? {
        return runCatching {
            val caps = Caps.fromBytes(payload)
            if (caps.size() < 2) return null
            JournalEvent(
                eventType = caps.at(0).string ?: return null,
                timestamp = caps.at(1).long,
                metadata = if (caps.size() > 2) caps.at(2).string ?: "" else "",
            )
        }.getOrNull()
    }
}
