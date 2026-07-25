package com.journal.app.ui.states

import com.journal.app.data.model.ChatMessage

data class ConversationUiState(
    val isLoading: Boolean = true,
    val contactName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
)
