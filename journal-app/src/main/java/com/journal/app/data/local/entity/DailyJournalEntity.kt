package com.journal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_journals")
data class DailyJournalEntity(
    @PrimaryKey val date: String,
    val summary: String? = null,
    val keywords: String? = null,
    val mood: String? = null,
    val entryCount: Int = 0,
    val lastModified: Long = 0,
)
