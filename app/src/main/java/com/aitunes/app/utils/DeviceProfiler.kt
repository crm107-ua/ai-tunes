package com.aitunes.app.utils

import android.app.ActivityManager
import android.content.Context
/**
 * Perfil de hardware del dispositivo para ajustar presupuestos de búsqueda y proteger estabilidad.
 */
enum class DeviceTier {
    NANO,
    SMALL,
    MEDIUM,
    FULL
}

data class MemorySearchBudget(
    /** Máximo de filas devueltas por FTS5 en una consulta de contexto. */
    val ftsMatchLimit: Int,
    /** Máximo de hechos semánticos a incluir. */
    val semanticFactLimit: Int,
    /** Tamaño máximo aproximado por fragmento de chat antes del ensamblado final. */
    val maxEpisodicSnippetChars: Int,
    /**
     * Tamaño de contexto sugerido para el motor LLM local (tokens aprox.), alineado con el tier
     * y la capacidad de RAG ([ftsMatchLimit], [semanticFactLimit], fragmentos).
     */
    val llamaContextSize: Int,
    /** Tamaño de batch para ggml/llama.cpp según tier. */
    val llamaBatchSize: Int,
    /** Modo ahorro de memoria (p.ej. NANO): reduce ventana efectiva y mmap en nativo. */
    val lowMemoryMode: Boolean
)

class DeviceProfiler(private val context: Context) {

    fun currentTier(): DeviceTier {
        val ramMb = totalRamMegabytes()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val byRam = tierFromRam(ramMb)
        val byCpu = tierFromCpu(cores)
        val idx = minOf(byRam.ordinal, byCpu.ordinal)
        return DeviceTier.entries[idx]
    }

    fun searchBudget(tier: DeviceTier = currentTier()): MemorySearchBudget = when (tier) {
        DeviceTier.NANO -> MemorySearchBudget(
            ftsMatchLimit = 4,
            semanticFactLimit = 6,
            maxEpisodicSnippetChars = 220,
            llamaContextSize = 512,
            llamaBatchSize = 64,
            lowMemoryMode = true
        )
        DeviceTier.SMALL -> MemorySearchBudget(
            ftsMatchLimit = 8,
            semanticFactLimit = 10,
            maxEpisodicSnippetChars = 320,
            llamaContextSize = 768,
            llamaBatchSize = 96,
            lowMemoryMode = true
        )
        DeviceTier.MEDIUM -> MemorySearchBudget(
            ftsMatchLimit = 14,
            semanticFactLimit = 16,
            maxEpisodicSnippetChars = 420,
            llamaContextSize = 1024,
            llamaBatchSize = 128,
            lowMemoryMode = true
        )
        DeviceTier.FULL -> MemorySearchBudget(
            ftsMatchLimit = 22,
            semanticFactLimit = 28,
            maxEpisodicSnippetChars = 560,
            llamaContextSize = 1536,
            llamaBatchSize = 192,
            lowMemoryMode = true
        )
    }

    private fun totalRamMegabytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024L * 1024L)
    }

    private fun tierFromRam(ramMb: Long): DeviceTier = when {
        ramMb < 1_800L -> DeviceTier.NANO
        ramMb < 3_500L -> DeviceTier.SMALL
        ramMb < 7_000L -> DeviceTier.MEDIUM
        else -> DeviceTier.FULL
    }

    private fun tierFromCpu(cores: Int): DeviceTier = when {
        cores <= 2 -> DeviceTier.NANO
        cores <= 4 -> DeviceTier.SMALL
        cores <= 6 -> DeviceTier.MEDIUM
        else -> DeviceTier.FULL
    }
}
