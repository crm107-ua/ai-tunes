package com.aitunes.app.data.models

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.utils.DeviceTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private val Context.modelStore: DataStore<Preferences> by preferencesDataStore(name = "model_prefs")

/** Fracción 0..1 de bytes descargados; [INDETERMINATE_PROGRESS] si el tamaño total aún no es conocido. */
const val INDETERMINATE_PROGRESS = -1f

/**
 * Progreso de descarga para la UI: [fraction] 0f–1f, o [INDETERMINATE_PROGRESS] si aún no hay total.
 * Bytes -1 si el sistema aún no los expone.
 */
data class ModelDownloadProgress(
    val fraction: Float,
    val bytesDownloaded: Long = -1L,
    val bytesTotal: Long = -1L,
    /** Bytes del .gguf visibles en la carpeta de descarga (comprobación directa). */
    val bytesOnDisk: Long = -1L,
    /** Mensaje corto cuando no hay % real (cola, conexión, etc.). */
    val statusHint: String? = null,
) {
    val isIndeterminate: Boolean get() = fraction == INDETERMINATE_PROGRESS
}

class ModelManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.modelStore
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val activeModelKey = stringPreferencesKey("active_model_id")
    private val pendingByDownloadId = ConcurrentHashMap<Long, String>()

    private val _downloadProgress = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, ModelDownloadProgress>> = _downloadProgress.asStateFlow()

    private val progressPollJobs = ConcurrentHashMap<Long, Job>()

    /** Evita doble migración/toast si llegan el broadcast y el polling casi a la vez. */
    private val handledTerminalDownloadIds =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun modelsDirectory(): File =
        File(appContext.filesDir, MODEL_SUBDIR).apply { mkdirs() }

    private fun externalModelsDirectory(): File {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        return File(base, MODEL_SUBDIR).apply { mkdirs() }
    }

    fun modelFile(model: RegisteredModel): File =
        File(modelsDirectory(), model.fileName)

    private fun externalModelFile(model: RegisteredModel): File =
        File(externalModelsDirectory(), model.fileName)

    fun isModelDownloaded(modelId: String): Boolean {
        val model = ModelRegistry.getById(modelId) ?: return false
        val f = modelFile(model)
        return f.isFile && f.length() > MIN_VALID_BYTES
    }

    suspend fun deleteModel(modelId: String) {
        val model = ModelRegistry.getById(modelId) ?: return
        modelFile(model).delete()
        externalModelFile(model).delete()
        dataStore.edit { prefs ->
            if (prefs[activeModelKey] == modelId) {
                prefs.remove(activeModelKey)
            }
        }
    }

    suspend fun setActiveModel(modelId: String?) {
        dataStore.edit { prefs ->
            if (modelId == null) {
                prefs.remove(activeModelKey)
            } else {
                prefs[activeModelKey] = modelId
            }
        }
    }

    fun activeModelIdFlow(): Flow<String?> =
        dataStore.data.map { it[activeModelKey] }

    suspend fun getActiveModelId(): String? =
        dataStore.data.map { it[activeModelKey] }.first()

    suspend fun getActiveModelPath(): String? {
        val id = getActiveModelId() ?: return null
        if (!isModelDownloaded(id)) return null
        val model = ModelRegistry.getById(id) ?: return null
        return modelFile(model).absolutePath
    }

    /**
     * Borra todos los GGUF en interno y externo app-specific, cancela descargas activas,
     * limpia preferencias del modelo activo y el estado de progreso en UI.
     */
    suspend fun clearAllLocalModelData() {
        withContext(Dispatchers.IO) {
            progressPollJobs.values.forEach { it.cancel() }
            progressPollJobs.clear()
            handledTerminalDownloadIds.clear()
            pendingByDownloadId.keys.forEach { id ->
                try {
                    downloadManager.remove(id)
                } catch (_: Exception) {
                }
            }
            pendingByDownloadId.clear()
            _downloadProgress.value = emptyMap()
            wipeModelDirectory(modelsDirectory())
            wipeModelDirectory(externalModelsDirectory())
            dataStore.edit { prefs -> prefs.remove(activeModelKey) }
        }
    }

    /**
     * Modelo a usar en inferencia: el **activo** en biblioteca si está descargado; si no, el óptimo por sector.
     */
    suspend fun modelForInference(sector: SectorId, tier: DeviceTier): RegisteredModel? {
        val activeId = getActiveModelId()
        if (activeId != null) {
            val active = ModelRegistry.getById(activeId)
            if (active != null && isModelDownloaded(activeId)) return active
        }
        val optimal = ModelRegistry.optimalModelForSector(sector, tier)
        return if (isModelDownloaded(optimal.id)) optimal else null
    }

    /**
     * Descarga via [DownloadManager] a [modelsDirectory] (app-specific, sin scoped storage frágil).
     */
    fun enqueueDownload(model: RegisteredModel): Long {
        return try {
            enqueueDownloadInner(model)
        } catch (t: Throwable) {
            Log.e(TAG, "enqueueDownload inesperado", t)
            toastMain("Error al iniciar la descarga: ${t.javaClass.simpleName}")
            -1L
        }
    }

    private fun notificationVisibility(): Int {
        val canNotify =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        return if (canNotify) {
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        } else {
            DownloadManager.Request.VISIBILITY_HIDDEN
        }
    }

    private fun enqueueDownloadInner(model: RegisteredModel): Long {
        modelsDirectory()
        externalModelsDirectory()

        val internalDest = modelFile(model)
        internalDest.parentFile?.mkdirs()
        if (internalDest.exists()) internalDest.delete()
        val externalDest = externalModelFile(model)
        externalDest.parentFile?.mkdirs()
        if (externalDest.exists()) externalDest.delete()

        val url = huggingFaceDirectUrl(model.huggingFaceDownloadUrl)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ai-tunes · ${model.displayName}")
            .setDescription("Descargando ${model.fileName}")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(notificationVisibility())
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .addRequestHeader("User-Agent", DOWNLOAD_USER_AGENT)

        try {
            request.setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "$MODEL_SUBDIR/${model.fileName}"
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "setDestinationInExternalFilesDir", e)
            toastMain("No se pudo preparar la carpeta de descarga.")
            return -1L
        }

        val id = try {
            downloadManager.enqueue(request)
        } catch (e: SecurityException) {
            Log.e(TAG, "DownloadManager.enqueue SecurityException", e)
            toastMain("Permiso denegado o bloqueo del sistema al descargar. Concede notificaciones (Android 13+) o revisa ajustes.")
            return -1L
        }
        if (id < 0L) {
            Log.e(TAG, "DownloadManager.enqueue falló (¿red, espacio o URL?)")
            toastMain("No se pudo iniciar la descarga. Revisa conexión y espacio libre.")
            return -1L
        }
        pendingByDownloadId[id] = model.id
        Log.i(TAG, "Descarga encolada id=$id url=${url.take(80)}...")
        toastShort("Descarga iniciada: ${model.displayName}")
        startProgressPolling(id, model.id)
        return id
    }

    /** Llamar desde [BroadcastReceiver] ACTION_DOWNLOAD_COMPLETE. */
    fun onDownloadManagerComplete(intent: Intent?) {
        try {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (id < 0L) return
            scheduleTerminalDownloadHandling(id)
        } catch (t: Throwable) {
            Log.e(TAG, "onDownloadManagerComplete", t)
        }
    }

    /**
     * Unifica broadcast + polling: migra a interno en un hilo de fondo, limpia la UI y activa el modelo.
     * Idempotente por [downloadId] (evita doble trabajo si llegan ambos eventos).
     */
    private fun scheduleTerminalDownloadHandling(downloadId: Long) {
        if (!handledTerminalDownloadIds.add(downloadId)) {
            Log.d(TAG, "Descarga terminal ya procesada id=$downloadId")
            return
        }
        progressPollJobs.remove(downloadId)?.cancel()
        externalScope.launch(Dispatchers.IO) {
            try {
                processTerminalDownload(downloadId)
            } catch (t: Throwable) {
                Log.e(TAG, "processTerminalDownload id=$downloadId", t)
                toastMain("Error al finalizar la descarga: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private suspend fun processTerminalDownload(downloadId: Long) {
        val hintedModelId = pendingByDownloadId[downloadId]
        pendingByDownloadId.remove(downloadId)
        if (hintedModelId != null) {
            clearProgressFor(hintedModelId)
        }
        var modelId = hintedModelId
            ?: inferModelIdFromDownloadId(downloadId)
            ?: inferModelIdFromExternalDirOnly()

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { c: Cursor ->
            if (!c.moveToFirst()) {
                Log.w(TAG, "Sin fila en DownloadManager para id=$downloadId (¿ya eliminada?)")
                if (modelId != null) {
                    val m = ModelRegistry.getById(modelId)
                    if (m != null && migrateWithRetries(m) && isModelDownloaded(modelId)) {
                        setActiveModel(modelId)
                        toastMain("${m.displayName} listo. Se activó para el chat.")
                    }
                }
                return@use
            }
            val idxStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (idxStatus < 0) return@use
            val status = c.getInt(idxStatus)
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    var mid = modelId ?: inferModelIdFromDownloadId(downloadId) ?: inferModelIdFromExternalDirOnly()
                    if (mid == null) {
                        Log.e(TAG, "SUCCESS sin modelId downloadId=$downloadId")
                        toastMain("Descarga completada pero no se reconoció el modelo. Vacía almacenamiento y reintenta.")
                        return@use
                    }
                    val model = ModelRegistry.getById(mid) ?: return@use
                    val ok = migrateWithRetries(model)
                    if (ok && isModelDownloaded(mid)) {
                        setActiveModel(mid)
                        toastMain("${model.displayName} listo. Se activó para el chat.")
                    } else {
                        val extLen = externalModelFile(model).length()
                        val intLen = modelFile(model).length()
                        Log.e(TAG, "Migración incompleta mid=$mid ext=$extLen int=$intLen")
                        toastMain("No se pudo copiar el modelo al almacenamiento interno (espacio o archivo incompleto).")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else -1
                    Log.e(TAG, "FAILED downloadId=$downloadId modelId=$modelId reason=$reason")
                    toastMain("Descarga fallida (código $reason). Comprueba red y URL del modelo.")
                }
                else -> Log.w(TAG, "Estado no terminal status=$status id=$downloadId")
            }
        }
    }

    /** Si solo hay un .gguf en la carpeta de descargas de la app, infiere el modelo del catálogo. */
    private fun inferModelIdFromExternalDirOnly(): String? {
        val dir = externalModelsDirectory()
        if (!dir.isDirectory) return null
        val ggufs = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?: return null
        if (ggufs.size != 1) return null
        return ModelRegistry.findByFileName(ggufs[0].name)?.id
    }

    private suspend fun migrateWithRetries(model: RegisteredModel): Boolean {
        val dst = modelFile(model)
        repeat(18) { attempt ->
            val src = externalModelFile(model)
            val extLen = if (src.isFile) src.length() else 0L
            Log.i(TAG, "migrate ${model.fileName} intento=${attempt + 1} extLen=$extLen")
            if (extLen >= MIN_VALID_BYTES) {
                try {
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                    val intLen = dst.length()
                    if (intLen >= MIN_VALID_BYTES && intLen >= extLen * 98 / 100) {
                        return true
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "copyTo interno", t)
                }
            }
            delay(400)
        }
        return isModelDownloaded(model.id)
    }

    fun onDownloadCompleted(downloadId: Long) {
        pendingByDownloadId.remove(downloadId)
    }

    fun queryDownloadStatus(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { c: Cursor ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (idx < 0) return null
            return c.getInt(idx)
        }
        return null
    }

    private fun startProgressPolling(downloadId: Long, modelId: String) {
        progressPollJobs[downloadId]?.cancel()
        progressPollJobs[downloadId] = externalScope.launch(Dispatchers.IO) {
            val model = ModelRegistry.getById(modelId)
            if (model == null) {
                progressPollJobs.remove(downloadId)
                return@launch
            }
            try {
                while (isActive) {
                    val status = queryDownloadStatus(downloadId)
                    val prog = queryBytesProgress(downloadId)
                    val dmSoFar = prog?.first?.coerceAtLeast(0L) ?: 0L
                    val dmTotal = prog?.second?.coerceAtLeast(-1L) ?: -1L
                    val fileLen = externalModelFile(model).length().coerceAtLeast(0L)
                    val ui = DownloadProgressCalculator.compute(
                        model = model,
                        dmSoFarRaw = dmSoFar,
                        dmTotalRaw = dmTotal,
                        fileLenRaw = fileLen,
                        status = status
                    )
                    setProgressFor(modelId, ui)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL,
                        DownloadManager.STATUS_FAILED -> {
                            scheduleTerminalDownloadHandling(downloadId)
                            break
                        }
                        null -> break
                        else -> { }
                    }
                    delay(350)
                }
            } finally {
                progressPollJobs.remove(downloadId)
            }
        }
    }

    private fun queryBytesProgress(downloadId: Long): Pair<Long, Long>? {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
            if (!c.moveToFirst()) return null
            val idxSoFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val idxTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            if (idxSoFar < 0 || idxTotal < 0) return null
            return c.getLong(idxSoFar) to c.getLong(idxTotal)
        }
        return null
    }

    private fun setProgressFor(modelId: String, progress: ModelDownloadProgress) {
        val next = _downloadProgress.value.toMutableMap()
        next[modelId] = progress
        _downloadProgress.value = next
    }

    private fun clearProgressFor(modelId: String) {
        val next = _downloadProgress.value.toMutableMap()
        if (next.remove(modelId) == null) return
        _downloadProgress.value = next
    }

    private fun inferModelIdFromDownloadId(downloadId: Long): String? {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
            if (!c.moveToFirst()) return null
            val idxUri = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (idxUri >= 0) {
                val uriStr = c.getString(idxUri)
                if (!uriStr.isNullOrBlank()) {
                    val path = Uri.parse(uriStr).path ?: return null
                    val name = File(path).name
                    return ModelRegistry.findByFileName(name)?.id
                }
            }
            return null
        }
        return null
    }

    private fun toastMain(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun toastShort(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun wipeModelDirectory(dir: File) {
        try {
            if (!dir.exists()) return
            dir.deleteRecursively()
            dir.mkdirs()
        } catch (t: Throwable) {
            Log.e(TAG, "wipeModelDirectory ${dir.path}", t)
        }
    }

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_SUBDIR = "llm_models"
        private const val MIN_VALID_BYTES = 512L * 1024L
        private const val DOWNLOAD_USER_AGENT = "AiTunes/1.0 (Android; GGUF DownloadManager)"

        internal fun huggingFaceDirectUrl(base: String): String {
            val trimmed = base.trim()
            return if (trimmed.contains("?", ignoreCase = true)) {
                if (trimmed.contains("download=true")) trimmed else "$trimmed&download=true"
            } else {
                "$trimmed?download=true"
            }
        }
    }
}
