package com.journal.app.data.repository

import com.journal.app.data.model.ChatMessage
import com.journal.app.data.model.Conversation
import kotlinx.coroutines.flow.Flow

interface MessagesRepository {
    fun getConversations(): Flow<List<Conversation>>

    /** Get all messages for a conversation, newest last. */
    fun getMessages(conversationId: String): Flow<List<ChatMessage>>

    /** Send a text message and return its id. */
    suspend fun sendMessage(conversationId: String, text: String): String
}
