package com.aitunes.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aitunes.app.AiTunesApplication

class ModelLibraryViewModelFactory(
    private val app: AiTunesApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelLibraryViewModel::class.java)) {
            return ModelLibraryViewModel(app) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: $modelClass")
    }
}
