package com.aitunes.app.utils

/**
 * Trunca por tokens (palabras separadas por espacio) para aproximar el límite del LLM.
 */
object ContextTruncator {

    fun truncateToMaxTokens(text: String, maxTokens: Int = 800): String {
        if (text.isBlank()) return text
        val parts = text.trim().split(Regex("\\s+"))
        if (parts.size <= maxTokens) return text.trim()
        return parts.take(maxTokens).joinToString(" ")
    }
}
