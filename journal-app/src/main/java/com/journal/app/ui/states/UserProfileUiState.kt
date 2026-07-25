package com.journal.app.ui.states

import com.journal.app.data.model.UserPublicProfile

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserPublicProfile? = null,
)
