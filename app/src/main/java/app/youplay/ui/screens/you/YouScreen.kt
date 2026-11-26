package app.youplay.ui.screens.you

import androidx.compose.ui.unit.sp
import app.youplay.ui.components.bounceClick
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import app.youplay.ui.components.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.data.local.DOWNLOAD_DONE
import app.youplay.ui.components.BrandBar
import app.youplay.ui.components.VideoMiniCard
import app.youplay.ui.screens.account.AccountViewModel
import app.youplay.ui.screens.downloads.formatSize
import app.youplay.ui.screens.library.LibraryViewModel
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage

/** "You" tab: account + library merged into one page. */
@Composable
fun YouScreen(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChannels: () -> Unit,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    library: LibraryViewModel = hiltViewModel(),
    accountVm: AccountViewModel = hiltViewModel(),
) {
    val data by library.state.collectAsStateWithLifecycle()
    val playlists by library.playlists.collectAsStateWithLifecycle()
    val downloads by library.downloads.collectAsStateWithLifecycle()
    val pending by library.pending.collectAsStateWithLifecycle()
    val account by accountVm.account.collectAsStateWithLifecycle()
    val busy by accountVm.busy.collectAsStateWithLifecycle()
    val message by accountVm.message.collectAsStateWithLifecycle()
    val accounts by accountVm.accounts.collectAsStateWithLifecycle()
    val saved by library.saved.collectAsStateWithLifecycle()
    var sheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (sheet) app.youplay.ui.components.AccountSheet(
        accounts = accounts, activeId = account.id, busy = busy, message = message,
        onDismiss = { sheet = false; accountVm.dismiss() }, onSwitch = accountVm::switchTo, onAdd = onSignIn,
        onSync = accountVm::pull, onSignOut = accountVm::signOut, onSignOutAll = accountVm::signOutAll,
    )

    Scaffold(topBar = { BrandBar(onSearch = onSearch) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            // ---- account header: tap to switch / add accounts ----
            item("account") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { sheet = true }
                            .background(androidx.compose.ui.graphics.Color(0xFF141414)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = account.avatarUrl ?: app.youplay.R.drawable.ic_logo, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(if (account.signedIn) CircleShape else RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (account.signedIn) account.name ?: "Signed in" else "Guest", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            val sub = listOfNotNull(account.handle, account.email).joinToString(" · ")
                            Text(
                                when {
                                    account.signedIn && accounts.size > 1 -> "${accounts.size} accounts · tap to switch"
                                    account.signedIn -> sub.ifBlank { "Tap to manage accounts" }
                                    else -> "Your library is saved on this device"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = YouPlayRed)
                    if (pending > 0) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$pending change${if (pending == 1) "" else "s"} waiting to sync with YouTube",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (account.signedIn) TextButton(onClick = library::syncNow) { Text("Sync now", color = YouPlayRed) }
                    }
                    if (!account.signedIn) Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Sign in to sync with YouTube")
                    }
                }
            }

            item("tiles") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibTile(Icons.Filled.WatchLater, "Watch later", "${saved.size} saved", Modifier.weight(1f)) { onOpenPlaylist("WL") }
                        LibTile(Icons.Filled.ThumbUp, "Liked videos", "${data.liked.size} saved", Modifier.weight(1f)) { onOpenPlaylist("LL") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibTile(Icons.Filled.Download, "Downloads", "${downloads.size} videos", Modifier.weight(1f), onOpenDownloads)
                        LibTile(Icons.Filled.VideoLibrary, "Channels", "${data.subscriptions.size} subscribed", Modifier.weight(1f), onOpenChannels)
                    }
                }
            }
            // ---- rows ----
            if (data.history.isNotEmpty()) item("history") {
                RowSection("History", action = "All", onAction = onOpenHistory) {
                    items(data.history.take(15), key = { "h" + it.videoId }) { h ->
                        VideoMiniCard(h.title, h.channelName, h.thumbnailUrl, { onOpenVideo(h.videoId) })
                    }
                }
            } else item("history") {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenHistory).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenHistory) { Text("All", color = YouPlayRed) }
                }
            }
            if (saved.isNotEmpty()) item("wl") {
                RowSection("Watch later", action = "All", onAction = { onOpenPlaylist("WL") }) {
                    items(saved.take(20), key = { "w" + it.videoId }) {
                        VideoMiniCard(it.title, it.channelName, it.thumbnailUrl, { onOpenVideo(it.videoId) })
                    }
                }
            }
            if (playlists.isNotEmpty()) item("p") {
                RowSection("Playlists") {
                    items(playlists.distinctBy { it.id }, key = { "p" + it.id }) {
                        VideoMiniCard(it.title, it.subtitle, it.thumbnailUrl, { onOpenPlaylist(it.id) })
                    }
                }
            }
            item("d") {
                if (downloads.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenDownloads).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Download, null); Spacer(Modifier.width(12.dp))
                        Text("Downloads", style = MaterialTheme.typography.titleMedium)
                    }
                } else RowSection("Downloads", action = "See all", onAction = onOpenDownloads) {
                    items(downloads, key = { "d" + it.videoId }) {
                        VideoMiniCard(
                            it.title, it.channelName, it.thumbnailUrl,
                            { if (it.status == DOWNLOAD_DONE) onOpenVideo(it.videoId) else onOpenDownloads() },
                            badge = if (it.status == DOWNLOAD_DONE) formatSize(it.sizeBytes) else "${it.progress}%",
                        )
                    }
                }
            }
            if (data.liked.isNotEmpty()) item("l") {
                RowSection("Liked videos") {
                    items(data.liked, key = { "l" + it.videoId }) {
                        VideoMiniCard(it.title, it.channelName, it.thumbnailUrl, { onOpenVideo(it.videoId) })
                    }
                }
            }
            if (data.subscriptions.isNotEmpty()) item("s") {
                RowSection("Subscriptions", action = "Manage", onAction = onOpenChannels) {
                    items(data.subscriptions, key = { "s" + it.channelId }) { sub ->
                        Column(Modifier.width(76.dp).clickable { onOpenChannel(sub.channelId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                sub.thumbnailUrl, sub.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text(sub.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (data.history.isEmpty() && data.liked.isEmpty() && playlists.isEmpty()) item("empty") {
                Text(
                    "Videos you watch, like or save for later show up here, even when you're signed out.",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowSection(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action, color = YouPlayRed) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun LibTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1A1A1A)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
            if (sub != null) Text(sub, color = androidx.compose.ui.graphics.Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
        }
    }
}
