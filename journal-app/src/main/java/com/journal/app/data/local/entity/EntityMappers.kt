package com.journal.app.data.local.entity

import com.journal.app.data.model.CommonDetail
import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.MatchCard
import com.journal.app.data.model.MatchStatus
import com.journal.app.data.model.Tag
import com.journal.app.data.model.TagType
import com.journal.app.data.model.TimelineEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

private val gson = Gson()
private val stringListType = object : TypeToken<List<String>>() {}.type
private val commonDetailListType = object : TypeToken<List<CommonDetail>>() {}.type

// ── TimelineEntry <-> TimelineEntryEntity ──

fun TimelineEntry.toEntity(): TimelineEntryEntity = TimelineEntryEntity(
    id = id,
    date = date.toString(),
    timestamp = timestamp,
    type = type.name,
    source = "PHONE",
    localPath = imageUrl,
    thumbnailPath = thumbnailUrl,
    durationMs = durationMs,
    transcription = transcription,
    noteText = noteText,
    locationName = locationName,
    isStarred = isStarred,
)

fun TimelineEntryEntity.toDomain(tags: List<Tag> = emptyList()): TimelineEntry = TimelineEntry(
    id = id,
    date = LocalDate.parse(date),
    timestamp = timestamp,
    type = EntryType.valueOf(type),
    imageUrl = localPath,
    thumbnailUrl = thumbnailPath,
    transcription = transcription,
    noteText = noteText,
    locationName = locationName,
    durationMs = durationMs,
    isStarred = isStarred,
    tags = tags,
)

// ── DailyJournal <-> DailyJournalEntity ──

fun DailyJournalEntity.toDomain(entries: List<TimelineEntry> = emptyList()): DailyJournal {
    val keywordList: List<String> = keywords?.let {
        runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }.getOrNull()
    } ?: emptyList()
    return DailyJournal(
        date = LocalDate.parse(date),
        entries = entries,
        summary = summary,
        keywords = keywordList,
        mood = mood,
        entryCount = entryCount,
    )
}

fun DailyJournal.toEntity(): DailyJournalEntity = DailyJournalEntity(
    date = date.toString(),
    summary = summary,
    keywords = gson.toJson(keywords),
    mood = mood,
    entryCount = entryCount,
    lastModified = System.currentTimeMillis(),
)

// ── Tag <-> TagEntity ──

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    type = runCatching { TagType.valueOf(type) }.getOrDefault(TagType.CUSTOM),
)

fun Tag.toEntity(entryId: String): TagEntity = TagEntity(
    id = id,
    entryId = entryId,
    name = name,
    type = type.name,
)

// ── MatchEntity <-> MatchCard ──

fun MatchEntity.toDomain(): MatchCard {
    val details: List<CommonDetail> = commonDetailsJson?.let {
        runCatching { gson.fromJson<List<CommonDetail>>(it, object : TypeToken<List<CommonDetail>>() {}.type) }.getOrNull()
    } ?: emptyList()
    return MatchCard(
        id = id,
        matchedUserId = matchedUserId,
        matchedUserNickname = matchedUserNickname,
        matchDate = LocalDate.parse(matchDate),
        commonDetails = details,
        iceBreakMessage = iceBreakMessage,
        status = runCatching { MatchStatus.valueOf(status) }.getOrDefault(MatchStatus.PENDING),
    )
}

fun MatchCard.toEntity(): MatchEntity = MatchEntity(
    id = id,
    matchedUserId = matchedUserId,
    matchedUserNickname = matchedUserNickname,
    matchDate = matchDate.toString(),
    commonDetailsJson = gson.toJson(commonDetails),
    iceBreakMessage = iceBreakMessage,
    status = status.name,
)
