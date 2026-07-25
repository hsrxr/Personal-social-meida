package com.journal.app.data.repository.mock

import com.journal.app.data.model.ChatMessage
import com.journal.app.data.model.Conversation
import com.journal.app.data.model.MessageType
import com.journal.app.data.repository.MessagesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockMessagesRepository @Inject constructor() : MessagesRepository {

    private val conversations = MutableStateFlow(generateMockConversations())
    private val messages = MutableStateFlow(generateMockMessages())

    override fun getConversations(): Flow<List<Conversation>> = conversations.asStateFlow()

    override fun getMessages(conversationId: String): Flow<List<ChatMessage>> {
        return MutableStateFlow(
            messages.value.filter { it.senderId.startsWith(conversationId.drop(5)) || it.isMine }
        ).asStateFlow()
    }

    override suspend fun sendMessage(conversationId: String, text: String): String {
        delay(100)
        val id = UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = id,
            senderId = "me",
            senderName = "Me",
            text = text,
            timestamp = System.currentTimeMillis(),
            isMine = true,
        )
        messages.value = messages.value + msg
        // Update conversation preview
        conversations.value = conversations.value.map {
            if (it.id == conversationId) it.copy(
                lastMessagePreview = text,
                lastMessageType = MessageType.TEXT,
                timestamp = System.currentTimeMillis(),
            ) else it
        }
        return id
    }

    companion object {
        private const val ONE_MINUTE = 60_000L
        private const val ONE_HOUR = 3_600_000L

        fun generateMockConversations(): List<Conversation> {
            val now = System.currentTimeMillis()
            return listOf(
                Conversation(
                    id = "conv-user-ethan",
                    contactName = "Ethan",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=12",
                    lastMessagePreview = "You also run in the mornings? We should compare routes!",
                    lastMessageType = MessageType.TEXT,
                    timestamp = now - 15 * ONE_MINUTE,
                    unreadCount = 2,
                ),
                Conversation(
                    id = "conv-user-mia",
                    contactName = "Mia",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=32",
                    lastMessagePreview = "Voice message",
                    lastMessageType = MessageType.VOICE,
                    timestamp = now - 2 * ONE_HOUR,
                    unreadCount = 0,
                ),
                Conversation(
                    id = "conv-user-noah",
                    contactName = "Noah",
                    contactAvatarUrl = "https://i.pravatar.cc/150?img=51",
                    lastMessagePreview = "Congrats on wrapping the project 🎉",
                    lastMessageType = MessageType.TEXT,
                    timestamp = now - 26 * ONE_HOUR,
                    unreadCount = 0,
                ),
            )
        }

        fun generateMockMessages(): List<ChatMessage> {
            val now = System.currentTimeMillis()
            return listOf(
                // Ethan conversation
                ChatMessage(
                    id = "msg-1",
                    senderId = "user-ethan",
                    senderName = "Ethan",
                    text = "Hey! I saw your running post — I also love morning runs.",
                    timestamp = now - 14 * ONE_MINUTE,
                    isMine = false,
                ),
                ChatMessage(
                    id = "msg-2",
                    senderId = "user-ethan",
                    senderName = "Ethan",
                    text = "Morning run by the river. #running felt amazing today.",
                    timestamp = now - 13 * ONE_MINUTE,
                    isMine = false,
                    sharedPostText = "Morning run by the river. #running felt amazing today.",
                ),
                ChatMessage(
                    id = "msg-3",
                    senderId = "me",
                    senderName = "Me",
                    text = "Thanks! Your trail run sounds epic. Where do you usually run?",
                    timestamp = now - 12 * ONE_MINUTE,
                    isMine = true,
                ),
                ChatMessage(
                    id = "msg-4",
                    senderId = "user-ethan",
                    senderName = "Ethan",
                    text = "You also run in the mornings? We should compare routes!",
                    timestamp = now - 2 * ONE_MINUTE,
                    isMine = false,
                ),

                // Mia conversation
                ChatMessage(
                    id = "msg-5",
                    senderId = "user-mia",
                    senderName = "Mia",
                    text = "I saw we both love that corner cafe ☕",
                    timestamp = now - 2 * ONE_HOUR,
                    isMine = false,
                ),
                ChatMessage(
                    id = "msg-6",
                    senderId = "user-mia",
                    senderName = "Mia",
                    text = "Coffee and reading at the corner cafe. #coffee #reading",
                    timestamp = now - 115 * ONE_MINUTE,
                    isMine = false,
                    sharedPostText = "Coffee and reading at the corner cafe. #coffee #reading",
                ),
                ChatMessage(
                    id = "msg-7",
                    senderId = "me",
                    senderName = "Me",
                    text = "Yes! Their osmanthus latte is amazing. Have you tried it?",
                    timestamp = now - 110 * ONE_MINUTE,
                    isMine = true,
                ),

                // Noah conversation
                ChatMessage(
                    id = "msg-8",
                    senderId = "user-noah",
                    senderName = "Noah",
                    text = "Congrats on shipping that big project! 🚀",
                    timestamp = now - 26 * ONE_HOUR,
                    isMine = false,
                ),
                ChatMessage(
                    id = "msg-9",
                    senderId = "me",
                    senderName = "Me",
                    text = "Thanks Noah! Your project looked amazing too. How's the hiking going?",
                    timestamp = now - 25 * ONE_HOUR,
                    isMine = true,
                ),
                ChatMessage(
                    id = "msg-10",
                    senderId = "user-noah",
                    senderName = "Noah",
                    text = "Congrats on wrapping the project 🎉 Weekend hike to celebrate?",
                    timestamp = now - 24 * ONE_HOUR,
                    isMine = false,
                ),
            )
        }
    }
}
