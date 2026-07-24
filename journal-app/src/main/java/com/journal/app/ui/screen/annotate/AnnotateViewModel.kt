package com.journal.app.ui.screen.annotate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnnotateUiState(
    val isLoading: Boolean = true,
    val entry: TimelineEntry? = null,
)

@HiltViewModel
class AnnotateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val entryId: String? = savedStateHandle["entryId"]

    private val _uiState = MutableStateFlow(AnnotateUiState())
    val uiState: StateFlow<AnnotateUiState> = _uiState.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        val id = entryId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val entry = timelineRepository.getEntry(id)
            _uiState.update { it.copy(isLoading = false, entry = entry) }
        }
    }
}
