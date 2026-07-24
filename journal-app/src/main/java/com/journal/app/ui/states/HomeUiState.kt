package com.journal.app.ui.states

import com.journal.app.data.model.TimelineEntry
import com.journal.cxrcore.link.LinkState
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val entries: List<TimelineEntry> = emptyList(),
    val summaryPreview: String? = null,
    val mood: String? = null,
    // Glasses connection state
    val glassLinkState: LinkState = LinkState.Idle,
    val glassConnected: Boolean = false,
    val glassBattery: Int = 0,
    val glassDeviceName: String = "",
    val glassWearing: Boolean = false,
    // Connection flow status
    val connectionStatus: String = "",
    // Recording state
    val isRecording: Boolean = false,
)
