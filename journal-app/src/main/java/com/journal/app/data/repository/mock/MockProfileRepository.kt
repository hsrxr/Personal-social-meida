package com.journal.app.data.repository.mock

import com.journal.app.data.model.UserProfile
import com.journal.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockProfileRepository @Inject constructor() : ProfileRepository {

    private val profile = MutableStateFlow(
        UserProfile(
            handle = "@JoyReader",
            tagline = "Capturing life, finding echoes.",
            avatarUrl = "https://i.pravatar.cc/150?img=47",
            entriesCount = 245,
            notesCount = 120,
            matchedFriendsCount = 58,
        ),
    )

    override fun getProfile(): Flow<UserProfile> = profile.asStateFlow()
}
