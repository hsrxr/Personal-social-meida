package com.journal.app.data.repository

import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.TimelineEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TimelineRepository {
    fun getJournal(date: LocalDate): Flow<DailyJournal>
    fun getJournals(range: ClosedRange<LocalDate>): Flow<List<DailyJournal>>

    /** Daily journals across [range], each populated with its timeline entries, newest day first. */
    fun getJournalsWithEntries(range: ClosedRange<LocalDate>): Flow<List<DailyJournal>>

    fun getEntries(date: LocalDate): Flow<List<TimelineEntry>>
    suspend fun getEntry(entryId: String): TimelineEntry?
    suspend fun addEntry(entry: TimelineEntry): String
    suspend fun updateTags(entryId: String, tags: List<com.journal.app.data.model.Tag>)
    suspend fun setMood(date: LocalDate, mood: String)
    suspend fun toggleStar(entryId: String)
}
