package app.youplay.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.LikedVideoEntity
import app.youplay.data.local.SubscriptionEntity
import app.youplay.data.local.WatchHistoryEntity
import app.youplay.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryData(
    val history: List<WatchHistoryEntity>,
    val liked: List<LikedVideoEntity>,
    val subscriptions: List<SubscriptionEntity>,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val offline: app.youplay.data.download.OfflineDownloads,
) : ViewModel() {
    val state: StateFlow<LibraryData> = combine(
        repository.watchHistory,
        repository.likedVideos,
        repository.subscriptions,
    ) { h, l, s -> LibraryData(h, l, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryData(emptyList(), emptyList(), emptyList()))

    val downloads: StateFlow<List<app.youplay.data.local.DownloadEntity>> = offline.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<Int> = repository.pendingSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncNow() { viewModelScope.launch { runCatching { repository.flushPending() } } }

    val account = repository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.youplay.domain.AccountState())

    private val _playlists = kotlinx.coroutines.flow.MutableStateFlow<List<app.youplay.domain.PlaylistItem>>(emptyList())
    val playlists: StateFlow<List<app.youplay.domain.PlaylistItem>> = _playlists

    /** Watch later kept on the device (mirrors the account's queue when signed in). */
    val saved: StateFlow<List<app.youplay.data.local.SavedVideoEntity>> = repository.savedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Re-runs on every sign-in / sign-out / account switch (the repository bumps the epoch once caches are clean).
        viewModelScope.launch {
            repository.accountEpoch.collect {
                val signed = repository.isSignedIn()
                if (signed && !autoSynced) {
                    autoSynced = true
                    launch { runCatching { repository.syncFromAccount() } }
                }
                _playlists.value = if (signed) {
                    val wl = app.youplay.domain.PlaylistItem("WL", "Watch later", null, "Saved for later")
                    (listOf(wl) + runCatching { repository.playlists() }.getOrDefault(emptyList())).distinctBy { it.id }
                } else emptyList()
            }
        }
    }

    fun removeFromHistory(id: String) { viewModelScope.launch { repository.removeFromHistory(id) } }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}

private var autoSynced = false
