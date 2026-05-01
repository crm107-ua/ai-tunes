package com.aitunes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nuclear_memory")
data class NuclearMemoryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long
)
