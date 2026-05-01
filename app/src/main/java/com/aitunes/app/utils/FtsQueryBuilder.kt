package com.aitunes.app.utils

import java.util.Locale

/**
 * Construye expresiones FTS5 seguras (prefijos OR) a partir de texto libre del usuario.
 */
object FtsQueryBuilder {

    fun fromUserQuery(raw: String): String? {
        val tokens = raw.lowercase(Locale.getDefault())
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-záéíóúñü0-9]"), "") }
            .filter { it.length >= 2 }
            .distinct()
            .take(12)

        if (tokens.isEmpty()) return null

        return tokens.joinToString(" OR ") { token ->
            "content : $token*"
        }
    }
}
