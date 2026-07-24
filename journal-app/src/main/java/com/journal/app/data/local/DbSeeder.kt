package com.journal.app.data.local

import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.entity.toEntity
import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.TimelineEntry
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Populates a fresh install with a few days of English sample journals so Home and
 * Full Journal look like the design out of the box. Idempotent: only seeds when the
 * journals table is empty, so it never overwrites real captures.
 */
@Singleton
class DbSeeder @Inject constructor(
    private val journalDao: JournalDao,
    private val entryDao: EntryDao,
) {
    suspend fun seedIfEmpty() {
        if (journalDao.count() > 0) return

        val today = LocalDate.now()
        val days = listOf(
            SeedDay(
                date = today.minusDays(1),
                summary = "Wrapped up the big project and celebrated with a weekend hike — feeling accomplished and light.",
                keywords = listOf("Weekend", "Project"),
                mood = "great",
                note = "Finished the project, feels great. Making something for the weekend — maybe a long photo walk.",
                photos = listOf(
                    "https://picsum.photos/seed/echoes-hike-1/500/500",
                    "https://picsum.photos/seed/echoes-hike-2/500/500",
                    "https://picsum.photos/seed/echoes-hike-3/500/500",
                ),
                audioDurationMs = 60_000,
                audioTranscription = "Voice note recapping the project launch and the plan for the weekend.",
            ),
            SeedDay(
                date = today.minusDays(3),
                summary = "A calm morning run by the river reset the whole week.",
                keywords = listOf("running", "Morning"),
                mood = "calm",
                note = "Morning run by the river. #running felt amazing today.",
                photos = listOf("https://picsum.photos/seed/echoes-run-1/500/500"),
                audioDurationMs = 45_000,
                audioTranscription = "Quick memo after the run — legs tired but head clear.",
            ),
            SeedDay(
                date = today.minusDays(5),
                summary = "Slow afternoon with coffee and a good book at the corner cafe.",
                keywords = listOf("coffee", "reading"),
                mood = "cozy",
                note = "Coffee and reading at the corner cafe. Nowhere to be, and that was the point.",
                photos = listOf(
                    "https://picsum.photos/seed/echoes-cafe-1/500/500",
                    "https://picsum.photos/seed/echoes-cafe-2/500/500",
                ),
                audioDurationMs = null,
                audioTranscription = null,
            ),
        )

        val entries = days.flatMap { buildEntries(it) }
        entryDao.insertAll(entries.map { it.toEntity() })
        days.forEach { day ->
            val count = entries.count { it.date == day.date }
            journalDao.upsert(
                DailyJournal(
                    date = day.date,
                    summary = day.summary,
                    keywords = day.keywords,
                    mood = day.mood,
                    entryCount = count,
                ).toEntity(),
            )
        }
    }

    private fun buildEntries(day: SeedDay): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()
        entries += TimelineEntry(
            id = UUID.randomUUID().toString(),
            date = day.date,
            timestamp = epochAt(day.date, 9, 12),
            type = EntryType.NOTE,
            noteText = day.note,
        )
        day.photos.forEachIndexed { index, url ->
            entries += TimelineEntry(
                id = UUID.randomUUID().toString(),
                date = day.date,
                timestamp = epochAt(day.date, 10, 20 + index),
                type = EntryType.PHOTO,
                imageUrl = url,
                thumbnailUrl = url,
            )
        }
        if (day.audioDurationMs != null) {
            entries += TimelineEntry(
                id = UUID.randomUUID().toString(),
                date = day.date,
                timestamp = epochAt(day.date, 18, 5),
                type = EntryType.AUDIO,
                transcription = day.audioTranscription,
                durationMs = day.audioDurationMs,
            )
        }
        return entries
    }

    private fun epochAt(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private data class SeedDay(
        val date: LocalDate,
        val summary: String,
        val keywords: List<String>,
        val mood: String,
        val note: String,
        val photos: List<String>,
        val audioDurationMs: Int?,
        val audioTranscription: String?,
    )
}
