package com.aitunes.app.domain

import com.aitunes.app.domain.model.MessageCategory
import com.aitunes.app.domain.model.SectorId

/**
 * System prompts por sector y contexto RAG + identidad nuclear.
 */
class SystemPromptBuilder {

    fun buildForSector(
        sector: SectorId,
        ragContext: String,
        nuclearIdentity: Map<String, String>
    ): String {
        val identityBlock = formatIdentity(nuclearIdentity)
        val ctx = ragContext.trim().ifBlank { "(sin contexto recuperado del sector)" }

        return when (sector) {
            SectorId.SALUD -> buildString {
                append("Eres un asistente médico local (offline). No eres médico: sin diagnósticos ni sustituto ")
                append("de atención profesional. Usa estos hechos del historial del usuario:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad y datos críticos:\n")
                    append(identityBlock)
                }
            }

            SectorId.FINANZAS -> buildString {
                append("Eres un asistente financiero local. Información general y educativa; sin asesoramiento ")
                append("personalizado de inversión. Contexto del sector Finanzas:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad:\n")
                    append(identityBlock)
                }
            }

            SectorId.TRABAJO -> buildString {
                append("Eres un asistente de productividad y trabajo (on-device). Contexto:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad:\n")
                    append(identityBlock)
                }
            }

            SectorId.LEGAL -> buildString {
                append("Eres un orientador legal general offline. No eres abogado: sin opiniones definitivas ")
                append("sobre casos concretos. Contexto legal recuperado:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad:\n")
                    append(identityBlock)
                }
            }

            SectorId.CREATIVO -> buildString {
                append("Eres un asistente creativo local. Inspira y propón ideas con el contexto:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad:\n")
                    append(identityBlock)
                }
            }

            SectorId.GENERAL -> buildString {
                append("Eres ai-tunes, una IA offline. Contexto:\n\n")
                append(ctx)
                if (identityBlock.isNotBlank()) {
                    append("\n\nIdentidad del usuario:\n")
                    append(identityBlock)
                }
            }
        }
    }

    fun build(
        category: MessageCategory,
        ragContext: String,
        nuclearIdentity: Map<String, String>
    ): String {
        val sector = when (category) {
            MessageCategory.SALUD -> SectorId.SALUD
            MessageCategory.FINANZAS -> SectorId.FINANZAS
            MessageCategory.TRABAJO -> SectorId.TRABAJO
            MessageCategory.GENERAL -> SectorId.GENERAL
        }
        return buildForSector(sector, ragContext, nuclearIdentity)
    }

    private fun formatIdentity(map: Map<String, String>): String {
        if (map.isEmpty()) return ""
        return map.entries.joinToString("\n") { (k, v) -> "$k: $v" }
    }
}
