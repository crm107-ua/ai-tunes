package com.aitunes.app.workers

import android.content.Context
import androidx.room.RoomDatabase
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aitunes.app.data.local.AiTunesDatabase
import com.aitunes.app.data.local.entity.EpisodicMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * Mantenimiento periódico: VACUUM, optimización de índices FTS y archivo de episodios antiguos.
 */
class DatabaseMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AiTunesDatabase.getInstance(applicationContext)
            SqliteMaintenanceRunner(db).runMaintenance()
            EpisodicArchivePurger(db, applicationContext).purgeIfDatabaseTooLarge()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "ai_tunes_database_maintenance"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

private class SqliteMaintenanceRunner(
    private val database: RoomDatabase
) {

    fun runMaintenance() {
        database.openHelper.writableDatabase.use { db ->
            db.execSQL("PRAGMA analysis_limit=400;")
            db.execSQL("PRAGMA optimize;")
            db.execSQL("VACUUM")
        }
    }
}

private class EpisodicArchivePurger(
    private val database: AiTunesDatabase,
    private val appContext: Context
) {

    suspend fun purgeIfDatabaseTooLarge() {
        val dbFile = appContext.getDatabasePath(AiTunesDatabase.DB_NAME)
        if (!dbFile.exists() || dbFile.length() < CRITICAL_BYTES) return

        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val dao = database.episodicMemoryDao()
        val oldRows = dao.findOlderThan(cutoff)
        if (oldRows.isEmpty()) return

        writeArchive(oldRows)

        dao.deleteOlderThan(cutoff)
    }

    private fun writeArchive(rows: List<EpisodicMemoryEntity>) {
        val target = File(
            appContext.filesDir,
            "episodic_archive_${System.currentTimeMillis()}.jsonl.gz"
        )
        FileOutputStream(target).use { fos ->
            GZIPOutputStream(fos).bufferedWriter(Charsets.UTF_8).use { writer ->
                rows.forEach { row ->
                    val line = JSONObject()
                        .put("messageId", row.messageId)
                        .put("content", row.content)
                        .put("timestamp", row.timestamp)
                        .put("sender", row.sender)
                        .put("category", row.category)
                    writer.appendLine(line.toString())
                }
            }
        }
    }

    companion object {
        private val CRITICAL_BYTES = 380L * 1024L * 1024L
        private const val RETENTION_DAYS = 30L
    }
}
