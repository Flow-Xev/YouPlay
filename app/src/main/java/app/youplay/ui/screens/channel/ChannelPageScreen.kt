package app.youplay.ui.screens.channel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.youplay.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.ChannelItem
import app.youplay.domain.FeedEntry
import app.youplay.domain.PlaylistItem
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import app.youplay.ui.components.PlaylistRow
import app.youplay.ui.components.ShortThumb
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.VideoCard
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChTab(val label: String, val params: String?) {
    HOME("Home", null),
    VIDEOS("Videos", "EgZ2aWRlb3PyBgQKAjoA"),
    SHORTS("Shorts", "EgZzaG9ydHPyBgUKA5oBAA=="),
    PLAYLISTS("Playlists", "EglwbGF5bGlzdHPyBgQKAkIA"),
}

data class ChannelHead(val channel: ChannelItem, val home: List<VideoItem>, val shelves: List<app.youplay.domain.ChannelShelf> = emptyList())
data class TabData(val videos: List<VideoItem> = emptyList(), val shorts: List<VideoItem> = emptyList(), val playlists: List<PlaylistItem> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
) : ViewModel() {
    private val channelId: String = checkNotNull(savedStateHandle["channelId"])
    private val _head = MutableStateFlow<UiState<ChannelHead>>(UiState.Loading)
    val head: StateFlow<UiState<ChannelHead>> = _head.asStateFlow()
    private val _tab = MutableStateFlow(ChTab.HOME)
    val tab: StateFlow<ChTab> = _tab.asStateFlow()
    private val _tabData = MutableStateFlow<UiState<TabData>?>(null)
    val tabData: StateFlow<UiState<TabData>?> = _tabData.asStateFlow()
    private val cache = HashMap<ChTab, TabData>()

    val subscribed: StateFlow<Boolean> = repository.observeSubscribed(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Shorts + playlists fetched in the background so the Home tab can show YouTube-style shelves. */
    private val _extras = MutableStateFlow(TabData())
    val extras: StateFlow<TabData> = _extras.asStateFlow()

    init { load(); loadExtras() }

    private fun loadExtras() {
        viewModelScope.launch {
            val shorts = runCatching { repository.channelTab(channelId, ChTab.SHORTS.params!!).first }.getOrNull()
                ?.entries?.filterIsInstance<FeedEntry.ShortsRow>()?.flatMap { it.shorts }.orEmpty()
            val pls = runCatching { repository.channelTab(channelId, ChTab.PLAYLISTS.params!!).second }.getOrDefault(emptyList())
            _extras.value = TabData(shorts = shorts, playlists = pls)
        }
    }

    fun load() {
        viewModelScope.launch {
            _head.value = UiState.Loading
            _head.value = runCatching {
                val (ch, vids, shelves) = repository.channelHome(channelId)
                UiState.Success(ChannelHead(ch ?: ChannelItem(channelId, "Channel"), vids, shelves))
            }.getOrElse { UiState.Error(it.message ?: "Channel unavailable", it) }
        }
    }

    fun selectTab(t: ChTab) {
        _tab.value = t
        val params = t.params ?: run { _tabData.value = null; return }
        cache[t]?.let { _tabData.value = UiState.Success(it); return }
        viewModelScope.launch {
            _tabData.value = UiState.Loading
            _tabData.value = runCatching {
                val (page, pls) = repository.channelTab(channelId, params)
                val vids = page.entries.filterIsInstance<FeedEntry.Video>().map { it.item }
                val shorts = page.entries.filterIsInstance<FeedEntry.ShortsRow>().flatMap { it.shorts }
                TabData(vids, shorts, pls).also { cache[t] = it }.let { UiState.Success(it) }
            }.getOrElse { UiState.Error(it.message ?: "Failed to load", it) }
        }
    }

    fun toggleSubscribe(ch: ChannelItem) { viewModelScope.launch { repository.toggleSubscribe(ch) } }
    fun setShortsQueue(list: List<VideoItem>) = repository.setShortsQueue(list)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelPageScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: ChannelPageViewModel = hiltViewModel(),
) {
    val head by viewModel.head.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val tabData by viewModel.tabData.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().background(bg)) {
        StateScaffold(head, viewModel::load, Modifier.fillMaxSize()) { h ->
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") { ChannelHeader(h.channel, subscribed) { viewModel.toggleSubscribe(h.channel) } }
                stickyHeader(key = "tabs") {
                    Row(
                        Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ChTab.entries.forEach { t ->
                            val on = tab == t
                            Column(
                                Modifier.weight(1f).clickable { viewModel.selectTab(t) }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    t.label,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.45f)
                                        .height(2.dp)
                                        .background(if (on) YouPlayRed else Color.Transparent),
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.08f)))
                }
                item { Spacer(Modifier.height(8.dp)) }
                if (tab == ChTab.HOME) {
                    if (h.shelves.isNotEmpty()) creatorShelves(h.shelves, extras.playlists, onOpenVideo, onOpenShort, onOpenPlaylist, viewModel::setShortsQueue)
                    else if (h.home.isEmpty()) item { EmptyNote("Nothing here yet") }
                    else youtubeHome(h.home, extras, onOpenVideo, onOpenShort, onOpenPlaylist, viewModel::setShortsQueue)
                } else when (val td = tabData) {
                    null, UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YouPlayRed) }
                    }
                    is UiState.Error -> item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(td.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            app.youplay.ui.components.YpGhostButton("Retry") { viewModel.selectTab(tab) }
                        }
                    }
                    is UiState.Success -> when (tab) {
                        ChTab.VIDEOS -> {
                            if (td.data.videos.isEmpty()) item { EmptyNote("No videos") }
                            items(td.data.videos, key = { "v" + it.id }) { v -> VideoCard(v, { onOpenVideo(v.id) }, Modifier.padding(horizontal = 16.dp)) }
                        }
                        ChTab.SHORTS -> {
                            if (td.data.shorts.isEmpty()) item { EmptyNote("No Shorts") }
                            items(td.data.shorts.chunked(3)) { row ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { v ->
                                        Column(Modifier.weight(1f)) { ShortThumb(v) { viewModel.setShortsQueue(td.data.shorts); onOpenShort(v.id) } }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        ChTab.PLAYLISTS -> {
                            if (td.data.playlists.isEmpty()) item { EmptyNote("No playlists") }
                            items(td.data.playlists, key = { "p" + it.id }) { p -> PlaylistRow(p, { onOpenPlaylist(p.id) }, Modifier.padding(horizontal = 16.dp)) }
                        }
                        else -> Unit
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        // floating back button over the banner
        app.youplay.ui.components.YpIconTap(
            Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack,
            modifier = Modifier.statusBarsPadding().padding(6.dp).background(Color.Black.copy(0.45f), CircleShape),
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChannelHeader(channel: ChannelItem, subscribed: Boolean, onSubscribe: () -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // banner: blurred avatar fading into the background
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            AsyncImage(
                channel.thumbnailUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp).alpha(0.55f),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(YouPlayRed.copy(0.25f), bg))))
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                channel.thumbnailUrl, channel.title, contentScale = ContentScale.Crop,
                modifier = Modifier.offset(y = (-48).dp).size(96.dp).clip(CircleShape)
                    .border(3.dp, bg, CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.offset(y = (-36).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(channel.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val stats = listOfNotNull(channel.subscriberCountText, channel.videoCountText).joinToString("  ·  ")
                if (stats.isNotBlank()) Text(stats, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                channel.description?.takeIf { it.isNotBlank() }?.let { d ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 12 else 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    app.youplay.ui.components.YpSubscribe(subscribed, onSubscribe)
                }
            }
        }
    }
}

private fun parseViews(t: String?): Double {
    val m = Regex("([0-9][0-9.,]*)\\s*([KMB]?)", RegexOption.IGNORE_CASE).find(t ?: return 0.0) ?: return 0.0
    val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0.0
    return n * when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
}

@Composable
private fun ShelfTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))

/** YouTube-style channel Home: featured latest upload, then shelves (Latest, Shorts, Popular, Playlists), then the rest. */
private fun androidx.compose.foundation.lazy.LazyListScope.youtubeHome(
    home: List<VideoItem>,
    extras: TabData,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    setQueue: (List<VideoItem>) -> Unit,
) {
    val featured = home.first()
    item(key = "featured") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            VideoCard(featured, { onOpenVideo(featured.id) })
        }
    }
    val latest = home.drop(1).take(10)
    if (latest.isNotEmpty()) {
        item(key = "latest") {
            ShelfTitle("Latest videos")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(latest, key = { "l" + it.id }) { v ->
                    app.youplay.ui.components.VideoMiniCard(
                        title = v.title, subtitle = listOfNotNull(v.viewCountText, v.publishedText).joinToString(" · "),
                        thumbnailUrl = v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg",
                        badge = v.durationText, onClick = { onOpenVideo(v.id) },
                    )
                }
            }
        }
    }
    if (extras.shorts.isNotEmpty()) item(key = "shorts") {
        app.youplay.ui.components.ShortsRowSection(
            shorts = extras.shorts.take(12),
            onClick = { v -> setQueue(extras.shorts); onOpenShort(v.id) },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    val popular = home.sortedByDescending { parseViews(it.viewCountText) }.filter { parseViews(it.viewCountText) > 0 }.take(8)
    if (popular.size >= 3) item(key = "popular") {
        ShelfTitle("Popular videos")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(popular.distinctBy { it.id }, key = { "p" + it.id }) { v ->
                app.youplay.ui.components.VideoMiniCard(
                    title = v.title, subtitle = v.viewCountText,
                    thumbnailUrl = v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg",
                    badge = v.durationText, onClick = { onOpenVideo(v.id) },
                )
            }
        }
    }
    if (extras.playlists.isNotEmpty()) item(key = "playlists") {
        ShelfTitle("Playlists")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(extras.playlists.take(10), key = { "pl" + it.id }) { p ->
                app.youplay.ui.components.VideoMiniCard(
                    title = p.title, subtitle = p.subtitle, thumbnailUrl = p.thumbnailUrl,
                    onClick = { onOpenPlaylist(p.id) },
                )
            }
        }
    }
    val rest = home.drop(11)
    if (rest.isNotEmpty()) {
        item(key = "more") { ShelfTitle("More videos") }
        items(rest, key = { "m" + it.id }) { v -> VideoCard(v, { onOpenVideo(v.id) }, Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun ShelfHeader(title: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClick != null) Text("Play all ›", color = YouPlayRed, style = MaterialTheme.typography.labelLarge)
    }
}

/** Renders the shelves exactly as the creator arranged them, then a Playlists shelf if they didn't include one. */
private fun androidx.compose.foundation.lazy.LazyListScope.creatorShelves(
    shelves: List<app.youplay.domain.ChannelShelf>,
    allPlaylists: List<PlaylistItem>,
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    setQueue: (List<VideoItem>) -> Unit,
) {
    shelves.forEachIndexed { i, sh ->
        when {
            sh.featured -> item(key = "sh$i") {
                ShelfHeader("Featured", null)
                Column(Modifier.padding(horizontal = 16.dp)) { VideoCard(sh.videos.first(), { onOpenVideo(sh.videos.first().id) }) }
            }
            sh.isShorts -> item(key = "sh$i") {
                app.youplay.ui.components.ShortsRowSection(
                    shorts = sh.videos, title = sh.title,
                    onClick = { v -> setQueue(sh.videos); onOpenShort(v.id) },
                )
            }
            sh.playlists.isNotEmpty() -> item(key = "sh$i") {
                ShelfHeader(sh.title, null)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sh.playlists, key = { "pl" + it.id }) { p ->
                        app.youplay.ui.components.VideoMiniCard(
                            title = p.title, subtitle = p.subtitle, thumbnailUrl = p.thumbnailUrl,
                            badge = "Playlist", onClick = { onOpenPlaylist(p.id) },
                        )
                    }
                }
            }
            else -> item(key = "sh$i") {
                ShelfHeader(sh.title, sh.playlistId?.let { id -> { onOpenPlaylist(id) } })
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sh.videos, key = { "v" + it.id }) { v ->
                        app.youplay.ui.components.VideoMiniCard(
                            title = v.title, subtitle = listOfNotNull(v.viewCountText, v.publishedText).joinToString(" · "),
                            thumbnailUrl = v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg",
                            badge = v.durationText, onClick = { onOpenVideo(v.id) },
                        )
                    }
                }
            }
        }
    }
    if (shelves.none { it.playlists.isNotEmpty() } && allPlaylists.isNotEmpty()) item(key = "all_playlists") {
        ShelfHeader("Playlists", null)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(allPlaylists.take(12), key = { "ap" + it.id }) { p ->
                app.youplay.ui.components.VideoMiniCard(
                    title = p.title, subtitle = p.subtitle, thumbnailUrl = p.thumbnailUrl,
                    badge = "Playlist", onClick = { onOpenPlaylist(p.id) },
                )
            }
        }
    }
}
