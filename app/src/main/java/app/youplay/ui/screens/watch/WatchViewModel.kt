package app.youplay.ui.screens.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.ChannelItem
import app.youplay.domain.PlaylistItem
import app.youplay.domain.StreamInfo
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import app.youplay.domain.WatchExtras
import app.youplay.ui.player.PlayerController
import app.youplay.data.download.OfflineDownloads
import app.youplay.data.local.DOWNLOAD_DONE
import app.youplay.data.local.DOWNLOAD_FAILED
import app.youplay.data.local.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatchPage(val stream: StreamInfo, val extras: WatchExtras)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
    val controller: PlayerController,
    private val downloads: OfflineDownloads,
    private val extrasApi: app.youplay.data.remote.ExtrasApi,
) : ViewModel() {
    val videoId: String = checkNotNull(savedStateHandle["videoId"])
    fun setShortsQueue(list: List<app.youplay.domain.VideoItem>) = repository.setShortsQueue(list)

    private val _state = MutableStateFlow<UiState<WatchPage>>(UiState.Loading)
    val state: StateFlow<UiState<WatchPage>> = _state.asStateFlow()
    private val _channelId = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistItem>> = _playlists.asStateFlow()

    private val _votes = MutableStateFlow<app.youplay.data.remote.VoteStats?>(null)
    val votes: StateFlow<app.youplay.data.remote.VoteStats?> = _votes.asStateFlow()

    val liked = repository.observeLiked(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val subscribed = _channelId
        .flatMapLatest { id -> if (id == null) flowOf(false) else repository.observeSubscribed(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoplay: StateFlow<Boolean> = repository.settings.map { it.autoplay }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val download: StateFlow<DownloadEntity?> = downloads.observe(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleDownload(s: StreamInfo) {
        val d = download.value
        if (d == null || d.status == DOWNLOAD_FAILED) {
            downloads.start(videoId, s.title, s.channelName, s.thumbnailUrl)
            _message.value = "Downloading \"${s.title.take(40)}\""
        } else {
            downloads.delete(videoId)
            _message.value = if (d.status == DOWNLOAD_DONE) "Download removed" else "Download cancelled"
        }
    }

    private val _px = MutableStateFlow(app.youplay.domain.PlayerExtras())
    val playerExtras: StateFlow<app.youplay.domain.PlayerExtras> = _px.asStateFlow()
    private val _moreShorts = MutableStateFlow<List<app.youplay.domain.VideoItem>>(emptyList())
    val moreShorts: StateFlow<List<app.youplay.domain.VideoItem>> = _moreShorts.asStateFlow()

    init {
        viewModelScope.launch { _px.value = repository.playerExtras(videoId) }
        viewModelScope.launch {
            _moreShorts.value = runCatching { repository.shortsFeed().videos.filter { it.id != videoId }.take(12) }.getOrDefault(emptyList())
        }
        load()
    }

    fun load() {
        controller.watchVisible = true // the Watch screen is opening: Auto may use full-size quality
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                val existing = controller.current.value
                val st = repository.settings.first()
                val fresh = existing?.videoId != videoId
                if (fresh) {
                    controller.setSpeed(st.defaultSpeed)
                    st.preferredQuality.toIntOrNull()?.let { if (controller.quality.value == 0) controller.setQualityValue(it) }
                }
                controller.sponsorBlock = st.sponsorBlock
                launch { _votes.value = extrasApi.votes(videoId) }
                val resume = repository.savedProgress(videoId)
                val start = if (resume > 8_000) resume - 3_000 else 0L
                val local = if (existing?.videoId == videoId) null else downloads.localStream(videoId)
                val pre = if (existing?.videoId == videoId || local != null) null else controller.takePreloaded(videoId)
                val h = heightFor(controller.quality.value)
                val stream = if (existing != null && existing.videoId == videoId) existing
                else if (local != null) local.also { controller.play(it, start) }
                else if (pre != null) pre.also { controller.play(it, start, height = h) }
                else runCatching { repository.stream(videoId, h) }
                    .recoverCatching { repository.stream(videoId, h, fresh = true) }
                    .getOrThrow()
                    .also { controller.play(it, start, height = h) }
                _channelId.value = stream.channelId
                val extras = runCatching { repository.watchExtras(videoId) }.getOrDefault(WatchExtras(emptyList()))
                controller.preloadNext(extras.related.firstOrNull { !it.isShort }?.id)
                UiState.Success(WatchPage(stream, extras))
            }.getOrElse { UiState.Error(it.message ?: "Playback unavailable", it) }
        }
    }

    private fun asVideo(s: StreamInfo) = VideoItem(s.videoId, s.title, s.channelName, s.channelId, s.thumbnailUrl)

    val disliked = repository.observeDisliked(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleLike(s: StreamInfo) {
        viewModelScope.launch {
            val (on, r) = repository.toggleLike(asVideo(s))
            _message.value = r.describe(if (on) "Liked" else "Like removed")
        }
    }

    fun toggleDislike() {
        viewModelScope.launch {
            val (on, r) = repository.toggleDislike(videoId)
            _message.value = r.describe(if (on) "Disliked" else "Dislike removed")
        }
    }

    fun toggleSubscribe(s: StreamInfo) {
        val id = s.channelId ?: return
        viewModelScope.launch {
            val (on, r) = repository.toggleSubscribe(ChannelItem(id, s.channelName))
            _message.value = r.describe(if (on) "Subscribed" else "Unsubscribed")
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            val wl = PlaylistItem("WL", "Watch later")
            _playlists.value = listOf(wl) + runCatching { repository.playlists() }.getOrDefault(emptyList())
        }
    }

    fun saveTo(p: PlaylistItem) {
        viewModelScope.launch {
            _message.value =
                if (p.id == "WL") repository.saveToWatchLater(videoId).describe("Saved to Watch later")
                else if (repository.addToPlaylist(p.id, videoId)) "Saved to ${p.title}" else "Sign in to save videos"
        }
    }

    private fun heightFor(q: Int) = if (q == 0) controller.autoHeight() else q

    fun setQuality(h: Int) {
        viewModelScope.launch {
            val pos = controller.player.currentPosition
            val hh = heightFor(h)
            runCatching { repository.stream(videoId, hh, record = false) }
                .onSuccess { controller.setQualityValue(h); controller.play(it, pos, height = hh, fromSwitch = true) }
                .onFailure { _message.value = "That quality isn't available" }
        }
    }

    fun messageShown() { _message.value = null }
}
