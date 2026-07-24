package com.journal.app.ui.screen.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.repository.MessagesRepository
import com.journal.app.ui.states.MessagesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messagesRepository.getConversations().collect { conversations ->
                _uiState.update { it.copy(isLoading = false, conversations = conversations) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
