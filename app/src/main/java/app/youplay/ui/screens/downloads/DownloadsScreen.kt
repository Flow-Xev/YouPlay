package app.youplay.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.youplay.data.download.OfflineDownloads
import app.youplay.data.local.DOWNLOAD_DONE
import app.youplay.data.local.DOWNLOAD_FAILED
import app.youplay.data.local.DownloadEntity
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val offline: OfflineDownloads) : ViewModel() {
    val items: StateFlow<List<DownloadEntity>> = offline.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: String) = offline.delete(id)
    fun retry(d: DownloadEntity) = offline.start(d.videoId, d.title, d.channelName, d.thumbnailUrl)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%d KB".format(bytes / 1024)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing downloaded yet.\nOpen a video and tap Download.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(items, key = { it.videoId }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        when (d.status) { DOWNLOAD_DONE -> onOpenVideo(d.videoId); DOWNLOAD_FAILED -> viewModel.retry(d) }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        d.thumbnailUrl, d.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(d.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        when (d.status) {
                            DOWNLOAD_DONE -> Text("Saved · ${formatSize(d.sizeBytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DOWNLOAD_FAILED -> Text("Failed — tap to retry", style = MaterialTheme.typography.labelMedium, color = YouPlayRed)
                            else -> Column {
                                Text("Downloading ${d.progress}%", style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(progress = { d.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = YouPlayRed)
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.delete(d.videoId) }) {
                        Icon(if (d.status == DOWNLOAD_DONE || d.status == DOWNLOAD_FAILED) Icons.Filled.Delete else Icons.Filled.Close, "Remove")
                    }
                }
            }
        }
    }
}
