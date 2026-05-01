package com.aitunes.app.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Puente JNI hacia libllama.so (llama.cpp).
 * El handle nativo solo es válido tras [loadModel] exitoso; [isModelReady] refleja ese estado.
 */
object LlamaNativeBridge {

    private val nativeHandle = AtomicLong(0L)
    private val loadedPath = AtomicReference<String?>(null)

    private var loadAttempted = false
    private var jniAvailable = false

    private val _diagnostics = MutableStateFlow("")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    fun isJniLibraryLoaded(): Boolean {
        if (!loadAttempted) {
            loadAttempted = true
            jniAvailable = try {
                System.loadLibrary("llama")
                _diagnostics.value = ""
                true
            } catch (e: UnsatisfiedLinkError) {
                _diagnostics.value =
                    "Error nativo: no se cargó libllama.so (${e.message ?: "UnsatisfiedLinkError"}). ¿Compilaste el CMake NDK?"
                false
            }
        }
        return jniAvailable
    }

    fun isModelReady(): Boolean = isJniLibraryLoaded() && nativeHandle.get() != 0L && loadedPath.get() != null

    fun loadModel(modelPath: String, config: LlamaRuntimeConfig): Boolean {
        if (!isJniLibraryLoaded()) return false
        releaseModel()
        val h = nativeLoadModel(
            modelPath,
            config.contextSizeTokens,
            config.batchSize,
            config.threads,
            config.useMmap,
            config.lowMemoryMode
        )
        if (h == 0L) {
            loadedPath.set(null)
            _diagnostics.value = "Error nativo: no se pudo cargar el GGUF (ruta o memoria)."
            return false
        }
        nativeHandle.set(h)
        loadedPath.set(modelPath)
        _diagnostics.value = ""
        return true
    }

    fun releaseModel() {
        val h = nativeHandle.getAndSet(0L)
        loadedPath.set(null)
        if (h != 0L && jniAvailable) {
            nativeReleaseModel(h)
        }
    }

    fun cancelStream() {
        val h = nativeHandle.get()
        if (h != 0L && jniAvailable) {
            nativeCancel(h)
        }
    }

    fun streamCompletion(
        systemPrompt: String,
        userMessage: String,
        @Suppress("UNUSED_PARAMETER") config: LlamaRuntimeConfig,
        onToken: (String) -> Unit
    ) {
        val h = nativeHandle.get()
        if (h == 0L || loadedPath.get() == null) return
        nativeStreamCompletion(
            h,
            systemPrompt,
            userMessage,
            NativeTokenCallback { onToken(it) }
        )
    }

    @JvmStatic
    private external fun nativeLoadModel(
        path: String,
        nCtx: Int,
        nBatch: Int,
        threads: Int,
        useMmap: Boolean,
        lowMemory: Boolean
    ): Long

    @JvmStatic
    private external fun nativeReleaseModel(handle: Long)

    @JvmStatic
    private external fun nativeCancel(handle: Long)

    @JvmStatic
    private external fun nativeStreamCompletion(
        handle: Long,
        systemPrompt: String,
        userMessage: String,
        callback: NativeTokenCallback
    )
}
