package com.journal.app.ui.states

import com.journal.app.data.model.MyPost
import com.journal.app.data.model.SimilarResults

data class FindSimilarUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val myPosts: List<MyPost> = emptyList(),
    val selectedPost: MyPost? = null,
    val results: SimilarResults? = null,
    val showPostPicker: Boolean = false,
)
