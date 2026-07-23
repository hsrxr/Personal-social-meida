package com.journal.cxrcore.link

import android.util.Log
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.utils.GlassInfo
import com.journal.cxrcore.app.JournalApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single [ICXRLinkCbk] for the entire process.
 * Registered once via [LinkSessionGate], submodules collect StateFlows instead of
 * calling [com.rokid.cxr.link.CXRLink.setCXRLinkCbk].
 *
 * Mirrors Sample's [CxrLinkConnectionHub] pattern.
 */
object LinkConnectionHub {

    private const val TAG = "LinkConnectionHub"

    private val _cxrlConnected = MutableStateFlow(false)
    val cxrlConnected: StateFlow<Boolean> = _cxrlConnected.asStateFlow()

    private val _btConnected = MutableStateFlow(false)
    val btConnected: StateFlow<Boolean> = _btConnected.asStateFlow()

    private val _wearingStatus = MutableStateFlow<Boolean?>(null)
    val wearingStatus: StateFlow<Boolean?> = _wearingStatus.asStateFlow()

    private val _deviceInfo = MutableStateFlow(GlassDeviceInfo.EMPTY)
    val deviceInfo: StateFlow<GlassDeviceInfo> = _deviceInfo.asStateFlow()

    private val _brightness = MutableStateFlow(8)
    val brightness: StateFlow<Int> = _brightness.asStateFlow()

    private val _volume = MutableStateFlow(10)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _glassAiInterrupt = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val glassAiInterrupt: SharedFlow<Boolean> = _glassAiInterrupt.asSharedFlow()

    /** Combined ready: CXR connected AND BT connected. */
    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    val linkCallback: ICXRLinkCbk = object : ICXRLinkCbk {
        override fun onCXRLConnected(connected: Boolean) {
            Log.i(TAG, "onCXRLConnected: connected=$connected (bt=${_btConnected.value})")
            _cxrlConnected.value = connected
            syncApplicationSessionReady()
        }

        override fun onGlassBtConnected(connected: Boolean) {
            Log.i(TAG, "onGlassBtConnected: connected=$connected (cxr=${_cxrlConnected.value})")
            _btConnected.value = connected
            syncApplicationSessionReady()
        }

        override fun onGlassAiAssistStart() {}

        override fun onGlassAiAssistStop() {}

        override fun onGlassAiInterrupt(interruptWake: Boolean) {
            _glassAiInterrupt.tryEmit(interruptWake)
        }

        override fun onGlassDeviceInfo(deviceInfo: GlassInfo) {
            Log.d(TAG, "onGlassDeviceInfo: $deviceInfo")
            _deviceInfo.value = GlassDeviceInfo(
                brightness = deviceInfo.brightness,
                volume = deviceInfo.sound,
                batteryLevel = deviceInfo.batteryLevel,
                deviceName = deviceInfo.deviceName ?: "",
                wearingStatus = deviceInfo.wearingStatus ?: "",
            )
            _brightness.value = deviceInfo.brightness
            _volume.value = deviceInfo.sound
        }

        override fun onGlassWearingStatus(wearing: Boolean) {
            Log.d(TAG, "onGlassWearingStatus: wearing=$wearing")
            _wearingStatus.value = wearing
        }
    }

    fun reset() {
        Log.d(TAG, "reset: clearing link state")
        _cxrlConnected.value = false
        _btConnected.value = false
        _wearingStatus.value = null
        _deviceInfo.value = GlassDeviceInfo.EMPTY
        _brightness.value = 8
        _volume.value = 10
        _isSessionReady.value = false
    }

    private fun syncApplicationSessionReady() {
        val ready = _cxrlConnected.value && _btConnected.value
        _isSessionReady.value = ready
        val app = runCatching { JournalApplication.instance }.getOrNull() ?: return
        app.isSessionReady = ready
        Log.d(TAG, "syncApplicationSessionReady: isSessionReady=$ready")
    }
}

/**
 * Immutable snapshot of glass device info from [GlassInfo] callback.
 */
data class GlassDeviceInfo(
    val brightness: Int,
    val volume: Int,
    val batteryLevel: Int,
    val deviceName: String,
    val wearingStatus: String,
) {
    companion object {
        val EMPTY = GlassDeviceInfo(
            brightness = 0, volume = 0, batteryLevel = 0,
            deviceName = "", wearingStatus = "",
        )
    }
}
