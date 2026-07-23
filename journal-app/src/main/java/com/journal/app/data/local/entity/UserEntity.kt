package com.journal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val phoneHash: String? = null,
    val token: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
)
