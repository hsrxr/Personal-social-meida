package com.journal.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_entries",
    indices = [
        Index("date"),
        Index("timestamp"),
    ],
)
data class TimelineEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val timestamp: Long,
    val type: String,
    val source: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val thumbnailPath: String? = null,
    val durationMs: Int? = null,
    val transcription: String? = null,
    val noteText: String? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val aiAnnotation: String? = null,
    val isStarred: Boolean = false,
    val syncStatus: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
)
