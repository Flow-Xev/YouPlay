package app.youplay.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.data.local.WatchHistoryEntity
import app.youplay.ui.player.formatTime
import app.youplay.ui.screens.library.LibraryViewModel
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage
import java.util.Calendar

private fun dayBucket(t: Long): String {
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val day = 86_400_000L
    return when {
        t >= today -> "Today"
        t >= today - day -> "Yesterday"
        t >= today - 6 * day -> "This week"
        t >= today - 29 * day -> "This month"
        else -> "Older"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val data by viewModel.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(data.history, query) {
        val q = query.trim()
        if (q.isEmpty()) data.history
        else data.history.filter { it.title.contains(q, true) || it.channelName.contains(q, true) }
    }
    // Rows in order: a header string, or a history item
    val rows: List<Any> = remember(shown) {
        buildList {
            var last = ""
            shown.forEach { h ->
                val b = dayBucket(h.watchedAt)
                if (b != last) { add(b); last = b }
                add(h)
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                if (searching) TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search watch history") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                ) else Text("History")
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = { if (searching) { searching = false; query = "" } else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                androidx.compose.material3.IconButton(onClick = { if (searching) query = "" else searching = true }, enabled = data.history.isNotEmpty()) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, if (searching) "Clear search" else "Search")
                }
                androidx.compose.material3.IconButton(onClick = { confirmClear = true }, enabled = data.history.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteOutline, "Clear history")
                }
            },
        )
    }) { padding ->
        when {
            data.history.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No watch history", style = MaterialTheme.typography.titleMedium)
                Text("Videos you watch will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shown.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No matches", style = MaterialTheme.typography.titleMedium)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(rows, key = { if (it is WatchHistoryEntity) "v" + it.videoId else "h$it" }) { row ->
                    if (row is String) {
                        Text(row, style = MaterialTheme.typography.titleSmall, color = YouPlayRed, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                    } else if (row is WatchHistoryEntity) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenVideo(row.videoId) }.padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = row.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 132.dp, height = 74.dp).clip(MaterialTheme.shapes.small),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(row.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                if (row.progressMs > 10_000) Text("Resume at " + formatTime(row.progressMs), style = MaterialTheme.typography.labelSmall, color = YouPlayRed)
                            }
                            androidx.compose.material3.IconButton(onClick = { viewModel.removeFromHistory(row.videoId) }) {
                                Icon(Icons.Filled.Close, "Remove from history", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear watch history?") },
        text = { Text("This removes all ${data.history.size} videos from your history on this device.") },
        confirmButton = { TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) { Text("Clear", color = YouPlayRed) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
