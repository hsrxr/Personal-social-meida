package com.journal.app.ui.states

import com.journal.app.data.model.DailyJournal

data class FullJournalUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val journals: List<DailyJournal> = emptyList(),
) {
    /** Days whose summary, notes, transcription, or tags match [query] (case-insensitive). */
    val filteredJournals: List<DailyJournal>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return journals
            return journals.filter { journal ->
                val haystack = buildString {
                    journal.summary?.let { append(it).append(' ') }
                    journal.keywords.forEach { append(it).append(' ') }
                    journal.entries.forEach { entry ->
                        entry.noteText?.let { append(it).append(' ') }
                        entry.transcription?.let { append(it).append(' ') }
                        entry.tags.forEach { append(it.name).append(' ') }
                    }
                }
                haystack.contains(q, ignoreCase = true)
            }
        }
}
