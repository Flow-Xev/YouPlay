package app.youplay.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.AppSettings
import app.youplay.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val caches: app.youplay.data.cache.CacheManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()
    init { refreshCacheSize() }
    fun refreshCacheSize() { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _cacheSize.value = caches.sizeBytes() } }
    fun clearCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            caches.clearAll(); _cacheSize.value = caches.sizeBytes(); _message.value = "Cache cleared"
        }
    }

    fun clearWatchHistory() { viewModelScope.launch { repository.clearHistory(); _message.value = "Watch history cleared" } }
    fun clearSearchHistory() { viewModelScope.launch { repository.clearSearchHistory(); _message.value = "Search history cleared" } }
}
