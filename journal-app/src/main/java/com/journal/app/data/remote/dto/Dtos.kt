package com.journal.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Auth ──

data class AuthRequest(
    @SerializedName("phone_hash") val phoneHash: String,
    @SerializedName("code") val code: String,
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("expires_in") val expiresIn: Long,
)

// ── Journal ──

data class JournalResponse(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("summary") val summary: String?,
    @SerializedName("keywords") val keywords: List<String>?,
    @SerializedName("mood") val mood: String?,
    @SerializedName("entry_count") val entryCount: Int,
    @SerializedName("entries") val entries: List<EntryResponse>?,
)

data class EntryResponse(
    @SerializedName("id") val id: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("type") val type: String,
    @SerializedName("source") val source: String?,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("duration_ms") val durationMs: Int?,
    @SerializedName("transcription") val transcription: String?,
    @SerializedName("location_name") val locationName: String?,
    @SerializedName("annotations") val annotations: Map<String, Any>?,
    @SerializedName("tags") val tags: List<TagDto>?,
    @SerializedName("is_starred") val isStarred: Boolean,
)

data class TagDto(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
)

// ── Match ──

data class MatchResponse(
    @SerializedName("id") val id: String,
    @SerializedName("matched_user_id") val matchedUserId: String,
    @SerializedName("matched_user_nickname") val matchedUserNickname: String,
    @SerializedName("match_date") val matchDate: String,
    @SerializedName("common_details") val commonDetails: List<CommonDetailDto>?,
    @SerializedName("ice_break_message") val iceBreakMessage: String,
    @SerializedName("status") val status: String,
)

data class CommonDetailDto(
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String,
)

data class IceBreakRequest(
    @SerializedName("content") val content: String,
    @SerializedName("round") val round: Int,
)

// ── Sync ──

data class SyncPayload(
    @SerializedName("entries") val entries: List<SyncEntryDto>,
    @SerializedName("last_sync_at") val lastSyncAt: Long,
)

data class SyncEntryDto(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("type") val type: String,
    @SerializedName("source") val source: String,
    @SerializedName("transcription") val transcription: String?,
    @SerializedName("note_text") val noteText: String?,
    @SerializedName("location_name") val locationName: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("is_starred") val isStarred: Boolean,
    @SerializedName("tags") val tags: List<TagDto>?,
)
