package app.youplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youplay.domain.ChannelItem
import app.youplay.domain.UiState
import app.youplay.domain.VideoItem
import app.youplay.ui.theme.YouPlayBlack
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage

@Composable
fun ChannelInitial(name: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val bg = Color.hsv(((name.hashCode() and 0x7fffffff) % 360).toFloat(), 0.45f, 0.55f)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val enter = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Column(
        modifier = modifier
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 28.dp.toPx() }
            .fillMaxWidth()
            .bounceClick(pressed = 0.975f, onClick = onClick)
            .padding(bottom = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (!video.durationText.isNullOrBlank()) {
                Text(
                    text = video.durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            YpAvatar(video.channelAvatarUrl, video.channelName.ifBlank { "?" }, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    video.channelName.takeIf { it.isNotBlank() }, video.viewCountText, video.publishedText,
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "More")
                }
                if (menu) VideoMenu(video, expanded = true, onDismiss = { menu = false })
            }
        }
    }
}

/** Slim filter chips (Flow / YouTube — no Material FilterChip). */
@Composable
fun PillChips(
    labels: List<String>,
    selected: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(labels) { i, label ->
            val on = label == selected
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Color.Black else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Shimmer-ish placeholder cards shown while a feed loads. */
@Composable
fun FeedSkeleton() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(
        -400f, 1400f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "x",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val hi = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val c = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(base, hi, base),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 400f, 200f),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(c))
            Row(Modifier.padding(top = 10.dp, bottom = 20.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(c))
                }
            }
        }
    }
}

@Composable
fun ChannelRow(
    channel: ChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = channel.thumbnailUrl,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(channel.subscriberCountText, channel.videoCountText).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun <T> StateScaffold(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { CircularProgressIndicator(color = YouPlayRed) },
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            UiState.Loading -> loading()
            is UiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                YpPrimaryButton("Retry", onClick = onRetry)
            }
            is UiState.Success -> content(state.data)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun BrandBar(modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = app.youplay.R.drawable.ic_logo,
            contentDescription = "YouPlay",
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "YouPlay",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (onSearch != null) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun ShortsRowSection(
    shorts: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Shorts",
) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = YouPlayRed)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shorts.size) { i ->
                val v = shorts[i]
                Box(
                    Modifier
                        .width(128.dp).aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onClick(v) },
                ) {
                    AsyncImage(v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", v.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
                    Text(v.title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }
    }
}

/** Shared renderer for feed rows (videos + Shorts carousels). */
fun androidx.compose.foundation.lazy.LazyListScope.feedEntries(
    entries: List<app.youplay.domain.FeedEntry>,
    onOpenVideo: (String) -> Unit,
    onShortClick: (List<VideoItem>, VideoItem) -> Unit,
) {
    itemsIndexed(
        entries,
        key = { i, e -> when (e) { is app.youplay.domain.FeedEntry.Video -> "v${i}_" + e.item.id; is app.youplay.domain.FeedEntry.ShortsRow -> "s$i" } },
    ) { _, e ->
        when (e) {
            is app.youplay.domain.FeedEntry.Video -> VideoCard(e.item, { onOpenVideo(e.item.id) }, Modifier.padding(horizontal = 16.dp))
            is app.youplay.domain.FeedEntry.ShortsRow -> ShortsRowSection(e.shorts, { onShortClick(e.shorts, it) })
        }
    }
}

@Composable
fun ShortThumb(v: VideoItem, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
    ) {
        AsyncImage(v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", v.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
        Text(v.title, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
fun PlaylistRow(p: app.youplay.domain.PlaylistItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(p.thumbnailUrl, p.title, contentScale = ContentScale.Crop,
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun VideoMiniCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Column(modifier.width(168.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(thumbnailUrl, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (badge != null) Text(
                badge, style = MaterialTheme.typography.labelSmall, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(Color.Black.copy(0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (!subtitle.isNullOrBlank()) Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
