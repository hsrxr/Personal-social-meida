package com.journal.app.ui.screen.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.ui.states.JournalDayUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class JournalDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val dateStr: String = savedStateHandle["date"] ?: LocalDate.now().toString()

    private val _uiState = MutableStateFlow(JournalDayUiState(date = LocalDate.parse(dateStr)))
    val uiState: StateFlow<JournalDayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            timelineRepository.getEntries(LocalDate.parse(dateStr)).collect { entries ->
                _uiState.update { it.copy(isLoading = false, entries = entries) }
            }
        }
    }
}
