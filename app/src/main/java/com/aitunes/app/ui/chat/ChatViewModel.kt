package com.aitunes.app.ui.chat

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitunes.app.AiTunesApplication
import com.aitunes.app.data.models.ModelRegistry
import com.aitunes.app.data.models.RegisteredModel
import com.aitunes.app.domain.model.AiAssistant
import com.aitunes.app.domain.model.ChatMessage
import com.aitunes.app.domain.model.MessageSender
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.engine.InferenceEvent
import com.aitunes.app.engine.LlamaNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val app: AiTunesApplication,
    val sector: SectorId
) : ViewModel() {

    private val chatRepository = app.chatRepository
    private val llamaEngine = app.llamaEngine
    private val modelManager = app.modelManager
    private val deviceProfiler = app.deviceProfiler

    private val _needsActiveModel = MutableStateFlow(false)
    val needsActiveModel: StateFlow<Boolean> = _needsActiveModel.asStateFlow()

    /** Modelo óptimo del sector no descargado → evento "Modelo requerido". */
    private val _sectorModelRequired = MutableStateFlow<RegisteredModel?>(null)
    val sectorModelRequired: StateFlow<RegisteredModel?> = _sectorModelRequired.asStateFlow()

    private val _intelligencePhase = MutableStateFlow(IntelligencePhase.Idle)
    val intelligencePhase: StateFlow<IntelligencePhase> = _intelligencePhase.asStateFlow()

    private val _intelligenceModelLabel = MutableStateFlow("")
    val intelligenceModelLabel: StateFlow<String> = _intelligenceModelLabel.asStateFlow()

    private val _longTermMemoryAccess = MutableStateFlow(false)
    val longTermMemoryAccess: StateFlow<Boolean> = _longTermMemoryAccess.asStateFlow()

    private val _ragDurationMs = MutableStateFlow<Long?>(null)
    val ragDurationMs: StateFlow<Long?> = _ragDurationMs.asStateFlow()

    private val _genDurationMs = MutableStateFlow<Long?>(null)
    val genDurationMs: StateFlow<Long?> = _genDurationMs.asStateFlow()

    /** Mensajes de diagnóstico del motor nativo (p. ej. fallo de loadLibrary). */
    val nativeEngineDiagnostics: StateFlow<String> = LlamaNativeBridge.diagnostics

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Evita dos prepareNativeSession concurrentes (resume + LaunchedEffect). */
    private val sessionPrepMutex = Mutex()

    /**
     * Actualiza banners y carga el GGUF si existe. Usar [initialDelayMs] al entrar en el chat para
     * dejar que Compose pinte y reciba toques antes de saturar CPU/RAM del emulador.
     */
    fun refreshModelRequirement(initialDelayMs: Long = 0L) {
        viewModelScope.launch(Dispatchers.Default) {
            applyModelRequirementAndPrepare(initialDelayMs)
        }
    }

    private suspend fun applyModelRequirementAndPrepare(initialDelayMs: Long) {
        if (initialDelayMs > 0) delay(initialDelayMs)
        sessionPrepMutex.withLock {
            val tier = deviceProfiler.currentTier()
            val optimal = ModelRegistry.optimalModelForSector(sector, tier)
            val usable = modelManager.modelForInference(sector, tier)
            val labelSource = usable ?: optimal
            _intelligenceModelLabel.value = ModelRegistry.intelligenceModelLabel(labelSource, sector)
            _sectorModelRequired.value = if (usable == null) optimal else null
            _needsActiveModel.value = usable == null
            if (usable != null) {
                llamaEngine.prepareNativeSession(modelManager.modelFile(usable).absolutePath)
            }
        }
    }

    fun seedWelcomeIfEmpty(assistant: AiAssistant) {
        if (_messages.value.isNotEmpty()) return
        _messages.value = listOf(
            ChatMessage(
                id = "welcome",
                content = "Hola, soy tu asistente de ${assistant.name}. ¿En qué puedo ayudarte hoy?",
                sender = MessageSender.AI,
                accentColor = assistant.accentColor
            )
        )
    }

    fun dismissModelBanner() {
        _needsActiveModel.value = false
    }

    fun sendUserMessage(text: String, accentColor: Color, onNeedModelLibrary: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val tier = deviceProfiler.currentTier()
            val optimal = ModelRegistry.optimalModelForSector(sector, tier)
            val usable = modelManager.modelForInference(sector, tier)
            if (usable == null) {
                _sectorModelRequired.value = optimal
                _needsActiveModel.value = true
                withContext(Dispatchers.Main.immediate) { onNeedModelLibrary() }
                return@launch
            }

            val modelPath = modelManager.modelFile(usable).absolutePath
            sessionPrepMutex.withLock {
                llamaEngine.prepareNativeSession(modelPath)
            }

            _intelligencePhase.value = IntelligencePhase.SearchingSectorMemories
            _longTermMemoryAccess.value = false

            val userMsg = ChatMessage(
                id = "u_${System.currentTimeMillis()}",
                content = trimmed,
                sender = MessageSender.USER,
                accentColor = accentColor
            )
            _messages.value = _messages.value + userMsg
            launch(Dispatchers.IO) { chatRepository.saveMessage(userMsg, sector) }

            val aiId = "a_${System.currentTimeMillis()}"
            var aiMsg = ChatMessage(
                id = aiId,
                content = "",
                sender = MessageSender.AI,
                accentColor = accentColor,
                isLoading = true
            )
            _messages.value = _messages.value + aiMsg

            _ragDurationMs.value = null
            _genDurationMs.value = null

            val accumulated = StringBuilder()
            var lastPersist = 0L

            try {
                llamaEngine.infer(trimmed, sector, modelPath).collect { ev ->
                    when (ev) {
                        is InferenceEvent.RagCompleted -> {
                            _ragDurationMs.value = ev.durationMs
                            _longTermMemoryAccess.value = ev.hitLongTermEpisodic
                            _intelligencePhase.value = IntelligencePhase.Generating
                        }
                        is InferenceEvent.TokenChunk -> {
                            accumulated.append(ev.text)
                            aiMsg = aiMsg.copy(content = accumulated.toString(), isLoading = false)
                            replaceMessageById(aiMsg)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPersist > 350) {
                                lastPersist = now
                                launch(Dispatchers.IO) { chatRepository.saveMessage(aiMsg, sector) }
                            }
                        }
                        is InferenceEvent.GenerationCompleted -> _genDurationMs.value = ev.durationMs
                    }
                }
            } finally {
                aiMsg = aiMsg.copy(isLoading = false)
                replaceMessageById(aiMsg)
            }

            launch(Dispatchers.IO) { chatRepository.saveMessage(aiMsg, sector) }

            _intelligencePhase.value = IntelligencePhase.IndexingSemantic
            val indexJob = launch(Dispatchers.IO) {
                chatRepository.ingestSemanticFromAssistantOutput(
                    assistantText = aiMsg.content,
                    sector = sector,
                    timestamp = aiMsg.timestamp
                )
            }
            indexJob.join()

            _intelligencePhase.value = IntelligencePhase.Idle
            _longTermMemoryAccess.value = false
        }
    }

    private fun replaceMessageById(updated: ChatMessage) {
        _messages.value = _messages.value.map { m -> if (m.id == updated.id) updated else m }
    }
}
