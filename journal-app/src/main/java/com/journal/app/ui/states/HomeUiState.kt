package com.journal.app.ui.states

import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.Summary
import com.journal.app.data.model.TimelineEntry
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val entries: List<TimelineEntry> = emptyList(),
    val summaryPreview: Summary? = null,
    val glassConnected: Boolean = false,
    val glassBattery: Int = 0,
)
