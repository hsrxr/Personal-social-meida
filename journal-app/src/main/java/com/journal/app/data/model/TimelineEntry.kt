package com.journal.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class EntryType { PHOTO, AUDIO, NOTE, AGENT_DIALOG, MOMENT_MARK }

data class TimelineEntry(
    val id: String,
    val date: LocalDate,
    val timestamp: Long,
    val type: EntryType,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val transcription: String? = null,
    val noteText: String? = null,
    val locationName: String? = null,
    val durationMs: Int? = null,
    val isStarred: Boolean = false,
    val tags: List<Tag> = emptyList(),
)
