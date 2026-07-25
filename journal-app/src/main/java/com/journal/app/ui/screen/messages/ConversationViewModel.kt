package com.journal.app.ui.screen.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.ChatMessage
import com.journal.app.data.repository.MessagesRepository
import com.journal.app.ui.states.ConversationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagesRepository: MessagesRepository,
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""
    private val contactName: String = savedStateHandle["contactName"] ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState(contactName = contactName))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messagesRepository.getMessages(conversationId).collect { msgs ->
                _uiState.update { it.copy(isLoading = false, messages = msgs) }
            }
        }
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }
            messagesRepository.sendMessage(conversationId, text)
        }
    }
}
