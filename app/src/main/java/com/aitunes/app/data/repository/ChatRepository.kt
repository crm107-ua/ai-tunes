package com.aitunes.app.data.repository

import com.aitunes.app.data.local.AiTunesDatabase
import com.aitunes.app.data.local.NuclearMemoryCache
import com.aitunes.app.data.local.entity.EpisodicMemoryEntity
import com.aitunes.app.data.local.entity.SemanticMemoryEntity
import com.aitunes.app.domain.RememberInstructionExtractor
import com.aitunes.app.domain.model.ChatMessage
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.utils.ContextTruncator
import com.aitunes.app.utils.DeviceProfiler
import com.aitunes.app.utils.FtsQueryBuilder

private const val LONG_TERM_EPISODIC_MS = 30L * 24L * 60L * 60L * 1000L

data class RagAssemblyResult(
    val contextText: String,
    /** True si algún fragmento episódico recuperado supera ~30 días de antigüedad. */
    val hitLongTermEpisodic: Boolean
)

/**
 * Persistencia híbrida (FTS + hechos) con RAG acotado por [SectorId].
 */
class ChatRepository(
    private val database: AiTunesDatabase,
    private val deviceProfiler: DeviceProfiler,
    private val nuclearMemoryCache: NuclearMemoryCache
) {

    private val episodicDao get() = database.episodicMemoryDao()
    private val semanticDao get() = database.semanticMemoryDao()

    /**
     * Ingesta: episodio etiquetado con el sector actual (capa 1).
     */
    suspend fun saveMessage(message: ChatMessage, sector: SectorId) {
        val row = EpisodicMemoryEntity(
            messageId = message.id,
            content = message.content,
            timestamp = message.timestamp,
            sender = message.sender.name,
            category = sector.episodicCategoryKey
        )
        episodicDao.insert(row)

        if (RememberInstructionExtractor.isRememberInstruction(message.content)) {
            val fact = RememberInstructionExtractor.extractFact(message.content) ?: return
            semanticDao.insert(
                SemanticMemoryEntity(
                    entity = "user",
                    relation = "recordatorio",
                    value = fact,
                    category = sector.episodicCategoryKey,
                    timestamp = message.timestamp,
                    embeddingBlob = null
                )
            )
        }
    }

    /**
     * Tras la respuesta del asistente: si contiene instrucción tipo "recuerda que…",
     * sube el hecho a la capa semántica sin esperar al worker.
     */
    suspend fun ingestSemanticFromAssistantOutput(
        assistantText: String,
        sector: SectorId,
        timestamp: Long
    ) {
        if (!RememberInstructionExtractor.isRememberInstruction(assistantText)) return
        val fact = RememberInstructionExtractor.extractFact(assistantText) ?: return
        semanticDao.insert(
            SemanticMemoryEntity(
                entity = "assistant",
                relation = "extraido_respuesta",
                value = fact,
                category = sector.episodicCategoryKey,
                timestamp = timestamp,
                embeddingBlob = null
            )
        )
    }

    /**
     * RAG por sector: nuclear → FTS episódico filtrado por sector → hechos del mismo sector.
     */
    suspend fun getContextForQuery(query: String, sector: SectorId): RagAssemblyResult {
        val budget = deviceProfiler.searchBudget()
        nuclearMemoryCache.ensureWarm()
        val nuclearSnapshot = nuclearMemoryCache.snapshotBlocking()

        val vitals = formatNuclearBlock(nuclearSnapshot)
        val ftsQuery = FtsQueryBuilder.fromUserQuery(query)
        val now = System.currentTimeMillis()

        val episodicMatches: List<EpisodicMemoryEntity> = when {
            ftsQuery != null -> episodicDao.searchFtsForSector(
                ftsQuery,
                budget.ftsMatchLimit,
                sector.episodicCategoryKey
            )
            else -> episodicDao.recentForSector(
                sector.episodicCategoryKey,
                budget.ftsMatchLimit.coerceAtMost(8)
            )
        }

        val hitLongTerm = episodicMatches.any { now - it.timestamp > LONG_TERM_EPISODIC_MS }

        val semanticFacts = semanticDao.findByCategory(
            sector.episodicCategoryKey,
            budget.semanticFactLimit
        )

        val messagesBlock = formatEpisodicBlock(episodicMatches, budget.maxEpisodicSnippetChars)
        val factsBlock = formatSemanticBlock(semanticFacts)

        val assembled = buildString {
            if (vitals.isNotBlank()) {
                append(vitals)
            }
            if (messagesBlock.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("--- Mensajes del sector ${sector.displayLabel} ---\n")
                append(messagesBlock)
            }
            if (factsBlock.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("--- Hechos del sector ${sector.displayLabel} ---\n")
                append(factsBlock)
            }
        }

        val text = ContextTruncator.truncateToMaxTokens(assembled.ifBlank { "" }, maxTokens = 800)
        return RagAssemblyResult(contextText = text, hitLongTermEpisodic = hitLongTerm)
    }

    private fun formatNuclearBlock(entries: Map<String, String>): String {
        if (entries.isEmpty()) return ""

        val priorityKeys = listOf(
            "NOMBRE", "nombre",
            "ALERGIAS", "alergias", "ALERGIA", "alergia",
            "MEDICACION", "medicacion", "MEDICACIÓN", "medicación",
            "CONTACTO_EMERGENCIA", "contacto_emergencia"
        )

        val orderedKeys = LinkedHashSet<String>()
        for (p in priorityKeys) {
            if (entries.containsKey(p)) orderedKeys.add(p)
        }
        entries.keys.sorted().forEach { k -> orderedKeys.add(k) }

        return buildString {
            append("--- Datos vitales / identidad ---\n")
            for (key in orderedKeys) {
                val value = entries[key] ?: continue
                append(key)
                append(": ")
                append(value)
                append('\n')
            }
        }.trimEnd()
    }

    private fun formatEpisodicBlock(
        rows: List<EpisodicMemoryEntity>,
        maxSnippetChars: Int
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            rows.forEach { r ->
                val snippet = if (r.content.length > maxSnippetChars) {
                    r.content.take(maxSnippetChars).trimEnd() + "…"
                } else {
                    r.content
                }
                append('[')
                append(r.sender)
                append("] ")
                append(snippet)
                append('\n')
            }
        }.trimEnd()
    }

    private fun formatSemanticBlock(rows: List<SemanticMemoryEntity>): String {
        if (rows.isEmpty()) return ""
        return buildString {
            rows.forEach { f ->
                append(f.entity)
                append(' ')
                append(f.relation)
                append(": ")
                append(f.value)
                append(" (")
                append(f.category)
                append(')')
                append('\n')
            }
        }.trimEnd()
    }
}
