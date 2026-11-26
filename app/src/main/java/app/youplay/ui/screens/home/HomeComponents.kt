package app.youplay.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youplay.domain.AccountState
import app.youplay.domain.VideoItem
import app.youplay.ui.components.IconButton
import app.youplay.ui.components.VideoMenu
import app.youplay.ui.components.YpAvatar
import app.youplay.ui.components.YpChip
import app.youplay.ui.components.bounceClick
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage

private val Panel = Color(0xFF141414)
private val Muted = Color(0xFFA3A3A3)

/**
 * Top bar: the YouPlay logo with a soft red glow, a two-tone wordmark, a search button and the account avatar
 * (opens the account switcher). A faint red wash fades out behind the status bar.
 */
@Composable
fun HomeTopBar(onSearch: () -> Unit, account: AccountState = AccountState(), onAccount: () -> Unit = {}) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(YouPlayRed.copy(alpha = 0.16f), Color.Transparent))),
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp)
                    .drawBehind {
                        val r = 30.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(listOf(YouPlayRed.copy(alpha = 0.40f), Color.Transparent), center = center, radius = r),
                            radius = r,
                        )
                    }
                    .graphicsLayer {
                        val k = 0.8f + 0.2f * enter.value
                        scaleX = k; scaleY = k; alpha = enter.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = app.youplay.R.drawable.ic_logo, contentDescription = "YouPlay", modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("You") }
                    withStyle(SpanStyle(color = YouPlayRed)) { append("Play") }
                },
                fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(2.dp, YouPlayRed, CircleShape).clickable(onClick = onAccount),
                contentAlignment = Alignment.Center,
            ) {
                YpAvatar(if (account.signedIn) account.avatarUrl else null, if (account.signedIn) account.name ?: "?" else "Guest", 32.dp)
            }
        }
    }
}

/** Filter chips that stay put while a chip's feed loads. */
@Composable
fun HomeChipRow(labels: List<String>, selected: String, onSelect: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels.size) { i -> YpChip(labels[i], labels[i] == selected) { onSelect(i) } }
    }
}

/** Full-width feed card: big rounded thumbnail with gradient + duration, channel avatar, 2-line title, meta, menu. */
@Composable
fun HomeVideoCard(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Column(
        modifier
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 24.dp.toPx() }
            .fillMaxWidth()
            .bounceClick(pressed = 0.978f, onClick = onClick)
            .padding(horizontal = 12.dp)
            .padding(bottom = 22.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Panel),
        ) {
            AsyncImage(
                model = video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))),
            )
            if (!video.durationText.isNullOrBlank()) {
                Text(
                    video.durationText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color.Black.copy(0.8f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            YpAvatar(video.channelAvatarUrl, video.channelName.ifBlank { "?" }, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    video.channelName.takeIf { it.isNotBlank() }, video.viewCountText, video.publishedText,
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
                if (menu) VideoMenu(video, expanded = true, onDismiss = { menu = false })
            }
        }
    }
}

/** One Home Shorts row: a panel with a Shorts header and exactly the 6 Shorts it was given. */
@Composable
fun HomeShortsRow(shorts: List<VideoItem>, onClick: (VideoItem) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 22.dp)
            .clip(RoundedCornerShape(22.dp)).background(Panel).padding(top = 12.dp, bottom = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(YouPlayRed),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("Shorts", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${shorts.size} for you", color = Muted, fontSize = 12.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shorts, key = { it.id }) { v -> HomeShortCard(v) { onClick(v) } }
        }
    }
}

@Composable
private fun HomeShortCard(v: VideoItem, onClick: () -> Unit) {
    Box(
        Modifier.width(140.dp).aspectRatio(9f / 16f)
            .bounceClick(pressed = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF222222)),
    ) {
        AsyncImage(
            v.thumbnailUrl ?: "https://i.ytimg.com/vi/${v.id}/hq720.jpg", v.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(0.82f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                v.title, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            v.viewCountText?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(0.75f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Shelf title used above "Continue watching". */
@Composable
fun HomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, top = 6.dp, bottom = 10.dp),
    )
}

/** Shown to guests: Home is only "from your account" once signed in. */
@Composable
fun HomeSignInCard(onSignIn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(YouPlayRed.copy(0.30f), Panel)))
            .clickable(onClick = onSignIn).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make Home yours", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("You're seeing popular videos. Sign in for recommendations, Shorts, likes and subscriptions from your YouTube account.", color = Color.White.copy(0.75f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Sign in", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun HomeEndCard(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 13.sp)
    }
}
