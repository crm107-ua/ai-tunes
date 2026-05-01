package com.aitunes.app.engine

/**
 * Parámetros de runtime para llama.cpp / JNI, derivados del [DeviceTier] y modo bajo consumo.
 */
data class LlamaRuntimeConfig(
    val contextSizeTokens: Int,
    val batchSize: Int,
    val threads: Int,
    /** NANO: desactivar mmap puede reducir picos de RAM (coste de velocidad). */
    val useMmap: Boolean,
    val lowMemoryMode: Boolean
)
