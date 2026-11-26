package app.youplay.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.domain.FeedEntry
import app.youplay.ui.components.FeedSkeleton
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.VideoMiniCard
import app.youplay.ui.player.formatTime
import app.youplay.ui.theme.YouPlayRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenVideo: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onSearch: () -> Unit = {},
    onSignIn: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chipRow by viewModel.chipRow.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val showShorts by viewModel.showShorts.collectAsStateWithLifecycle()
    val resume by viewModel.continueWatching.collectAsStateWithLifecycle()
    val accountVm: app.youplay.ui.screens.account.AccountViewModel = hiltViewModel()
    val accounts by accountVm.accounts.collectAsStateWithLifecycle()
    val busy by accountVm.busy.collectAsStateWithLifecycle()
    val message by accountVm.message.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val (chips, selected) = chipRow

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        HomeTopBar(onSearch = onSearch, account = account, onAccount = { sheet = true })
        if (sheet) app.youplay.ui.components.AccountSheet(
            accounts = accounts, activeId = account.id, busy = busy, message = message,
            onDismiss = { sheet = false; accountVm.dismiss() }, onSwitch = accountVm::switchTo, onAdd = onSignIn,
            onSync = accountVm::pull, onSignOut = accountVm::signOut, onSignOutAll = accountVm::signOutAll,
        )
        if (chips.size > 1) HomeChipRow(chips.map { it.label }, selected) { viewModel.selectChip(chips[it]) }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; viewModel.refresh(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            StateScaffold(state = state, onRetry = viewModel::refresh, loading = { FeedSkeleton() }) { ui ->
                val listState = rememberLazyListState()
                val shown = if (showShorts) ui.entries else ui.entries.filterNot { it is FeedEntry.ShortsRow }
                // Near the end of the list -> ask for more (videos while the account has them, then Shorts).
                LaunchedEffect(listState, shown.size, ui.loadingMore, ui.continuation) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last -> if (last >= shown.size - 3) viewModel.loadMore() }
                }
                val resumeAt = shown.indexOfFirst { it is FeedEntry.ShortsRow }.let { if (it >= 0) it else 0 }
                val showResume = selected == "All" && resume.isNotEmpty() && shown.isNotEmpty()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                    if (!account.signedIn) item(key = "signin") { HomeSignInCard(onSignIn) }
                    itemsIndexed(
                        shown,
                        key = { _, e -> when (e) { is FeedEntry.Video -> "v" + e.item.id; is FeedEntry.ShortsRow -> "s" + e.shorts.first().id } },
                    ) { index, entry ->
                        when (entry) {
                            is FeedEntry.Video -> HomeVideoCard(video = entry.item, onClick = { onOpenVideo(entry.item.id) })
                            is FeedEntry.ShortsRow -> HomeShortsRow(
                                shorts = entry.shorts,
                                onClick = { v -> viewModel.setShortsQueue(entry.shorts); onOpenShort(v.id) },
                            )
                        }
                        // Continue watching sits right after the first Shorts row (or after the first video if there is none).
                        if (showResume && index == resumeAt) ContinueWatchingRow(resume, onOpenVideo)
                    }
                    if (shown.isEmpty() && !ui.loadingMore) item(key = "empty") {
                        HomeEndCard("Nothing here yet. Pull down to refresh.")
                    }
                    if (ui.loadingMore) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YouPlayRed)
                        }
                    } else if (ui.endReached && shown.isNotEmpty()) item(key = "end") {
                        HomeEndCard("You're all caught up")
                    }
                }
            }
        }
    }
}

/** Videos with saved resume progress, as a shelf between feed entries. */
@Composable
private fun ContinueWatchingRow(
    resume: List<app.youplay.data.local.WatchHistoryEntity>,
    onOpenVideo: (String) -> Unit,
) {
    Column(Modifier.padding(bottom = 22.dp)) {
        HomeSectionTitle("Continue watching")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            items(resume, key = { "r" + it.videoId }) { h ->
                VideoMiniCard(h.title, h.channelName, h.thumbnailUrl, { onOpenVideo(h.videoId) }, badge = formatTime(h.progressMs))
            }
        }
    }
}
