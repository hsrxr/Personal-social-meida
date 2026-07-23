package com.journal.app.ui.screen.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.MatchCard
import com.journal.app.data.repository.MatchRepository
import com.journal.app.ui.states.MatchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            matchRepository.getDailyMatches().collect { matches ->
                _uiState.update { it.copy(isLoading = false, dailyMatches = matches) }
            }
        }
    }

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            matchRepository.acceptMatch(matchId)
        }
    }

    fun skipMatch(matchId: String) {
        viewModelScope.launch {
            matchRepository.skipMatch(matchId)
        }
    }
}
