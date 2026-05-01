package com.aitunes.app.ui.chat

enum class IntelligencePhase {
    Idle,
    /** Buscando en FTS + hechos del sector. */
    SearchingSectorMemories,
    /** Ejecutando el modelo GGUF. */
    Generating,
    /** Promoción inmediata a capa semántica si aplica. */
    IndexingSemantic
}
