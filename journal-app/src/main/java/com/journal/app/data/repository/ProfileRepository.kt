package com.journal.app.data.repository

import com.journal.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfile(): Flow<UserProfile>
}
