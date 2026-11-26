package app.youplay.ui.screens.shorts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.data.remote.Purpose
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.StreamInfo
import app.youplay.domain.VideoItem
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.StatusBarScrim
import app.youplay.ui.components.StatusBarStyle
import app.youplay.ui.components.YpAvatar
import app.youplay.ui.components.YpIconTap
import app.youplay.ui.components.bounceClick
import app.youplay.ui.player.VideoPlayer
import app.youplay.ui.theme.YouPlayBlack
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShortsRepoEntryPoint {
    fun videoRepository(): VideoRepository
    fun playerController(): app.youplay.ui.player.PlayerController
}

/**
 * Shorts player. Own design: tap = pause, double-tap = like (heart burst, sent to your account), hold = 2x,
 * a bottom action dock instead of a side rail, a scrubbable seek line, mute and auto-scroll that stick
 * across Shorts, and a top-right menu (speed, auto-scroll, description, share, copy, watch later).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shortsQuality by viewModel.shortsQuality.collectAsStateWithLifecycle()
    val preloadShorts by viewModel.preloadShorts.collectAsStateWithLifecycle()
    val startMuted by viewModel.startMuted.collectAsStateWithLifecycle()
    var muted by remember(startMuted) { mutableStateOf(startMuted) }
    val startAuto by viewModel.startAutoScroll.collectAsStateWithLifecycle()
    var autoScroll by remember(startAuto) { mutableStateOf(startAuto) }
    var userSpeed by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    StateScaffold(state = state, onRetry = viewModel::refresh, modifier = Modifier.fillMaxSize()) { shorts ->
        if (shorts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Shorts available right now", color = Color.White)
            }
            return@StateScaffold
        }
        val pagerState = rememberPagerState(pageCount = { shorts.size })
        LaunchedEffect(pagerState.currentPage, shorts.size, preloadShorts, shortsQuality) { viewModel.onPage(pagerState.currentPage, preloadShorts) }
        // Shorts is full-bleed video: white status bar icons, plus a faint fade behind the bar so they stay legible.
        StatusBarStyle(lightIcons = true)
        Box(Modifier.fillMaxSize().background(YouPlayBlack)) {
            VerticalPager(
                state = pagerState, modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1, key = { shorts[it].id },
            ) { page ->
                val item = shorts[page]
                ShortPage(
                    videoId = item.id, quality = shortsQuality, title = item.title, channelName = item.channelName,
                    channelId = item.channelId, thumb = item.thumbnailUrl,
                    active = pagerState.currentPage == page,
                    scrolling = pagerState.isScrollInProgress,
                    muted = muted, onMuted = { muted = it },
                    autoScroll = autoScroll, onAutoScroll = { autoScroll = it },
                    userSpeed = userSpeed, onSpeed = { userSpeed = it },
                    onNext = { if (page + 1 < shorts.size) scope.launch { pagerState.animateScrollToPage(page + 1) } },
                    onBack = onBack,
                )
            }
            StatusBarScrim(modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun ShortPage(
    videoId: String, quality: Int, title: String, channelName: String, channelId: String?, thumb: String?, active: Boolean, scrolling: Boolean,
    muted: Boolean, onMuted: (Boolean) -> Unit,
    autoScroll: Boolean, onAutoScroll: (Boolean) -> Unit,
    userSpeed: Float, onSpeed: (Float) -> Unit,
    onNext: () -> Unit, onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stream by remember(videoId) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(videoId) { mutableStateOf<String?>(null) }
    var menu by remember(videoId) { mutableStateOf(false) }
    var reloads by remember(videoId) { mutableStateOf(0) }
    var forceFresh by remember(videoId) { mutableStateOf(false) }
    var showComments by remember(videoId) { mutableStateOf(false) }
    var showDesc by remember(videoId) { mutableStateOf(false) }
    var extras by remember(videoId) { mutableStateOf<app.youplay.domain.WatchExtras?>(null) }
    var voteStats by remember(videoId) { mutableStateOf<app.youplay.data.remote.VoteStats?>(null) }
    var paused by remember(videoId) { mutableStateOf(false) }
    var fast by remember(videoId) { mutableStateOf(false) }
    var exo by remember(videoId) { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var pos by remember(videoId) { mutableLongStateOf(0L) }
    var dur by remember(videoId) { mutableLongStateOf(0L) }
    var burst by remember(videoId) { mutableStateOf<Pair<Offset, Int>?>(null) }
    var flash by remember(videoId) { mutableStateOf(0) }

    // Like / dislike / subscribe state comes from the account-synced database.
    val repo = remember { repoOf(context) }
    val shortsDataSource = remember { playerControllerOf(context).shortsDataSourceFactory() }
    val liked by repo.observeLiked(videoId).collectAsState(false)
    val disliked by repo.observeDisliked(videoId).collectAsState(false)
    val subChannel = stream?.channelId ?: channelId
    val subscribed by remember(subChannel) {
        if (subChannel == null) kotlinx.coroutines.flow.flowOf(false) else repo.observeSubscribed(subChannel)
    }.collectAsState(false)
    val name = stream?.channelName?.takeIf { it.isNotBlank() } ?: channelName
    val toast = { msg: String -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }

    LaunchedEffect(active, showDesc) {
        if ((active || showDesc) && extras == null) extras = runCatching { repo.watchExtras(videoId) }.getOrNull()
        if (active && voteStats == null) voteStats = runCatching { repo.voteStats(videoId) }.getOrNull()
    }
    LaunchedEffect(videoId, active, reloads) {
        if (!active || stream != null) return@LaunchedEffect
        val fresh = forceFresh
        runCatching {
            withContext(Dispatchers.IO) {
                if (fresh) repo.stream(videoId, quality, record = false, fresh = true, purpose = Purpose.SHORTS)
                else runCatching { repo.stream(videoId, quality, purpose = Purpose.SHORTS) }
                    .recoverCatching { repo.stream(videoId, quality, fresh = true, purpose = Purpose.SHORTS) }
                    .getOrThrow()
            }
        }.onSuccess { stream = it }.onFailure { error = it.message ?: "Playback failed" }
    }
    // progress line: poll the player while this page is the active one
    LaunchedEffect(exo, active) {
        val p = exo ?: return@LaunchedEffect
        while (active) { pos = p.currentPosition; dur = p.duration.coerceAtLeast(0L); delay(100) }
    }
    LaunchedEffect(active) { if (!active) { paused = false; fast = false } }

    fun like() = scope.launch {
        val st = stream ?: return@launch
        val (on, r) = repo.toggleLike(VideoItem(st.videoId, st.title, st.channelName, st.channelId, st.thumbnailUrl))
        toast(r.describe(if (on) "Liked" else "Like removed"))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            stream != null && active -> VideoPlayer(
                stream = stream!!, modifier = Modifier.fillMaxSize(),
                playWhenReady = true, resizeModeFill = true,
                speed = if (fast) 2f else userSpeed, paused = paused, muted = muted, loop = !autoScroll,
                onPlayer = { exo = it }, onEnded = onNext, dataSourceFactory = shortsDataSource,
                onError = { if (reloads < 1) { forceFresh = true; stream = null; reloads += 1 } else error = it },
            )
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "Error", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tap to retry", color = YouPlayRed, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { error = null; stream = null; forceFresh = true; reloads += 1 },
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = thumb ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg", contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
                Text("Loading…", color = Color.White.copy(0.85f))
            }
        }

        // Gesture layer (below every button): tap pause/resume, double-tap like, hold 2x. Swipes pass through.
        if (error == null) Box(
            Modifier.fillMaxSize().pointerInput(videoId) {
                detectTapGestures(
                    onTap = { paused = !paused; flash++ },
                    onDoubleTap = { at -> burst = at to ((burst?.second ?: 0) + 1); if (!liked) like() },
                    onLongPress = { fast = true },
                    onPress = { tryAwaitRelease(); fast = false },
                )
            },
        )

        PauseFlash(flash, paused)
        burst?.let { (at, key) -> HeartBurst(at, key) }
        if (fast) Text(
            "2x  ▶▶", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = OverlayShadow),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
        )

        // The "Shorts" title and the Subscribed pill stay off the picture while a Short plays;
        // they fade in on a pause and out again on resume or scroll (as Flow does).
        val idle = paused && !scrolling

        // top bar: 16dp sides, fixed 40dp height, no backgrounds
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) YpIconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                else this@Row.AnimatedVisibility(visible = idle, enter = fadeIn(tween(220)), exit = fadeOut(tween(90))) {
                    Text("Shorts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = OverlayShadow))
                }
            }
            YpIconTap(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (muted) "Unmute" else "Mute", { onMuted(!muted) })
            YpIconTap(Icons.Filled.MoreVert, "More", { menu = true })
        }

        // seek line first, so the caption and rail sit on top of its touch area
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SeekLine(pos, dur) { f -> exo?.let { p -> if (dur > 0) { p.seekTo((dur * f).toLong()); pos = (dur * f).toLong() } } }
        }

        // Flow's reel chrome: caption down the left edge, action rail down the right edge, nothing behind either
        val toggleSub = {
            val st = stream; val cid = st?.channelId ?: channelId
            if (cid != null) scope.launch {
                val (on, r) = repo.toggleSubscribe(app.youplay.domain.ChannelItem(cid, (st?.channelName ?: name).ifBlank { name }))
                toast(r.describe(if (on) "Subscribed" else "Unsubscribed"))
            }
            Unit
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f).padding(end = 20.dp)) {
                if (name.isNotBlank()) Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    YpAvatar(extras?.channelAvatarUrl, name, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = OverlayShadow), modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!subscribed) { Spacer(Modifier.width(5.dp)); SubPill(false, toggleSub) }
                    else AnimatedVisibility(
                        visible = idle, enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.9f), exit = fadeOut(tween(90)),
                    ) { Row { Spacer(Modifier.width(5.dp)); SubPill(true, toggleSub) } }
                }
                Text(
                    title.replace(Regex("#\\S+"), "").replace(Regex("\\s+"), " ").trim().ifBlank { title },
                    color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = OverlayShadow), modifier = Modifier.clickable { showDesc = true },
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RailAction(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, extras?.likeCountText ?: "Like", if (liked) YouPlayRed else Color.White) { like() }
                RailAction(Icons.Outlined.ChatBubbleOutline, extras?.commentCountText ?: "Comments") { showComments = true }
                RailAction(Icons.Outlined.WatchLater, "Save") {
                    scope.launch { toast(repo.saveToWatchLater(videoId).describe("Saved to Watch later")) }
                }
                Spacer(Modifier.height(8.dp))
                RailAvatar(extras?.channelAvatarUrl, name)
            }
        }

        if (menu) ShortsMenu(
            onDismiss = { menu = false }, autoScroll = autoScroll, onAutoScroll = onAutoScroll,
            speed = userSpeed, onSpeed = onSpeed,
            disliked = disliked,
            onDislike = {
                menu = false
                scope.launch {
                    val (on, r) = repo.toggleDislike(videoId)
                    toast(r.describe(if (on) "Disliked" else "Dislike removed"))
                }
            },
            onDescription = { menu = false; showDesc = true },
            onShare = { menu = false; shareShort(context, videoId) },
            onCopy = {
                menu = false
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Short", "https://youtube.com/shorts/$videoId"))
                toast("Link copied")
            },
            onWatchLater = { menu = false; scope.launch { toast(repo.saveToWatchLater(videoId).describe("Saved to Watch later")) } },
        )
        if (showComments) app.youplay.ui.screens.comments.CommentsSheet(videoId, extras?.commentCountText) { showComments = false }
        if (showDesc) app.youplay.ui.components.DescriptionSheet(
            title = stream?.title?.takeIf { it.isNotBlank() } ?: title, channelName = name,
            channelAvatarUrl = extras?.channelAvatarUrl, subscriberText = extras?.subscriberText,
            views = extras?.views, published = extras?.published,
            description = extras?.description?.takeIf { it.isNotBlank() } ?: stream?.description,
            onDismiss = { showDesc = false },
        )
    }
}

private fun shareShort(context: android.content.Context, id: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, "https://youtube.com/shorts/$id")
    context.startActivity(android.content.Intent.createChooser(send, "Share"))
}

private val OverlayShadow = Shadow(Color.Black.copy(0.6f), blurRadius = 4f)

private fun compactShortCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.1fK".format(n / 1e3)
    else -> n.coerceAtLeast(0).toString()
}

/** Flow's rail item: 40dp touch, 24dp icon, a 16dp label row so every item keeps one even pitch. */
@Composable
private fun RailAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.bounceClick(pressed = 0.88f, onClick = onClick).size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TextStyle(shadow = OverlayShadow))
        }
    }
}

/** 36dp rounded square with a thin light outline, at the foot of the rail. */
@Composable
private fun RailAvatar(url: String?, name: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.size(36.dp).border(1.5.dp, Color.White, shape).padding(1.5.dp).clip(shape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Outline-only pill (no fill). Once subscribed it dims. */
@Composable
private fun SubPill(subscribed: Boolean, onClick: () -> Unit) {
    Text(
        if (subscribed) "Subscribed" else "Subscribe", color = Color.White.copy(if (subscribed) 0.8f else 1f),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = OverlayShadow),
        modifier = Modifier.bounceClick(onClick = onClick).border(1.5.dp, Color.White.copy(if (subscribed) 0.55f else 1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Thin progress line that thickens while scrubbing; tap or drag to seek. */
@Composable
private fun SeekLine(pos: Long, dur: Long, onSeek: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(30.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / w).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / w).coerceIn(0f, 1f) }
            }
            .pointerInput(dur) { detectTapGestures { o -> onSeek((o.x / w).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.BottomStart,
    ) {
        if (drag != null && dur > 0) Text(
            "${fmt((dur * frac).toLong())} / ${fmt(dur)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = OverlayShadow), modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        val h = if (drag != null) 6.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(h).background(Color.White.copy(0.25f)))
        Box(Modifier.fillMaxWidth(frac).height(h).background(YouPlayRed))
    }
}

private fun fmt(ms: Long): String { val t = ms / 1000; return "%d:%02d".format(t / 60, t % 60) }

/** Big pause / play glyph that pops and fades on every tap. */
@Composable
private fun PauseFlash(key: Int, paused: Boolean) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) { if (key > 0) { a.snapTo(1f); a.animateTo(0f, tween(550)) } }
    if (a.value > 0.01f) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(72.dp).scale(0.8f + 0.4f * (1f - a.value)).graphicsLayer { alpha = a.value }.clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
    }
}

/** Heart that pops where you double-tapped, floats up and fades. */
@Composable
private fun HeartBurst(at: Offset, key: Int) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(700)) }
    if (t.value < 1f) Icon(
        Icons.Filled.Favorite, null, tint = YouPlayRed,
        modifier = Modifier
            .offset { IntOffset((at.x - 48.dp.toPx()).roundToInt(), (at.y - 48.dp.toPx() - 60.dp.toPx() * t.value).roundToInt()) }
            .size(96.dp)
            .graphicsLayer { val s = if (t.value < 0.25f) t.value * 4f else 1f; scaleX = s; scaleY = s; alpha = 1f - ((t.value - 0.5f).coerceAtLeast(0f) * 2f) },
    )
}

@Composable
private fun ShortsMenu(
    onDismiss: () -> Unit, autoScroll: Boolean, onAutoScroll: (Boolean) -> Unit, speed: Float, onSpeed: (Float) -> Unit,
    disliked: Boolean, onDislike: () -> Unit, onDescription: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit, onWatchLater: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF212121))
                .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                    val on = s == speed
                    Text(
                        if (s == 1f) "1x" else "${s}x", color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color(0xFF3A3A3A))
                            .clickable { onSpeed(s) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { onAutoScroll(!autoScroll) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Auto-scroll when a Short ends", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    if (autoScroll) "On" else "Off", color = if (autoScroll) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (autoScroll) YouPlayRed else Color(0xFF3A3A3A)).padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
            MenuRow(Icons.Outlined.Notes, "Description", onDescription)
            MenuRow(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, if (disliked) "Remove dislike" else "Dislike", onDislike)
            MenuRow(Icons.Outlined.Share, "Share", onShare)
            MenuRow(Icons.Outlined.Link, "Copy link", onCopy)
            MenuRow(Icons.Outlined.WatchLater, "Save to Watch later", onWatchLater)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(rememberVectorPainter(icon), label, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

private fun repoOf(context: android.content.Context): VideoRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, ShortsRepoEntryPoint::class.java).videoRepository()

private fun playerControllerOf(context: android.content.Context): app.youplay.ui.player.PlayerController =
    EntryPointAccessors.fromApplication(context.applicationContext, ShortsRepoEntryPoint::class.java).playerController()
