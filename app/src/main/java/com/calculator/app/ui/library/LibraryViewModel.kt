package com.calculator.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.data.Repository
import com.calculator.app.data.entity.DislikeRuleEntity
import com.calculator.app.data.entity.SearchPresetEntity
import com.calculator.app.data.entity.VideoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application)

    val favorites: StateFlow<List<VideoEntity>> = repository.getFavoriteVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLater: StateFlow<List<VideoEntity>> = repository.getWatchLaterVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dislikeRules: StateFlow<List<DislikeRuleEntity>> = repository.getDislikeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: StateFlow<List<SearchPresetEntity>> = repository.getSearchPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteCount: StateFlow<Int> = repository.getFavoriteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val watchLaterCount: StateFlow<Int> = repository.getWatchLaterCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dislikeCount: StateFlow<Int> = repository.getDislikeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val presetCount: StateFlow<Int> = repository.getPresetCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun removeFavorite(videoId: String) {
        viewModelScope.launch { repository.removeFavorite(videoId) }
    }

    fun removeWatchLater(videoId: String) {
        viewModelScope.launch { repository.removeWatchLater(videoId) }
    }

    fun removeDislikeRule(rule: DislikeRuleEntity) {
        viewModelScope.launch { repository.removeDislikeRule(rule) }
    }
}
