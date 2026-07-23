package com.journal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val matchedUserId: String,
    val matchedUserNickname: String,
    val matchDate: String,
    val commonDetailsJson: String? = null,
    val iceBreakMessage: String = "",
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
)
