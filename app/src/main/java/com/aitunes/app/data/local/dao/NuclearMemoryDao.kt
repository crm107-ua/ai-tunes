package com.aitunes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aitunes.app.data.local.entity.NuclearMemoryEntity

@Dao
interface NuclearMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: NuclearMemoryEntity)

    @Query("SELECT * FROM nuclear_memory WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): NuclearMemoryEntity?

    @Query("SELECT * FROM nuclear_memory")
    suspend fun getAll(): List<NuclearMemoryEntity>
}
