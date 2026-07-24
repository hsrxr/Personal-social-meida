package com.journal.app.data.repository

import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.dao.TagDao
import com.journal.app.data.local.entity.toDomain
import com.journal.app.data.local.entity.toEntity
import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.Tag
import com.journal.app.data.model.TimelineEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepositoryImpl @Inject constructor(
    private val journalDao: JournalDao,
    private val entryDao: EntryDao,
    private val tagDao: TagDao,
) : TimelineRepository {

    override fun getJournal(date: LocalDate): Flow<DailyJournal> {
        val dateStr = date.toString()
        val journalFlow = journalDao.getJournal(dateStr)
        val entriesFlow = getEntries(date)

        return combine(journalFlow, entriesFlow) { entity, entries ->
            entity?.toDomain(entries) ?: DailyJournal(date = date, entries = entries)
        }
    }

    override fun getJournals(range: ClosedRange<LocalDate>): Flow<List<DailyJournal>> {
        val from = range.start.toString()
        val to = range.endInclusive.toString()
        return journalDao.getJournals(from, to).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEntries(date: LocalDate): Flow<List<TimelineEntry>> {
        val dateStr = date.toString()
        return entryDao.getEntries(dateStr).map { entities ->
            entities.map { entity ->
                // Load tags for each entry — for a flow-based approach,
                // we load tags on demand since Room doesn't support relations with Flow easily.
                // In practice, a single query join would be better but this works for now.
                entity.toDomain()
            }
        }
    }

    override suspend fun addEntry(entry: TimelineEntry): String {
        val entity = entry.toEntity()
        entryDao.insert(entity)
        journalDao.refreshEntryCount(entry.date.toString())
        return entity.id
    }

    override suspend fun updateTags(entryId: String, tags: List<Tag>) {
        tagDao.deleteByEntry(entryId)
        tagDao.insertAll(tags.map { it.toEntity(entryId) })
    }

    override suspend fun setMood(date: LocalDate, mood: String) {
        journalDao.updateMood(date.toString(), mood)
    }

    override suspend fun toggleStar(entryId: String) {
        val entry = entryDao.getById(entryId) ?: return
        entryDao.updateStarred(entryId, !entry.isStarred)
    }
}
