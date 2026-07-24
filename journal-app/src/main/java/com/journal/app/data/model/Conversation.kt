package com.journal.app.data.model

enum class MessageType { TEXT, VOICE }

data class Conversation(
    val id: String,
    val contactName: String,
    val contactAvatarUrl: String?,
    val lastMessagePreview: String,
    val lastMessageType: MessageType,
    val timestamp: Long,
    val unreadCount: Int,
)
