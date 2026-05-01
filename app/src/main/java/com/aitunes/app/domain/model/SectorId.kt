package com.aitunes.app.domain.model

/**
 * Sector de la app (orquestación multi-modelo). La clave [episodicCategoryKey] etiqueta filas en
 * EpisodicMemory y filtra RAG + hechos semánticos del mismo sector.
 */
enum class SectorId(
    val displayLabel: String,
    val episodicCategoryKey: String,
    /** Categoría base para prompts cuando no hay rama específica en [SystemPromptBuilder]. */
    val basePromptCategory: MessageCategory
) {
    SALUD("Salud", "SALUD", MessageCategory.SALUD),
    FINANZAS("Finanzas", "FINANZAS", MessageCategory.FINANZAS),
    TRABAJO("Trabajo", "TRABAJO", MessageCategory.TRABAJO),
    GENERAL("General", "GENERAL", MessageCategory.GENERAL),
    LEGAL("Legal", "LEGAL", MessageCategory.GENERAL),
    CREATIVO("Creativo", "CREATIVO", MessageCategory.GENERAL);

    companion object {
        fun fromAssistantId(assistantId: String): SectorId = when (assistantId) {
            "health" -> SALUD
            "finance" -> FINANZAS
            "legal" -> LEGAL
            "creative" -> CREATIVO
            else -> GENERAL
        }
    }
}
