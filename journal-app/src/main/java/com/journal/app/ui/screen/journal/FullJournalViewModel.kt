package com.journal.app.ui.screen.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.ui.states.FullJournalUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FullJournalViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    companion object {
        private const val HISTORY_WINDOW_DAYS = 365L
    }

    private val _uiState = MutableStateFlow(FullJournalUiState())
    val uiState: StateFlow<FullJournalUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        val range = today.minusDays(HISTORY_WINDOW_DAYS)..today
        viewModelScope.launch {
            timelineRepository.getJournalsWithEntries(range).collect { journals ->
                _uiState.update { it.copy(isLoading = false, journals = journals) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
