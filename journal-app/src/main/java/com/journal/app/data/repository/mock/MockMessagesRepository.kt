package com.journal.app.data.repository.mock

import com.journal.app.data.model.Conversation
import com.journal.app.data.model.MessageType
import com.journal.app.data.repository.MessagesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockMessagesRepository @Inject constructor() : MessagesRepository {

    private val conversations = MutableStateFlow(generateMockConversations())

    override fun getConversations(): Flow<List<Conversation>> = conversations.asStateFlow()

    companion object {
        private const val ONE_MINUTE = 60_000L

        fun generateMockConversations(): List<Conversation> {
            val now = System.currentTimeMillis()
            return listOf(
                Conversation(
                    id = "conv-1",
                    contactName = "Ethan",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=12",
                    lastMessagePreview = "You also run in the mornings? We should compare routes!",
                    lastMessageType = MessageType.TEXT,
                    timestamp = now - 15 * ONE_MINUTE,
                    unreadCount = 2,
                ),
                Conversation(
                    id = "conv-2",
                    contactName = "Mia",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=32",
                    lastMessagePreview = "Voice message",
                    lastMessageType = MessageType.VOICE,
                    timestamp = now - 2 * 60 * ONE_MINUTE,
                    unreadCount = 0,
                ),
                Conversation(
                    id = "conv-3",
                    contactName = "Noah",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=51",
                    lastMessagePreview = "Congrats on wrapping the project 🎉",
                    lastMessageType = MessageType.TEXT,
                    timestamp = now - 26 * 60 * ONE_MINUTE,
                    unreadCount = 0,
                ),
            )
        }
    }
}
