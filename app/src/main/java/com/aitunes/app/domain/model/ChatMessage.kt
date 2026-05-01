package com.aitunes.app.domain.model

import androidx.compose.ui.graphics.Color

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO
}

enum class MessageSender {
    USER,
    AI
}

data class ChatMessage(
    val id: String,
    val content: String,
    val sender: MessageSender,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val accentColor: Color
)
