package com.aitunes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aitunes.app.data.local.dao.EpisodicMemoryDao
import com.aitunes.app.data.local.dao.NuclearMemoryDao
import com.aitunes.app.data.local.dao.SemanticMemoryDao
import com.aitunes.app.data.local.entity.EpisodicMemoryEntity
import com.aitunes.app.data.local.entity.EpisodicMemoryFtsEntity
import com.aitunes.app.data.local.entity.NuclearMemoryEntity
import com.aitunes.app.data.local.entity.SemanticMemoryEntity

@Database(
    entities = [
        EpisodicMemoryEntity::class,
        EpisodicMemoryFtsEntity::class,
        SemanticMemoryEntity::class,
        NuclearMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AiTunesDatabase : RoomDatabase() {

    abstract fun episodicMemoryDao(): EpisodicMemoryDao
    abstract fun semanticMemoryDao(): SemanticMemoryDao
    abstract fun nuclearMemoryDao(): NuclearMemoryDao

    companion object {
        const val DB_NAME = "ai_tunes_memory.db"

        @Volatile
        private var instance: AiTunesDatabase? = null

        fun getInstance(context: Context): AiTunesDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiTunesDatabase::class.java,
                    DB_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
