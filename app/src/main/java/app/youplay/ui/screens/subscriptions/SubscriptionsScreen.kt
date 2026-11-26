package app.youplay.ui.screens.subscriptions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import app.youplay.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.ui.components.BrandBar
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.feedEntries
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenChannels: () -> Unit,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }

    Scaffold(topBar = { BrandBar(onSearch = onSearch) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val s = state
            if (!account.signedIn || s == null) {
                Column(Modifier.fillMaxSize()) {
                    ChannelStrip(channels, onOpenChannel, onOpenChannels)
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (account.signedIn) "Loading…" else "Sign in to see new videos from your subscriptions.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!account.signedIn) Button(onClick = onSignIn, modifier = Modifier.padding(top = 12.dp)) { Text("Sign in") }
                    }
                }
            } else PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) { StateScaffold(state = s, onRetry = viewModel::load) { ui ->
                val listState = rememberLazyListState()
                LaunchedEffect(listState, ui.entries.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last -> if (last >= ui.entries.size - 2) viewModel.loadMore() }
                }
                var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val shortsAll = androidx.compose.runtime.remember(ui.entries) {
                    ui.entries.filterIsInstance<app.youplay.domain.FeedEntry.ShortsRow>().flatMap { it.shorts }.distinctBy { it.id }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "strip") { ChannelStrip(channels, onOpenChannel, onOpenChannels) }
                    item(key = "chips") {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("All", "Videos", "Shorts").forEachIndexed { i, l ->
                                app.youplay.ui.components.YpChip(l, mode == i) { mode = i }
                            }
                        }
                    }
                    if (mode == 2) items(shortsAll.chunked(3), key = { row -> "sg" + row.first().id }) { row ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { v ->
                                Box(Modifier.weight(1f)) {
                                    app.youplay.ui.components.ShortThumb(v) { viewModel.setShortsQueue(shortsAll); onOpenShort(v.id) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (mode != 2) feedEntries(
                        if (mode == 1) ui.entries.filterIsInstance<app.youplay.domain.FeedEntry.Video>() else ui.entries, onOpenVideo,
                        onShortClick = { list, v -> viewModel.setShortsQueue(list); onOpenShort(v.id) },
                    )
                    if (ui.loadingMore) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YouPlayRed)
                        }
                    }
                }
            } }
        }
    }
}

@Composable
private fun ChannelStrip(
    channels: List<app.youplay.data.local.SubscriptionEntity>,
    onOpenChannel: (String) -> Unit,
    onAll: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Subscriptions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("All", color = YouPlayRed) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(channels, key = { it.channelId }) { c ->
                Column(
                    Modifier.width(68.dp).clickable { onOpenChannel(c.channelId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(c.thumbnailUrl, c.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(CircleShape))
                    Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
