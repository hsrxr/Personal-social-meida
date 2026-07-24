package com.journal.app.ui.states

import com.journal.app.data.model.Conversation

data class MessagesUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val conversations: List<Conversation> = emptyList(),
) {
    val filteredConversations: List<Conversation>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return conversations
            return conversations.filter { it.contactName.contains(q, ignoreCase = true) }
        }
}
