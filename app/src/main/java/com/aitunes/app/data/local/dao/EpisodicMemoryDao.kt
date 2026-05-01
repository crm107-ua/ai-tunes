package com.aitunes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aitunes.app.data.local.entity.EpisodicMemoryEntity

@Dao
interface EpisodicMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: EpisodicMemoryEntity): Long

    @Query(
        """
        SELECT e.* FROM episodic_memory e
        INNER JOIN episodic_memory_fts ON episodic_memory_fts.rowid = e.id
        WHERE episodic_memory_fts MATCH :matchQuery
        ORDER BY e.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(matchQuery: String, limit: Int): List<EpisodicMemoryEntity>

    @Query(
        """
        SELECT e.* FROM episodic_memory e
        INNER JOIN episodic_memory_fts ON episodic_memory_fts.rowid = e.id
        WHERE episodic_memory_fts MATCH :matchQuery AND e.category = :sectorCategory
        ORDER BY e.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchFtsForSector(
        matchQuery: String,
        limit: Int,
        sectorCategory: String
    ): List<EpisodicMemoryEntity>

    @Query(
        """
        SELECT * FROM episodic_memory
        WHERE category = :sectorCategory
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun recentForSector(sectorCategory: String, limit: Int): List<EpisodicMemoryEntity>

    @Query("SELECT * FROM episodic_memory WHERE timestamp < :cutoffMillis ORDER BY timestamp ASC")
    suspend fun findOlderThan(cutoffMillis: Long): List<EpisodicMemoryEntity>

    @Query("DELETE FROM episodic_memory WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
