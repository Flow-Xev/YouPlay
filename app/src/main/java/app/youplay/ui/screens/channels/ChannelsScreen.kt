package app.youplay.ui.screens.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.youplay.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.domain.ChannelItem
import app.youplay.ui.components.ChannelRow
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.VideoCard
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onBack: (() -> Unit)? = null,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val detail by viewModel.state.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (detail == null) "Channels" else "Channel") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val page = detail) {
            null -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    item {
                        Text("Subscriptions", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (subscriptions.isEmpty()) {
                        item {
                            Text(
                                "Subscribe to channels from search or watch pages.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(subscriptions, key = { it.channelId }) { sub ->
                            ChannelRow(
                                channel = ChannelItem(sub.channelId, sub.title, sub.thumbnailUrl),
                                onClick = { onOpenChannel(sub.channelId) },
                            )
                        }
                    }
                }
            }
            else -> StateScaffold(
                state = page,
                onRetry = { /* handled via VM init */ },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { data ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        val ch = data.channel
                        if (ch != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = ch.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ch.title, style = MaterialTheme.typography.titleLarge)
                                    ch.subscriberCountText?.let {
                                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (subscribed) {
                                OutlinedButton(onClick = { viewModel.toggleSubscribe(ch) }) { Text("Subscribed") }
                            } else {
                                Button(onClick = { viewModel.toggleSubscribe(ch) }) { Text("Subscribe") }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Videos", style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Text("Channel details unavailable", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(data.videos, key = { it.id }) { video ->
                        VideoCard(video, onClick = { onOpenVideo(video.id) })
                    }
                }
            }
        }
    }
}
