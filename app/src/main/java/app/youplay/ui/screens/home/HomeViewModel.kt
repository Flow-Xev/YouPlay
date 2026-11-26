package app.youplay.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.AccountState
import app.youplay.domain.ChipItem
import app.youplay.domain.FeedEntry
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUi(
    val entries: List<FeedEntry> = emptyList(),
    val chips: List<ChipItem> = emptyList(),
    val selected: String = "All",
    val continuation: String? = null,
    val loadingMore: Boolean = false,
    /** True when the account has nothing more to show (no more videos and no more Shorts). */
    val endReached: Boolean = false,
)

/** Raw, account-sourced lists. The screen state is derived from this with a Flow. */
private data class Core(
    val ready: Boolean = false,
    val error: String? = null,
    val chips: List<ChipItem> = emptyList(),
    val selected: String = "All",
    val videos: List<VideoItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val continuation: String? = null,
    val loadingMore: Boolean = false,
    val shortsNext: String? = null,
    val shortsDone: Boolean = false,
    /** Extra rows of Shorts wanted beyond what the videos need (used when the videos have run out). */
    val extraRows: Int = 0,
)

/**
 * Home pipeline, built on Kotlin Flow:
 *  - [core] holds the raw videos + Shorts pool pulled from the signed-in account;
 *  - [state] is a Flow derived from it ([composeHomeFeed] interleaves them, off the main thread);
 *  - a second Flow watches how many videos are on screen and tops the Shorts pool up from the account's
 *    Shorts feed (collectLatest, so a newer need cancels an older fetch);
 *  - a third, conflated Flow turns "scrolled near the end" into sequential page loads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val core = MutableStateFlow(Core())
    private var chips: List<ChipItem> = emptyList()
    private var loadJob: Job? = null
    private val moreDemand = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val showShorts: StateFlow<Boolean> = repository.settings.map { it.showShorts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val account: StateFlow<AccountState> = repository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())
    /** Videos you started but didn't finish (resume position saved), newest first. Not a copy of History. */
    val continueWatching = repository.watchHistory
        .map { list -> list.filter { it.progressMs > 10_000 }.take(12) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chips stay on screen while a chip's feed is loading. */
    val chipRow: StateFlow<Pair<List<ChipItem>, String>> = core
        .map { it.chips to it.selected }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<ChipItem>() to "All")

    val state: StateFlow<UiState<HomeUi>> = core
        .map { c ->
            when {
                c.error != null -> UiState.Error(c.error)
                !c.ready -> UiState.Loading
                else -> {
                    val withShorts = c.selected == "All"
                    val videosDone = c.continuation == null
                    val entries = composeHomeFeed(c.videos, c.shorts, videosDone, withShorts)
                    UiState.Success(
                        HomeUi(
                            entries = entries, chips = c.chips, selected = c.selected,
                            continuation = c.continuation, loadingMore = c.loadingMore,
                            endReached = videosDone && (!withShorts || c.shortsDone),
                        ),
                    )
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        // Flow 1: keep the Shorts pool full enough for the rows the videos on screen need.
        viewModelScope.launch {
            core
                .map { Triple(it.ready && it.selected == "All", it.videos.size, it.extraRows) }
                .distinctUntilChanged()
                .collectLatest { (active, _, _) -> if (active) topUpShorts() }
        }
        // Flow 2: scroll-near-end requests, handled one at a time.
        viewModelScope.launch { moreDemand.collect { fetchMore() } }
        // Flow 3: sign-in / sign-out / account switch -> start over with the new identity's Home.
        viewModelScope.launch {
            repository.accountEpoch.drop(1).collect {
                chips = emptyList()
                core.value = Core()
                load(null)
            }
        }
        refresh()
    }

    fun refresh() = load(null)

    fun selectChip(chip: ChipItem) = if (chip.label == "All" || chip.token == null) load(null) else load(chip)

    private fun load(chip: ChipItem?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            core.update { Core(chips = it.chips, selected = chip?.label ?: "All") }
            try {
                // keep the account's likes / subs / history fresh (throttled), never block the feed on it
                if (chip == null) launch { runCatching { repository.syncIfStale() } }
                val page = if (chip == null) repository.feed("FEwhat_to_watch") else repository.feed(null, chip.token)
                if (chip == null && page.chips.isNotEmpty()) chips = listOf(ChipItem("All", null, true)) + page.chips.filter { it.label != "All" }
                if (chips.isEmpty()) chips = listOf(ChipItem("All", null, true))
                val videos = LinkedHashMap<String, VideoItem>()
                val shelfShorts = LinkedHashMap<String, VideoItem>()
                page.entries.forEach { e ->
                    when (e) {
                        is FeedEntry.Video -> videos.putIfAbsent(e.item.id, e.item)
                        is FeedEntry.ShortsRow -> e.shorts.forEach { shelfShorts.putIfAbsent(it.id, it) }
                    }
                }
                core.value = Core(
                    ready = true, chips = chips, selected = chip?.label ?: "All",
                    videos = videos.values.toList(), shorts = shelfShorts.values.toList(),
                    continuation = page.continuation,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                core.update { it.copy(ready = false, error = e.message ?: "Failed to load home") }
            }
        }
    }

    private fun needsShorts(c: Core) = c.shorts.size < shortsTarget(c.videos.size, c.extraRows)

    /** Pulls Shorts pages from the account's Shorts feed until the pool covers the rows needed. */
    private suspend fun topUpShorts() {
        repeat(6) {
            val c = core.value
            if (!c.ready || c.selected != "All" || c.shortsDone || !needsShorts(c) || !showShorts.value) return
            val page = try {
                repository.shortsFeed(c.shortsNext)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return // transient failure: try again when the next page of videos arrives
            }
            core.update { cur ->
                val have = cur.shorts.mapTo(HashSet()) { it.id }
                val fresh = page.videos.filter { have.add(it.id) }
                cur.copy(shorts = cur.shorts + fresh, shortsNext = page.next, shortsDone = page.next == null && fresh.isEmpty())
            }
        }
    }

    fun loadMore() { moreDemand.tryEmit(Unit) }

    private suspend fun fetchMore() {
        val c = core.value
        if (!c.ready || c.loadingMore) return
        val token = c.continuation
        if (token == null) {
            // Videos are over: keep going with the account's Shorts while there are any.
            if (c.selected == "All" && !c.shortsDone) core.update { it.copy(extraRows = it.extraRows + 2) }
            return
        }
        core.update { it.copy(loadingMore = true) }
        var next: String? = token
        var tries = 0
        while (next != null && tries++ < 3) {
            val page = try {
                repository.feed(null, next)
            } catch (e: CancellationException) {
                core.update { it.copy(loadingMore = false) }
                throw e
            } catch (_: Throwable) {
                null
            }
            if (page == null) { core.update { it.copy(loadingMore = false, continuation = null) }; return }
            var added = 0
            core.update { cur ->
                val haveV = cur.videos.mapTo(HashSet()) { it.id }
                val haveS = cur.shorts.mapTo(HashSet()) { it.id }
                val vids = ArrayList<VideoItem>(); val shs = ArrayList<VideoItem>()
                page.entries.forEach { e ->
                    when (e) {
                        is FeedEntry.Video -> if (haveV.add(e.item.id)) vids += e.item
                        is FeedEntry.ShortsRow -> e.shorts.forEach { if (haveS.add(it.id)) shs += it }
                    }
                }
                added = vids.size
                cur.copy(videos = cur.videos + vids, shorts = cur.shorts + shs, continuation = page.continuation)
            }
            next = page.continuation
            if (added > 0) break
        }
        core.update { it.copy(loadingMore = false) }
    }

    fun setShortsQueue(list: List<VideoItem>) = repository.setShortsQueue(list)
}
