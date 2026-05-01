package com.aitunes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aitunes.app.data.local.entity.SemanticMemoryEntity

@Dao
interface SemanticMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SemanticMemoryEntity): Long

    @Query(
        """
        SELECT * FROM semantic_memory
        WHERE category = :category
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun findByCategory(category: String, limit: Int): List<SemanticMemoryEntity>
}
