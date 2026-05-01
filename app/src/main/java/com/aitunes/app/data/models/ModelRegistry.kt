package com.aitunes.app.data.models

import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.utils.DeviceTier

/**
 * Catálogo de GGUF recomendados por [DeviceTier] (descarga desde HuggingFace).
 *
 * Nota: modelos **multimodales** tipo MobileVLM (imagen+texto) requieren pipeline visión en llama.cpp;
 * esta app solo carga GGUF de **texto** compatibles con el puente actual.
 */
data class RegisteredModel(
    val id: String,
    val displayName: String,
    val description: String,
    /** URL directa `.../resolve/main/archivo.gguf`. */
    val huggingFaceDownloadUrl: String,
    val fileName: String,
    val quantLabel: String,
    val approximateSizeBytes: Long,
    val recommendedTiers: Set<DeviceTier>
)

object ModelRegistry {

    /** ~669 MB · rápido en cualquier móvil */
    private val tinyLlamaQ4 = RegisteredModel(
        id = "tinyllama-1.1b-q4km",
        displayName = "TinyLlama 1.1B Q4",
        description = "Funciona en casi cualquier dispositivo. Respuestas cortas típicas ~2–4 s. Ideal sin internet tras descargar.",
        huggingFaceDownloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        quantLabel = "Q4_K_M",
        approximateSizeBytes = 669L * 1024L * 1024L,
        recommendedTiers = setOf(DeviceTier.NANO, DeviceTier.SMALL, DeviceTier.MEDIUM, DeviceTier.FULL)
    )

    /** ~1.5 GB · Llama 3.2 instruct, muy rápido en NPU/GPU si el SoC lo acelera */
    private val llama32_1bQ4 = RegisteredModel(
        id = "llama-3.2-1b-instruct-q4km",
        displayName = "Llama 3.2 1B Instruct",
        description = "Ultra rápido en muchos móviles; buena calidad para el tamaño. Formato chat instruct.",
        huggingFaceDownloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        quantLabel = "Q4_K_M",
        approximateSizeBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong(),
        recommendedTiers = setOf(DeviceTier.SMALL, DeviceTier.MEDIUM, DeviceTier.FULL)
    )

    /** ~1.8 GB · mejor calidad/velocidad en gama media-alta */
    private val phi3MiniQ4 = RegisteredModel(
        id = "phi3-mini-q4",
        displayName = "Phi-3 Mini 4K Q4",
        description = "Buena relación calidad/velocidad; respuestas típicas ~3–8 s según dispositivo. Muy usado en móvil con NPU.",
        huggingFaceDownloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
        fileName = "Phi-3-mini-4k-instruct-q4.gguf",
        quantLabel = "Q4",
        approximateSizeBytes = (1.8 * 1024.0 * 1024.0 * 1024.0).toLong(),
        recommendedTiers = setOf(DeviceTier.MEDIUM, DeviceTier.FULL)
    )

    /** ~120 MB · sólo dispositivos muy justos de RAM */
    private val smolLm135Q2 = RegisteredModel(
        id = "smollm-135m-q2k",
        displayName = "SmolLM-135M Q2",
        description = "Ultraligero si no cabe TinyLlama; calidad muy limitada.",
        huggingFaceDownloadUrl = "https://huggingface.co/QuantFactory/SmolLM-135M-GGUF/resolve/main/SmolLM-135M.Q2_K.gguf",
        fileName = "SmolLM-135M.Q2_K.gguf",
        quantLabel = "Q2_K",
        approximateSizeBytes = 120L * 1024L * 1024L,
        recommendedTiers = setOf(DeviceTier.NANO)
    )

    private val gemma2bQ4 = RegisteredModel(
        id = "gemma-2b-q4km",
        displayName = "Gemma 2B IT Q4",
        description = "Alternativa instruct compacta (Google). Útil si Phi-3 es pesado.",
        huggingFaceDownloadUrl = "https://huggingface.co/bartowski/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-Q4_K_M.gguf",
        fileName = "gemma-2b-it-Q4_K_M.gguf",
        quantLabel = "Q4_K_M",
        approximateSizeBytes = (1.7 * 1024.0 * 1024.0 * 1024.0).toLong(),
        recommendedTiers = setOf(DeviceTier.MEDIUM, DeviceTier.FULL)
    )

    private val gemma2bQ8 = RegisteredModel(
        id = "gemma-2b-q8",
        displayName = "Gemma 2B IT Q8",
        description = "Mayor fidelidad; sólo si tienes RAM de sobra.",
        huggingFaceDownloadUrl = "https://huggingface.co/bartowski/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-Q8_0.gguf",
        fileName = "gemma-2b-it-Q8_0.gguf",
        quantLabel = "Q8_0",
        approximateSizeBytes = (2.9 * 1024.0 * 1024.0 * 1024.0).toLong(),
        recommendedTiers = setOf(DeviceTier.FULL)
    )

    /** Orden en biblioteca: recomendados móvil primero */
    val allModels: List<RegisteredModel> = listOf(
        tinyLlamaQ4,
        llama32_1bQ4,
        phi3MiniQ4,
        smolLm135Q2,
        gemma2bQ4,
        gemma2bQ8
    )

    private val byId: Map<String, RegisteredModel> = allModels.associateBy { it.id }

    private val byFileName: Map<String, RegisteredModel> = allModels.associateBy { it.fileName }

    fun findByFileName(fileName: String): RegisteredModel? = byFileName[fileName]

    val recommendedByTier: Map<DeviceTier, List<RegisteredModel>> = mapOf(
        DeviceTier.NANO to listOf(smolLm135Q2, tinyLlamaQ4),
        DeviceTier.SMALL to listOf(tinyLlamaQ4, llama32_1bQ4),
        DeviceTier.MEDIUM to listOf(tinyLlamaQ4, llama32_1bQ4, phi3MiniQ4, gemma2bQ4),
        DeviceTier.FULL to listOf(phi3MiniQ4, llama32_1bQ4, tinyLlamaQ4, gemma2bQ4, gemma2bQ8)
    )

    fun getById(id: String): RegisteredModel? = byId[id]

    fun isRecommendedFor(model: RegisteredModel, tier: DeviceTier): Boolean =
        tier in model.recommendedTiers ||
            recommendedByTier[tier].orEmpty().any { it.id == model.id }

    fun optimalModelForSector(sector: SectorId, tier: DeviceTier): RegisteredModel {
        val tierList = recommendedByTier[tier].orEmpty().ifEmpty { listOf(allModels.first()) }
        fun pickTiny() = tierList.firstOrNull { it.id == "tinyllama-1.1b-q4km" }
        fun pickLlama32() = tierList.firstOrNull { it.id == "llama-3.2-1b-instruct-q4km" }
        fun pickPhi() = tierList.firstOrNull { it.id == "phi3-mini-q4" }
        fun pickSmol() = tierList.firstOrNull { it.id == "smollm-135m-q2k" }
        fun pickGemmaQ4() = tierList.firstOrNull { it.id == "gemma-2b-q4km" }
        fun pickMobileFirst() = pickTiny() ?: pickLlama32() ?: pickPhi() ?: pickSmol() ?: tierList.first()
        fun first() = tierList.first()

        return when (sector) {
            SectorId.SALUD -> pickMobileFirst()
            SectorId.FINANZAS -> pickMobileFirst()
            SectorId.TRABAJO -> pickLlama32() ?: pickPhi() ?: pickMobileFirst()
            SectorId.LEGAL -> pickPhi() ?: pickLlama32() ?: pickMobileFirst()
            SectorId.CREATIVO -> pickGemmaQ4() ?: pickLlama32() ?: pickMobileFirst()
            SectorId.GENERAL -> first()
        }
    }

    fun intelligenceModelLabel(model: RegisteredModel, sector: SectorId): String {
        val tag = when (sector) {
            SectorId.SALUD -> "Medical"
            SectorId.FINANZAS -> "Finance"
            SectorId.TRABAJO -> "Work"
            SectorId.LEGAL -> "Legal"
            SectorId.CREATIVO -> "Creative"
            SectorId.GENERAL -> "Core"
        }
        val short = model.displayName.replace(" ", "-")
        return "$short · $tag"
    }
}
