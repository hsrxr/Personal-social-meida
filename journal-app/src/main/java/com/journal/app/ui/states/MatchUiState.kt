package com.journal.app.ui.states

import com.journal.app.data.model.MatchCard

data class MatchUiState(
    val isLoading: Boolean = true,
    val dailyMatches: List<MatchCard> = emptyList(),
    val hasMore: Boolean = false,
)
