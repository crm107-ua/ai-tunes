package com.aitunes.app.data.local

import android.util.LruCache
import com.aitunes.app.data.local.dao.NuclearMemoryDao
import com.aitunes.app.data.local.entity.NuclearMemoryEntity

/**
 * Capa RAM para [NuclearMemoryEntity]: lecturas calientes vía LruCache tras warm-up.
 */
class NuclearMemoryCache(
    private val nuclearDao: NuclearMemoryDao,
    maxEntries: Int = 128
) {

    private val lru = LruCache<String, String>(maxEntries)

    @Volatile
    private var warmed = false

    suspend fun warmUp() {
        val rows = nuclearDao.getAll()
        synchronized(lru) {
            lru.evictAll()
            rows.forEach { row ->
                lru.put(row.key, row.value)
            }
            warmed = true
        }
    }

    suspend fun ensureWarm() {
        if (!warmed) warmUp()
    }

    suspend fun get(key: String): String? {
        synchronized(lru) {
            lru.get(key)?.let { return it }
        }
        val row = nuclearDao.getByKey(key) ?: return null
        synchronized(lru) {
            lru.put(key, row.value)
        }
        return row.value
    }

    suspend fun upsert(key: String, value: String, updatedAt: Long = System.currentTimeMillis()) {
        nuclearDao.upsert(NuclearMemoryEntity(key = key, value = value, updatedAt = updatedAt))
        synchronized(lru) {
            lru.put(key, value)
        }
    }

    fun snapshotBlocking(): Map<String, String> = synchronized(lru) {
        HashMap(lru.snapshot())
    }
}
