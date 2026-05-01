package com.aitunes.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitunes.app.AiTunesApplication
import com.aitunes.app.data.models.ModelRegistry
import com.aitunes.app.data.models.RegisteredModel
import com.aitunes.app.utils.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import com.aitunes.app.data.models.ModelDownloadProgress
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class ModelLibraryViewModel(
    private val app: AiTunesApplication
) : ViewModel() {

    val deviceTier: DeviceTier = app.deviceProfiler.currentTier()
    val models: List<RegisteredModel> = ModelRegistry.allModels

    val activeModelId = app.modelManager.activeModelIdFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val downloadProgress: StateFlow<Map<String, ModelDownloadProgress>> = app.modelManager.downloadProgress

    fun isRecommended(model: RegisteredModel): Boolean =
        ModelRegistry.isRecommendedFor(model, deviceTier)

    fun isDownloaded(id: String): Boolean = app.modelManager.isModelDownloaded(id)

    fun download(model: RegisteredModel) {
        viewModelScope.launch(Dispatchers.IO) {
            app.modelManager.enqueueDownload(model)
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            if (app.modelManager.isModelDownloaded(id)) {
                app.modelManager.setActiveModel(id)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { app.modelManager.deleteModel(id) }
    }

    /** Borra GGUF internos/externos de la app, cancela descargas y quita el modelo activo. */
    fun clearAllModelStorage(onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            app.modelManager.clearAllLocalModelData()
            onFinished()
        }
    }
}
