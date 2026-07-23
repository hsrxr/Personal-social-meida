package com.journal.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.ui.states.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadToday()
    }

    fun loadToday() {
        val today = LocalDate.now()
        viewModelScope.launch {
            timelineRepository.getJournal(today).collect { journal ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        date = today,
                        entries = journal.entries,
                        summaryPreview = journal.summary,
                        mood = journal.mood,
                    )
                }
            }
        }
    }

    fun toggleStar(entryId: String) {
        viewModelScope.launch {
            timelineRepository.toggleStar(entryId)
        }
    }
}
