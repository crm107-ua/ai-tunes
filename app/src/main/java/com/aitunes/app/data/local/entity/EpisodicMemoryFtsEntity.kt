package com.aitunes.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Índice FTS (tabla virtual) enlazada a [EpisodicMemoryEntity].
 *
 * Se usa [Fts4] en lugar de [androidx.room.Fts5] porque la combinación Room 2.6.1 + KSP 2.0
 * falla al resolver `contentEntity` para `@Fts5` ([MissingType] en el procesador). La búsqueda
 * full-text masiva sigue siendo válida; se puede migrar a `@Fts5` al actualizar Room cuando el
 * fix esté estable en tu cadena de herramientas.
 */
@Entity(tableName = "episodic_memory_fts")
@Fts4(contentEntity = EpisodicMemoryEntity::class)
data class EpisodicMemoryFtsEntity(
    val content: String,
    val category: String
)
