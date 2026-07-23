package com.journal.app.data.model

import java.time.LocalDate

data class DailyJournal(
    val date: LocalDate,
    val entries: List<TimelineEntry> = emptyList(),
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val mood: String? = null,
    val entryCount: Int = 0,
)
