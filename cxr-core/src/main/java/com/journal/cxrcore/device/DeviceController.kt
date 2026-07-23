package com.journal.cxrcore.device

import com.journal.cxrcore.app.JournalApplication
import com.journal.cxrcore.link.LinkConnectionHub
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls glasses hardware: brightness (0..15), volume (0..15), device info.
 *
 * Does NOT require session build (CustomView/CustomApp); link-ready is sufficient.
 */
class DeviceController {

    val brightness: StateFlow<Int> = LinkConnectionHub.brightness
    val volume: StateFlow<Int> = LinkConnectionHub.volume

    /**
     * Sets glasses brightness. Range: 0..15.
     */
    fun setBrightness(level: Int) {
        val link = readyLinkOrNull() ?: return
        link.setGlassBrightness(level)
    }

    /**
     * Sets glasses volume. Range: 0..15.
     */
    fun setVolume(level: Int) {
        val link = readyLinkOrNull() ?: return
        link.setGlassVolume(level)
    }

    /**
     * Requests current device info. Result arrives via [LinkConnectionHub.deviceInfo].
     */
    fun refreshDeviceInfo() {
        val link = readyLinkOrNull() ?: return
        link.getGlassDeviceInfo()
    }

    private fun readyLinkOrNull() =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()
}
