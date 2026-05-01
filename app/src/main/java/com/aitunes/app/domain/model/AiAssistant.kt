package com.aitunes.app.domain.model

import androidx.compose.ui.graphics.Color

data class AiAssistant(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val accentColor: Color,
    val gradientColors: List<Color>,
    val modelLabel: String
)
