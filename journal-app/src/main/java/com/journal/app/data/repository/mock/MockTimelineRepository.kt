package com.journal.app.data.repository.mock

import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.Tag
import com.journal.app.data.model.TagType
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.repository.TimelineRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockTimelineRepository @Inject constructor() : TimelineRepository {

    private val journals = MutableStateFlow(generateMockData())

    override fun getJournal(date: LocalDate): Flow<DailyJournal> {
        return journals.map { list ->
            list.find { it.date == date } ?: DailyJournal(date = date)
        }
    }

    override fun getJournals(range: ClosedRange<LocalDate>): Flow<List<DailyJournal>> {
        return journals.map { list ->
            list.filter { it.date in range }
        }
    }

    override fun getEntries(date: LocalDate): Flow<List<TimelineEntry>> {
        return journals.map { list ->
            list.find { it.date == date }?.entries ?: emptyList()
        }
    }

    override suspend fun getEntry(entryId: String): TimelineEntry? =
        journals.value
            .asSequence()
            .flatMap { it.entries.asSequence() }
            .firstOrNull { it.id == entryId }

    override suspend fun addEntry(entry: TimelineEntry): String {
        delay(300) // simulate storage
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val saved = entry.copy(id = id)
        journals.value = journals.value.map { journal ->
            if (journal.date == entry.date) {
                journal.copy(
                    entries = journal.entries + saved,
                    entryCount = journal.entryCount + 1,
                )
            } else {
                journal
            }
        }
        return id
    }

    override suspend fun updateTags(entryId: String, tags: List<Tag>) {
        delay(100)
        journals.value = journals.value.map { journal ->
            journal.copy(entries = journal.entries.map { entry ->
                if (entry.id == entryId) entry.copy(tags = tags) else entry
            })
        }
    }

    override suspend fun setMood(date: LocalDate, mood: String) {
        delay(100)
        journals.value = journals.value.map { journal ->
            if (journal.date == date) journal.copy(mood = mood) else journal
        }
    }

    override suspend fun toggleStar(entryId: String) {
        delay(50)
        journals.value = journals.value.map { journal ->
            journal.copy(entries = journal.entries.map { entry ->
                if (entry.id == entryId) entry.copy(isStarred = !entry.isStarred) else entry
            })
        }
    }

    companion object {
        fun generateMockData(): List<DailyJournal> {
            val today = LocalDate.now()
            return (-7..0).map { offset ->
                val date = today.plusDays(offset.toLong())
                DailyJournal(
                    date = date,
                    entries = if (offset >= -2) generateEntries(date) else emptyList(),
                    summary = if (offset == 0) "今天又是平凡但有趣的一天。上午在望京SOHO处理工作，中午路过楼下的咖啡店，闻到了今年第一缕桂花香。下午在公园跑了5公里。晚上和朋友约了火锅。" else null,
                    keywords = if (offset == 0) listOf("咖啡", "望京", "桂花", "平静", "工作") else emptyList(),
                    mood = if (offset == 0) "平静" else null,
                    entryCount = if (offset >= -2) 4 else 0,
                )
            }
        }

        private fun generateEntries(date: LocalDate): List<TimelineEntry> {
            val baseTime = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return listOf(
                TimelineEntry(
                    id = "entry-${date}-1",
                    date = date,
                    timestamp = baseTime + 3 * 60 * 60 * 1000, // 11:00
                    type = EntryType.PHOTO,
                    imageUrl = "https://picsum.photos/seed/${date}1/800/600",
                    thumbnailUrl = "https://picsum.photos/seed/${date}1/200/150",
                    locationName = "望京SOHO",
                    tags = listOf(Tag(name = "望京", type = TagType.LOCATION)),
                ),
                TimelineEntry(
                    id = "entry-${date}-2",
                    date = date,
                    timestamp = baseTime + 4 * 60 * 60 * 1000 + 18 * 60 * 1000, // 12:18
                    type = EntryType.AUDIO,
                    transcription = "路过咖啡店闻到桂花香，秋天真的来了。买了杯桂花拿铁，很好喝。",
                    durationMs = 12000,
                    locationName = "望京咖啡店",
                    tags = listOf(
                        Tag(name = "咖啡", type = TagType.ACTIVITY),
                        Tag(name = "桂花", type = TagType.AI_SUGGESTED),
                    ),
                ),
                TimelineEntry(
                    id = "entry-${date}-3",
                    date = date,
                    timestamp = baseTime + 4 * 60 * 60 * 1000 + 25 * 60 * 1000, // 12:25
                    type = EntryType.MOMENT_MARK,
                    imageUrl = "https://picsum.photos/seed/${date}3/800/600",
                    thumbnailUrl = "https://picsum.photos/seed/${date}3/200/150",
                    noteText = "桂花拿铁意外地好喝",
                    locationName = "望京咖啡店",
                    isStarred = true,
                    tags = listOf(
                        Tag(name = "重要时刻", type = TagType.MOOD),
                        Tag(name = "桂花", type = TagType.AI_SUGGESTED),
                        Tag(name = "探店", type = TagType.ACTIVITY),
                    ),
                ),
                TimelineEntry(
                    id = "entry-${date}-4",
                    date = date,
                    timestamp = baseTime + 6 * 60 * 60 * 1000, // 14:00
                    type = EntryType.AGENT_DIALOG,
                    transcription = "今天聊了周末计划，想去爬山。提到了上次去香山的经历，觉得秋天是最适合爬山的季节。",
                    locationName = "望京SOHO",
                ),
            )
        }
    }
}
