package app.youplay.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.SubscriptionEntity
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.AccountState
import app.youplay.domain.FeedEntry
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubsUi(val entries: List<FeedEntry>, val continuation: String?, val loadingMore: Boolean = false)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val notifier: app.youplay.data.notify.NewVideoNotifier,
) : ViewModel() {
    val account: StateFlow<AccountState> =
        repository.account.stateIn(viewModelScope, SharingStarted.Eagerly, AccountState())
    val channels: StateFlow<List<SubscriptionEntity>> =
        repository.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<UiState<SubsUi>?>(null)
    val state: StateFlow<UiState<SubsUi>?> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var lastLoaded = 0L

    init {
        viewModelScope.launch {
            repository.accountEpoch.collect { if (repository.isSignedIn()) load() else _state.value = null }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                val p = repository.feed("FEsubscriptions", fresh = true)
                lastLoaded = System.currentTimeMillis()
                notifier.markSeen(p.entries)
                UiState.Success(SubsUi(p.entries, p.continuation))
            }.getOrElse { UiState.Error(it.message ?: "Failed to load subscriptions", it) }
        }
    }

    /** Pull-to-refresh: re-fetch the feed and the channel list without blanking the screen; on failure the old list stays. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                runCatching { repository.refreshSubscribedChannels() }
                val p = runCatching { repository.feed("FEsubscriptions", fresh = true) }.getOrNull()
                if (p != null) {
                    lastLoaded = System.currentTimeMillis()
                    notifier.markSeen(p.entries)
                    _state.value = UiState.Success(SubsUi(p.entries, p.continuation))
                } else if (_state.value !is UiState.Success) {
                    _state.value = UiState.Error("Couldn't refresh subscriptions", null)
                }
            } finally { _refreshing.value = false }
        }
    }

    /** Called when the tab comes back to the foreground: refresh quietly if the list is older than [maxAgeMs]. */
    fun refreshIfStale(maxAgeMs: Long = 10 * 60_000L) {
        if (_state.value is UiState.Success && System.currentTimeMillis() - lastLoaded > maxAgeMs) refresh()
    }

    fun loadMore() {
        val cur = (_state.value as? UiState.Success)?.data ?: return
        val token = cur.continuation ?: return
        if (cur.loadingMore) return
        _state.value = UiState.Success(cur.copy(loadingMore = true))
        viewModelScope.launch {
            val p = runCatching { repository.feed(null, token) }.getOrNull()
            val latest = (_state.value as? UiState.Success)?.data ?: return@launch
            if (p == null) { _state.value = UiState.Success(latest.copy(loadingMore = false, continuation = null)); return@launch }
            val have = latest.entries.filterIsInstance<FeedEntry.Video>().map { it.item.id }.toHashSet()
            val fresh = p.entries.filter { it !is FeedEntry.Video || have.add(it.item.id) }
            _state.value = UiState.Success(latest.copy(entries = latest.entries + fresh, continuation = p.continuation, loadingMore = false))
        }
    }

    fun setShortsQueue(list: List<VideoItem>) = repository.setShortsQueue(list)
}
