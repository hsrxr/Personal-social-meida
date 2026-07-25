package com.journal.app.ui.states

import com.journal.app.data.model.TimelineEntry
import java.time.LocalDate

data class JournalDayUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val entries: List<TimelineEntry> = emptyList(),
)
