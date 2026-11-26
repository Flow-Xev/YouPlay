package app.youplay.ui.screens.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.SubscriptionEntity
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.ChannelItem
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelPage(val channel: ChannelItem?, val videos: List<VideoItem>)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
) : ViewModel() {
    private val channelId: String? = savedStateHandle["channelId"]

    val subscriptions: StateFlow<List<SubscriptionEntity>> =
        repository.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<UiState<ChannelPage>?>(
        if (channelId == null) null else UiState.Loading,
    )
    val state: StateFlow<UiState<ChannelPage>?> = _state.asStateFlow()

    private val _subscribed = MutableStateFlow(false)
    val subscribed: StateFlow<Boolean> = _subscribed.asStateFlow()

    private var subJob: Job? = null

    init {
        channelId?.let { load(it) }
    }

    fun load(id: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                val (channel, videos) = repository.channel(id)
                subJob?.cancel()
                if (channel != null) {
                    subJob = launch {
                        repository.observeSubscribed(channel.id).collect { _subscribed.value = it }
                    }
                }
                UiState.Success(ChannelPage(channel, videos))
            }.getOrElse { UiState.Error(it.message ?: "Channel unavailable", it) }
        }
    }

    fun toggleSubscribe(channel: ChannelItem) {
        viewModelScope.launch { repository.toggleSubscribe(channel) }
    }
}
