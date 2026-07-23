package com.journal.cxrcore.command

import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.link.CXRLink
import com.journal.cxrcore.app.JournalApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bidirectional command channel between phone and glasses via Caps protocol.
 *
 * Key events from glasses (journal_event) are emitted via [inboundFlow].
 * Outbound commands go through [sendToGlasses].
 *
 * IMPORTANT: Must call [init] once after session is built BEFORE any other pipelines.
 * The [CustomCmdRouter.init] must be called first (by SessionManager or debug code),
 * then [CommandChannel.init] subscribes to its channels.
 */
class CommandChannel {

    companion object {
        private const val TAG = "CommandChannel"
    }

    private val _inboundFlow = MutableSharedFlow<JournalEvent>(extraBufferCapacity = 4)
    val inboundFlow: SharedFlow<JournalEvent> = _inboundFlow.asSharedFlow()

    private var subscribed = false

    /**
     * Subscribes to the journal_event channel via the shared CustomCmdRouter.
     * CustomCmdRouter.init() must have been called first.
     */
    fun init() {
        if (subscribed) return
        CustomCmdRouter.subscribe(CapsProtocol.CHANNEL_JOURNAL_EVENT) { payload ->
            val event = parseJournalEvent(payload) ?: return@subscribe
            _inboundFlow.tryEmit(event)
        }
        subscribed = true
        Log.d(TAG, "CommandChannel subscribed to ${CapsProtocol.CHANNEL_JOURNAL_EVENT}")
    }

    /**
     * Sends a command to the glasses app.
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
     * Unsubscribes from the journal_event channel.
     */
    fun release() {
        if (subscribed) {
            CustomCmdRouter.unsubscribe(CapsProtocol.CHANNEL_JOURNAL_EVENT)
            subscribed = false
        }
    }

    private fun readyLink(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()

    /**
     * Parses a journal event Caps payload.
     * Fields: [0] eventType (String), [1] timestamp (Long), [2] metadata (String, optional)
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
