package com.aitunes.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "semantic_memory",
    indices = [
        Index(value = ["category", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class SemanticMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entity: String,
    val relation: String,
    val value: String,
    val category: String,
    val timestamp: Long,
    @ColumnInfo(name = "embedding_blob")
    val embeddingBlob: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SemanticMemoryEntity

        if (id != other.id) return false
        if (entity != other.entity) return false
        if (relation != other.relation) return false
        if (value != other.value) return false
        if (category != other.category) return false
        if (timestamp != other.timestamp) return false
        if (embeddingBlob != null) {
            if (other.embeddingBlob == null) return false
            if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        } else if (other.embeddingBlob != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + entity.hashCode()
        result = 31 * result + relation.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (embeddingBlob?.contentHashCode() ?: 0)
        return result
    }
}
