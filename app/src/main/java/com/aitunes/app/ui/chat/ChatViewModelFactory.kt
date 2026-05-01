package com.aitunes.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aitunes.app.AiTunesApplication
import com.aitunes.app.domain.model.SectorId

class ChatViewModelFactory(
    private val app: AiTunesApplication,
    private val sector: SectorId
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(app, sector) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: $modelClass")
    }
}
