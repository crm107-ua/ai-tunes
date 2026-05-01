package com.aitunes.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodic_memory",
    indices = [Index(value = ["messageId"], unique = true)]
)
data class EpisodicMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val content: String,
    val timestamp: Long,
    val sender: String,
    val category: String
)
