package com.aitunes.app.engine

sealed interface InferenceEvent {
    data class RagCompleted(
        val durationMs: Long,
        val hitLongTermEpisodic: Boolean
    ) : InferenceEvent
    data class TokenChunk(val text: String) : InferenceEvent
    data class GenerationCompleted(val durationMs: Long) : InferenceEvent
}
