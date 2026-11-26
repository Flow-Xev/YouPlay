package app.youplay.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.domain.SearchFilter
import app.youplay.domain.SearchType
import app.youplay.ui.components.ChannelRow
import app.youplay.ui.components.CompactSearchRow
import app.youplay.ui.components.PlaylistRow
import app.youplay.ui.components.ShortThumb
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.YpChip
import app.youplay.ui.components.YpIconTap
import app.youplay.ui.components.YpPrimaryButton
import app.youplay.ui.theme.YouPlayRed
import app.youplay.domain.FeedEntry

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YpIconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF212121))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text("Search YouPlay", color = Color(0xFF888888), fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(YouPlayRed),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                YpIconTap(Icons.Default.Close, "Clear", { viewModel.onQueryChange("") })
            }
        }

        val s = state
        if (s == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isNotBlank() && suggestions.isNotEmpty()) {
                    items(suggestions) { sug ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.search(sug) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color(0xFFAAAAAA))
                            Text(sug, Modifier.weight(1f).padding(horizontal = 14.dp), color = Color.White)
                            YpIconTap(Icons.Default.NorthWest, "Use", { viewModel.onQueryChange(sug) }, size = 36.dp, iconSize = 18.dp)
                        }
                    }
                } else {
                    if (history.isNotEmpty()) item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("Clear", color = YouPlayRed, modifier = Modifier.clickable(onClick = viewModel::clearHistory))
                        }
                    }
                    items(history, key = { it.query }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.search(h.query) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.History, null, tint = Color(0xFFAAAAAA))
                            Text(h.query, Modifier.weight(1f).padding(horizontal = 14.dp), color = Color.White)
                            YpIconTap(Icons.Default.Close, "Remove", { viewModel.removeHistory(h.query) }, size = 36.dp, iconSize = 18.dp)
                        }
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { YpChip("Filters", false) { showFilters = true } }
                items(SearchType.entries.toList()) { t ->
                    YpChip(t.label, filter.type == t) { viewModel.setType(t) }
                }
            }
            StateScaffold(s, { viewModel.search() }) { ui ->
                if (filter.type == SearchType.SHORTS) {
                    if (ui.shorts.isEmpty()) Text("No Shorts found", Modifier.padding(16.dp), color = Color(0xFFAAAAAA))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(ui.shorts, key = { "v" + it.id }) { v ->
                            CompactSearchRow(
                                id = v.id, title = v.title, channelName = v.channelName,
                                meta = v.viewCountText, thumbnailUrl = v.thumbnailUrl, durationText = v.durationText,
                                onClick = { viewModel.setShortsQueue(ui.shorts); onOpenShort(v.id) },
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    val total = ui.entries.size
                    LaunchedEffect(listState, total) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .collect { last -> if (last >= total - 2) viewModel.loadMore() }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(ui.channels.take(3), key = { "c" + it.id }) { c ->
                            ChannelRow(c, { onOpenChannel(c.id) }, Modifier.padding(horizontal = 16.dp))
                        }
                        items(
                            ui.entries.filterIsInstance<FeedEntry.Video>(),
                            key = { "v" + it.item.id },
                        ) { e ->
                            val v = e.item
                            CompactSearchRow(
                                id = v.id, title = v.title, channelName = v.channelName,
                                meta = listOfNotNull(v.viewCountText, v.publishedText).joinToString(" · ").ifBlank { null },
                                thumbnailUrl = v.thumbnailUrl, durationText = v.durationText,
                                onClick = { onOpenVideo(v.id) },
                            )
                        }
                        items(ui.entries.filterIsInstance<FeedEntry.ShortsRow>(), key = { "s" + it.shorts.firstOrNull()?.id }) { row ->
                            app.youplay.ui.components.ShortsRowSection(row.shorts, {
                                viewModel.setShortsQueue(row.shorts); onOpenShort(it.id)
                            })
                        }
                        items(ui.playlists, key = { "p" + it.id }) { p ->
                            PlaylistRow(p, { onOpenPlaylist(p.id) }, Modifier.padding(horizontal = 16.dp))
                        }
                        if (ui.loadingMore) item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = YouPlayRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) FilterDialog(filter, { showFilters = false }) { viewModel.setFilter(it); showFilters = false }
}

@Composable
private fun FilterDialog(current: SearchFilter, onDismiss: () -> Unit, onApply: (SearchFilter) -> Unit) {
    var f by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .padding(16.dp),
        ) {
            Text("Search filters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            @Composable
            fun group(title: String, options: List<Pair<String, Int?>>, selected: Int?, pick: (Int?) -> Unit) {
                Text(title, color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options) { (label, v) ->
                        YpChip(label, selected == v) { pick(v) }
                    }
                }
            }
            group("Upload date", listOf("Any" to null, "Hour" to 1, "Today" to 2, "Week" to 3, "Month" to 4, "Year" to 5), f.date) { f = f.copy(date = it) }
            group("Duration", listOf("Any" to null, "< 4 min" to 1, "4–20 min" to 3, "> 20 min" to 2), f.duration) { f = f.copy(duration = it) }
            group("Sort by", listOf("Relevance" to null, "Upload date" to 2, "Views" to 3, "Rating" to 1), f.sort) { f = f.copy(sort = it) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                Text("Reset", color = Color(0xFFAAAAAA), modifier = Modifier.clickable { onApply(SearchFilter(type = current.type)) }.padding(8.dp))
                YpPrimaryButton("Apply") { onApply(f) }
            }
        }
    }
}
