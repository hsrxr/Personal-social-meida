package com.journal.app.data.repository

import com.journal.app.data.model.Conversation
import kotlinx.coroutines.flow.Flow

interface MessagesRepository {
    fun getConversations(): Flow<List<Conversation>>
}
