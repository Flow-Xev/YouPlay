package app.youplay.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.SearchHistoryEntity
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.ChannelItem
import app.youplay.domain.FeedEntry
import app.youplay.domain.PlaylistItem
import app.youplay.domain.SearchFilter
import app.youplay.domain.SearchType
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUi(
    val entries: List<FeedEntry> = emptyList(),
    val channels: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val continuation: String? = null,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    val history: StateFlow<List<SearchHistoryEntity>> =
        repository.searchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private val _state = MutableStateFlow<UiState<SearchUi>?>(null)
    val state: StateFlow<UiState<SearchUi>?> = _state.asStateFlow()
    private var suggestJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) { _state.value = null; _suggestions.value = emptyList(); return }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            _suggestions.value = repository.suggestions(value.trim()).take(8)
        }
    }

    fun setFilter(f: SearchFilter) {
        _filter.value = f
        if (_query.value.isNotBlank()) search()
    }

    fun setType(t: SearchType) = setFilter(_filter.value.copy(type = t))

    fun search(q: String = _query.value) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        _query.value = trimmed
        _suggestions.value = emptyList()
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                val p = repository.searchPage(trimmed, _filter.value)
                UiState.Success(SearchUi(p.entries, p.channels, p.playlists, p.shorts, p.continuation))
            }.getOrElse { UiState.Error(it.message ?: "Search failed", it) }
        }
    }

    fun loadMore() {
        val cur = (_state.value as? UiState.Success)?.data ?: return
        val token = cur.continuation ?: return
        if (cur.loadingMore) return
        _state.value = UiState.Success(cur.copy(loadingMore = true))
        viewModelScope.launch {
            val p = runCatching { repository.searchPage(_query.value, _filter.value, token) }.getOrNull()
            val latest = (_state.value as? UiState.Success)?.data ?: return@launch
            if (p == null) { _state.value = UiState.Success(latest.copy(loadingMore = false, continuation = null)); return@launch }
            val have = latest.entries.filterIsInstance<FeedEntry.Video>().map { it.item.id }.toHashSet()
            val fresh = p.entries.filter { it !is FeedEntry.Video || have.add(it.item.id) }
            val haveS = latest.shorts.map { it.id }.toHashSet()
            _state.value = UiState.Success(
                latest.copy(
                    entries = latest.entries + fresh,
                    channels = latest.channels + p.channels.filter { c -> latest.channels.none { it.id == c.id } },
                    playlists = latest.playlists + p.playlists.filter { c -> latest.playlists.none { it.id == c.id } },
                    shorts = latest.shorts + p.shorts.filter { haveS.add(it.id) },
                    continuation = p.continuation, loadingMore = false,
                ),
            )
        }
    }

    fun setShortsQueue(list: List<VideoItem>) = repository.setShortsQueue(list)
    fun removeHistory(q: String) { viewModelScope.launch { repository.removeSearch(q) } }
    fun clearHistory() { viewModelScope.launch { repository.clearSearchHistory() } }
}
