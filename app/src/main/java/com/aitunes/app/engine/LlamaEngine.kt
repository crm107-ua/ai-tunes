package com.aitunes.app.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.aitunes.app.data.local.NuclearMemoryCache
import com.aitunes.app.data.models.ModelManager
import com.aitunes.app.data.repository.ChatRepository
import com.aitunes.app.domain.SystemPromptBuilder
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.utils.ContextTruncator
import com.aitunes.app.utils.DeviceProfiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * Motor de inferencia local: RAG por sector, prompt especializado y streaming.
 * Hilos y contexto según [DeviceTier]; [MemorySearchBudget.lowMemoryMode] reduce ventana y mmap.
 */
private const val LLAMA_ENGINE_TAG = "LlamaEngine"

class LlamaEngine(
    @Suppress("unused") private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val deviceProfiler: DeviceProfiler,
    private val modelManager: ModelManager,
    private val nuclearMemoryCache: NuclearMemoryCache,
    private val systemPromptBuilder: SystemPromptBuilder
) {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ai-tunes-llama-inference").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /**
     * Precarga el GGUF en nativo (sin bloquear Main). Idempotente por ruta.
     */
    suspend fun prepareNativeSession(ggufAbsolutePath: String) {
        withContext(inferenceDispatcher) {
            val loaded = withTimeoutOrNull(LOAD_MODEL_TIMEOUT_MS) {
                val config = runtimeConfigForCurrentTier()
                LlamaNativeBridge.releaseModel()
                LlamaNativeBridge.loadModel(ggufAbsolutePath, config)
            }
            if (loaded == null) {
                Log.w(LLAMA_ENGINE_TAG, "loadModel superó ${LOAD_MODEL_TIMEOUT_MS}ms; liberando nativo.")
                LlamaNativeBridge.releaseModel()
            }
        }
    }

    suspend fun releaseNativeSession() {
        withContext(inferenceDispatcher) {
            LlamaNativeBridge.releaseModel()
        }
    }

    fun infer(
        userQuery: String,
        sector: SectorId,
        @Suppress("unused") modelPathForValidation: String?
    ): Flow<InferenceEvent> = channelFlow {
        val tier = deviceProfiler.currentTier()
        val budget = deviceProfiler.searchBudget(tier)
        val config = runtimeConfigForCurrentTier()

        withContext(Dispatchers.IO) {
            nuclearMemoryCache.ensureWarm()
        }
        val identity = nuclearMemoryCache.snapshotBlocking()

        val ragStart = SystemClock.elapsedRealtime()
        val rag = withContext(Dispatchers.IO) {
            chatRepository.getContextForQuery(userQuery, sector)
        }
        val ragMs = SystemClock.elapsedRealtime() - ragStart
        send(
            InferenceEvent.RagCompleted(
                durationMs = ragMs,
                hitLongTermEpisodic = rag.hitLongTermEpisodic
            )
        )

        val systemPrompt = systemPromptBuilder.buildForSector(sector, rag.contextText, identity)
        val nativeContextTokens = maxOf(512, config.contextSizeTokens)
        val reservedForGeneration = 128
        val maxUserTokens = 64
        val maxSystemTokens = (nativeContextTokens - reservedForGeneration - maxUserTokens).coerceAtLeast(128)
        val safeSystemPrompt =
            if (nativeContextTokens <= 768) {
                // Para contextos muy pequeños (TinyLlama en móvil/emulador), un prompt compacto evita overflow de KV.
                "Eres ai-tunes offline. Responde en español con una frase breve y útil."
            } else {
                ContextTruncator.truncateToMaxTokens(systemPrompt, maxSystemTokens)
            }
        val safeUserQuery = ContextTruncator.truncateToMaxTokens(userQuery, maxUserTokens)

        val genStart = SystemClock.elapsedRealtime()
        var usedFallback = false
        var emittedFallback = false
        if (LlamaNativeBridge.isModelReady()) {
            try {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    val tokens = Channel<String>(Channel.UNLIMITED)
                    val job = launch(inferenceDispatcher) {
                        try {
                            LlamaNativeBridge.streamCompletion(
                                systemPrompt = safeSystemPrompt,
                                userMessage = safeUserQuery,
                                config = config,
                                onToken = { chunk -> tokens.trySend(chunk) }
                            )
                        } finally {
                            tokens.close()
                        }
                    }
                    var sawAnyToken = false
                    var nativeStalled = false
                    try {
                        while (true) {
                            val timedToken = withTimeoutOrNull(
                                if (sawAnyToken) NEXT_TOKEN_WAIT_MS else FIRST_TOKEN_WAIT_MS
                            ) {
                                try {
                                    tokens.receive()
                                } catch (_: ClosedReceiveChannelException) {
                                    null
                                }
                            }
                            if (timedToken == null) {
                                if (job.isCompleted) break
                                nativeStalled = true
                                Log.w(
                                    LLAMA_ENGINE_TAG,
                                    "Sin tokens nativos durante ${if (sawAnyToken) NEXT_TOKEN_WAIT_MS else FIRST_TOKEN_WAIT_MS}ms; cancelando stream."
                                )
                                LlamaNativeBridge.cancelStream()
                                break
                            }
                            val t = timedToken
                            if (t.isEmpty()) {
                                if (job.isCompleted) break
                                continue
                            }
                            if (t.isNotBlank()) sawAnyToken = true
                            send(InferenceEvent.TokenChunk(t))
                        }
                    } finally {
                        val joined = withTimeoutOrNull(STREAM_JOIN_TIMEOUT_MS) {
                            job.join()
                            true
                        } == true
                        if (!joined) {
                            Log.w(
                                LLAMA_ENGINE_TAG,
                                "El stream nativo no terminó tras cancelación; liberando sesión para evitar bloqueo."
                            )
                            LlamaNativeBridge.releaseModel()
                        }
                    }
                    if (!sawAnyToken || nativeStalled) {
                        usedFallback = true
                    }
                }
            } catch (_: TimeoutCancellationException) {
                Log.w(LLAMA_ENGINE_TAG, "Inferencia nativa cancelada por tiempo (${GENERATION_TIMEOUT_MS}ms); fallback.")
                LlamaNativeBridge.cancelStream()
                usedFallback = true
            }
            if (usedFallback) {
                streamFallback(systemPrompt, userQuery, rag.contextText).collect { chunk ->
                    send(InferenceEvent.TokenChunk(chunk))
                }
                emittedFallback = true
            }
        } else {
            usedFallback = true
        }
        if (usedFallback && !emittedFallback) {
            streamFallback(systemPrompt, userQuery, rag.contextText).collect { chunk ->
                send(InferenceEvent.TokenChunk(chunk))
            }
        }

        val genMs = SystemClock.elapsedRealtime() - genStart
        Log.i(LLAMA_ENGINE_TAG, "Generación total ${genMs}ms (modelo listo=${LlamaNativeBridge.isModelReady()})")
        send(InferenceEvent.GenerationCompleted(genMs))
    }
        .buffer(128)
        // channelFlow hereda el Main del collector; isModelReady() puede cargar libllama en Main y congelar la UI.
        .flowOn(Dispatchers.Default)

    private fun runtimeConfigForCurrentTier(): LlamaRuntimeConfig {
        val tier = deviceProfiler.currentTier()
        val budget = deviceProfiler.searchBudget(tier)
        var ctx = budget.llamaContextSize
        if (budget.lowMemoryMode) {
            ctx = (ctx * 0.62).toInt().coerceAtLeast(384)
        }
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
        val batch = budget.llamaBatchSize.coerceAtMost(if (budget.lowMemoryMode) 128 else 256)
        return LlamaRuntimeConfig(
            contextSizeTokens = ctx,
            batchSize = batch,
            threads = threads,
            useMmap = !budget.lowMemoryMode,
            lowMemoryMode = budget.lowMemoryMode
        )
    }

    private fun streamFallback(systemPrompt: String, userQuery: String, rag: String): Flow<String> = flow {
        val summary = buildFallbackAnswer(systemPrompt, userQuery, rag)
        val tokens = summary.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (t in tokens) {
            emit("$t ")
            delay(5L)
        }
    }

    private fun buildFallbackAnswer(systemPrompt: String, userQuery: String, rag: String): String {
        val head =
            "Resumen local (JNI llama no enlazado o modelo no cargado). "
        val body = buildString {
            append("Consulta: \"${userQuery.trim()}\". ")
            if (rag.isNotBlank()) {
                append("Contexto sectorial: ")
                append(rag.take(900).replace('\n', ' '))
                append(". ")
            }
            append("System (extracto): ")
            append(systemPrompt.take(400).replace('\n', ' '))
            append(". ")
            append("Respuesta orientativa: sintetiza el contexto; si es insuficiente, dilo con claridad.")
        }
        return head + body
    }

    companion object {
        private const val LOAD_MODEL_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        // En móvil real suele ser rápido; en emulador x86_64 el primer token puede tardar bastante más.
        private const val FIRST_TOKEN_WAIT_MS = 45_000L
        private const val NEXT_TOKEN_WAIT_MS = 60_000L
        private const val STREAM_JOIN_TIMEOUT_MS = 3_000L
    }
}
