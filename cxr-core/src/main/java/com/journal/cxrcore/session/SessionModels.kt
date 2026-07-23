package com.journal.cxrcore.session

import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.link.GlassDeviceInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Data models for session management.
 */
data class GlassState(
    val deviceName: String = "",
    val batteryLevel: Int = 0,
    val wearing: Boolean = false,
    val brightness: Int = 8,
    val volume: Int = 10,
)
