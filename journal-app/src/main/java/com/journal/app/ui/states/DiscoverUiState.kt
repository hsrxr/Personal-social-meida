package com.journal.app.ui.states

import com.journal.app.data.model.FeedPost

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val posts: List<FeedPost> = emptyList(),
    val selectedPost: FeedPost? = null,
    val sayHiConfirmation: String? = null,
)
