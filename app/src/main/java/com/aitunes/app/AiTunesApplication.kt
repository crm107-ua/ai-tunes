package com.aitunes.app

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.aitunes.app.data.local.AiTunesDatabase
import com.aitunes.app.data.local.NuclearMemoryCache
import com.aitunes.app.data.models.ModelManager
import com.aitunes.app.data.repository.ChatRepository
import com.aitunes.app.domain.MessageClassifier
import com.aitunes.app.domain.SystemPromptBuilder
import com.aitunes.app.engine.LlamaEngine
import com.aitunes.app.engine.LlamaNativeBridge
import com.aitunes.app.utils.DeviceProfiler
import com.aitunes.app.workers.DatabaseMaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiTunesApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var deviceProfiler: DeviceProfiler
        private set

    lateinit var messageClassifier: MessageClassifier
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var llamaEngine: LlamaEngine
        private set

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            modelManager.onDownloadManagerComplete(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AiTunesDatabase.getInstance(this)
        val nuclearCache = NuclearMemoryCache(database.nuclearMemoryDao())
        deviceProfiler = DeviceProfiler(applicationContext)
        messageClassifier = MessageClassifier()
        modelManager = ModelManager(this, applicationScope)
        chatRepository = ChatRepository(
            database = database,
            deviceProfiler = deviceProfiler,
            nuclearMemoryCache = nuclearCache
        )
        llamaEngine = LlamaEngine(
            appContext = applicationContext,
            chatRepository = chatRepository,
            deviceProfiler = deviceProfiler,
            modelManager = modelManager,
            nuclearMemoryCache = nuclearCache,
            systemPromptBuilder = SystemPromptBuilder()
        )
        applicationScope.launch(Dispatchers.IO) {
            nuclearCache.warmUp()
        }
        applicationScope.launch {
            LlamaNativeBridge.isJniLibraryLoaded()
        }
        DatabaseMaintenanceWorker.schedule(this)
        registerDownloadReceiver()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadCompleteReceiver, filter)
        }
    }
}
