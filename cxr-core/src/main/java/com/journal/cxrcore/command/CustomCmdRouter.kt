package com.journal.cxrcore.command

import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICustomCmdCbk

/**
 * Routes incoming custom command callbacks to registered per-channel handlers.
 *
 * There is only ONE [ICustomCmdCbk] per CXRLink session. This router is the
 * single owner of that callback and dispatches by channel key.
 *
 * Usage:
 * - Call [init] once after session is built (registers with CXRLink).
 * - Call [subscribe] to receive messages on a specific channel.
 * - Call [release] to unregister.
 */
object CustomCmdRouter {

    private const val TAG = "CustomCmdRouter"

    private val handlers = mutableMapOf<String, (ByteArray) -> Unit>()
    private var initialized = false
    private var link: CXRLink? = null

    private val routerCallback = object : ICustomCmdCbk {
        override fun onCustomCmdResult(key: String?, payload: ByteArray?) {
            if (key == null || payload == null) return
            val handler = handlers[key]
            if (handler != null) {
                handler(payload)
            } else {
                Log.v(TAG, "No handler for channel: $key")
            }
        }
    }

    /**
     * Registers the single ICustomCmdCbk with the shared CXRLink.
     * Must be called after session is built, before any [subscribe] calls.
     */
    fun init(sharedLink: CXRLink) {
        if (initialized) return
        link = sharedLink
        sharedLink.setCXRCustomCmdCbk(routerCallback)
        initialized = true
        Log.d(TAG, "CustomCmdRouter initialized")
    }

    /**
     * Subscribes to a channel. [onReceive] is called with the raw Caps payload bytes.
     * Only one handler per channel; re-subscribing overwrites.
     */
    fun subscribe(channel: String, onReceive: (ByteArray) -> Unit) {
        handlers[channel] = onReceive
        Log.d(TAG, "Subscribed to channel: $channel")
    }

    /**
     * Unsubscribes from a channel.
     */
    fun unsubscribe(channel: String) {
        handlers.remove(channel)
        Log.d(TAG, "Unsubscribed from channel: $channel")
    }

    /**
     * Unregisters the callback from CXRLink. Call when all channels are done.
     */
    fun release() {
        if (!initialized) return
        val l = link ?: return
        runCatching {
            l.setCXRCustomCmdCbk(object : ICustomCmdCbk {
                override fun onCustomCmdResult(key: String?, payload: ByteArray?) {}
            })
        }
        handlers.clear()
        initialized = false
        Log.d(TAG, "CustomCmdRouter released")
    }
}
