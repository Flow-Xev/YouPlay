package app.youplay.ui.screens.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import app.youplay.data.net.NetworkMonitor
import app.youplay.data.net.NetKind
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

@HiltViewModel
class ShortsViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val repository: VideoRepository,
    private val playerController: app.youplay.ui.player.PlayerController,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<VideoItem>>> = _state.asStateFlow()
    val shortsQuality: StateFlow<Int> = combine(repository.settings, networkMonitor.profile) { settings, net ->
        when (settings.shortsQuality) {
            "1080" -> 1080; "720" -> 720; "480" -> 480; "360" -> 360; "240" -> 240
            else -> when {
                net.dataSaver -> 360
                net.kind == NetKind.WIFI && net.linkKbps >= 8_000 -> 1080
                net.linkKbps >= 3_000 -> 720
                net.linkKbps >= 1_200 -> 480
                else -> 360
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 720)
    val preloadShorts: StateFlow<Boolean> = repository.settings
        .map { it.preloadShorts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val startMuted: StateFlow<Boolean> = repository.settings
        .map { it.shortsStartMuted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val preloadSecSetting: StateFlow<String> = repository.settings
        .map { it.shortsPreloadSec }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")

    val startAutoScroll: StateFlow<Boolean> = repository.settings
        .map { it.shortsAutoScroll }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val wifiOnlyPreload: StateFlow<Boolean> = repository.settings
        .map { it.shortsPreloadWifiOnly }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val warmed = HashSet<String>() // "id@quality": never prefetch the same Short twice

    private val startId: String? = savedStateHandle["videoId"]
    private var next: String? = null
    private var loadingMore = false
    private var exhausted = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            exhausted = false
            _state.value = runCatching {
                val page = repository.shortsFeed()
                next = page.next
                if (startId == null) UiState.Success(page.videos)
                else {
                    val q = repository.shortsQueue()
                    val head = q.dropWhile { it.id != startId }.ifEmpty { listOf(VideoItem(startId, "Short", isShort = true)) }
                    UiState.Success((head + page.videos).distinctBy { it.id })
                }
            }.getOrElse { UiState.Error(it.message ?: "Failed to load Shorts", it) }
        }
    }

    /** Called as the user swipes: keeps the feed endless and warms the streams of the next Shorts. */
    fun onPage(index: Int, preloadEnabled: Boolean = true) {
        val list = (_state.value as? UiState.Success)?.data ?: return
        if (index >= list.size - 4) loadMore()
        if (preloadEnabled && !(wifiOnlyPreload.value && networkMonitor.profile.value.metered)) {
            val profile = networkMonitor.profile.value
            val setting = preloadSecSetting.value
            val fast = !profile.metered && profile.linkKbps >= 5_000 && !profile.powerSave && !profile.dataSaver
            // 10-15 s of the next Shorts: 15 s on a fast, unmetered link, 10 s otherwise (or the fixed value from Settings).
            val seconds = setting.toIntOrNull() ?: if (fast) 15 else 10
            val ahead = if (profile.dataSaver || profile.linkKbps in 1..1_199) 1 else 2
            list.drop(index + 1).take(ahead).forEach { v ->
                if (!warmed.add(v.id + "@" + shortsQuality.value)) return@forEach
                viewModelScope.launch {
                    runCatching {
                        val stream = repository.stream(v.id, shortsQuality.value, record = false, purpose = app.youplay.data.remote.Purpose.SHORTS)
                        playerController.preloadShortStream(stream, shortsQuality.value, seconds)
                    }.onFailure { warmed.remove(v.id + "@" + shortsQuality.value) }
                }
            }
        }
    }

    private fun loadMore() {
        if (loadingMore || exhausted) return
        loadingMore = true
        viewModelScope.launch {
            runCatching {
                // continuation while there is one; afterwards a fresh recommendation batch from the account
                val page = repository.shortsFeed(next)
                next = page.next
                val cur = (_state.value as? UiState.Success)?.data ?: return@runCatching
                val merged = (cur + page.videos).distinctBy { it.id }
                if (merged.size == cur.size && next == null) exhausted = true
                else _state.value = UiState.Success(merged)
            }
            loadingMore = false
        }
    }
}
