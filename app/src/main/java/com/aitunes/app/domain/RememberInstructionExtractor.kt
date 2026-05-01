package com.aitunes.app.domain

/**
 * Detecta instrucciones del tipo "recuerda que..." y extrae el hecho a almacenar.
 */
object RememberInstructionExtractor {

    private val pattern = Regex(
        pattern = """(?i)(?:recuerda|recuerde|memoriza|apunta|no olvides|que sepas|ten presente|guarda|anota)\s+(?:que\s+)?(.{3,800})""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )

    fun isRememberInstruction(text: String): Boolean = pattern.containsMatchIn(text.trim())

    fun extractFact(text: String): String? {
        val match = pattern.find(text.trim()) ?: return null
        return match.groupValues[1]
            .trim()
            .trimEnd('.', '!', '?')
    }
}
