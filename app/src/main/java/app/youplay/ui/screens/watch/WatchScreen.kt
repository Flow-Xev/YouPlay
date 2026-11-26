package app.youplay.ui.screens.watch

import kotlinx.coroutines.launch
import app.youplay.domain.UiState
import androidx.compose.foundation.gestures.draggable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.ui.components.RelatedVideoRow
import app.youplay.ui.components.StateScaffold
import app.youplay.ui.components.YpActionPill
import app.youplay.ui.components.YpAvatar
import app.youplay.ui.components.YpSubscribe
import app.youplay.ui.player.PlayerMenuAction
import app.youplay.ui.player.WatchPlayer
import app.youplay.ui.theme.YouPlayRed
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun WatchScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenShort: (String) -> Unit = {},
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val controller = viewModel.controller
    val dl by viewModel.download.collectAsStateWithLifecycle()
    val disliked by viewModel.disliked.collectAsStateWithLifecycle()
    val votes by viewModel.votes.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.controller.watchVisible = true
        onDispose { viewModel.controller.watchVisible = false }
    }
    val current by controller.current.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var toast by remember { mutableStateOf<String?>(null) }
    var showComments by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { toast = it; viewModel.messageShown() }
    }

    DisposableEffect(landscape) {
        val window = activity?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else ctl?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val toggleFullscreen = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    BackHandler(enabled = landscape) { toggleFullscreen() }

    val title = current?.title.orEmpty()
    val px by viewModel.playerExtras.collectAsStateWithLifecycle()
    val moreShorts by viewModel.moreShorts.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val nextId = (state as? app.youplay.domain.UiState.Success)?.data?.extras?.related?.firstOrNull { !it.isShort }?.id
    DisposableEffect(autoplay, nextId) {
        controller.onEnded = { if (autoplay && nextId != null && !controller.loop.value) onOpenVideo(nextId) }
        onDispose { controller.onEnded = null }
    }
    val descText = (state as? app.youplay.domain.UiState.Success)?.data?.extras?.description
    val chapters = remember(px, descText) { px.chapters.takeIf { it.size >= 2 } ?: app.youplay.util.parseChapters(descText) }
    val shareAction = PlayerMenuAction("Share", Icons.Filled.Share) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${viewModel.videoId}")
        context.startActivity(Intent.createChooser(send, "Share"))
    }
    val dlLabel = when {
        dl == null || dl?.status == app.youplay.data.local.DOWNLOAD_FAILED -> "Download"
        dl?.status == app.youplay.data.local.DOWNLOAD_DONE -> "Downloaded"
        else -> "Downloading ${dl?.progress ?: 0}%"
    }
    val downloadAction = PlayerMenuAction(
        dlLabel, if (dl?.status == app.youplay.data.local.DOWNLOAD_DONE) Icons.Filled.DownloadDone else Icons.Filled.Download,
    ) { (state as? app.youplay.domain.UiState.Success)?.data?.stream?.let(viewModel::toggleDownload) }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            WatchPlayer(
                controller = controller, fullscreen = true, title = title,
                onToggleFullscreen = toggleFullscreen, onMinimize = onBack,
                onQuality = viewModel::setQuality, extraActions = listOf(downloadAction, shareAction),
                chapters = chapters, storyboard = px.storyboard, onNext = nextId?.let { id -> { onOpenVideo(id) } },
            )
        }
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val prog = remember { androidx.compose.animation.core.Animatable(if (controller.watchVisible) 0f else 1f) }
    val dragScope = androidx.compose.runtime.rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { prog.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 330f)) }
    val minimize: () -> Unit = {
        if (!leaving) { leaving = true; dragScope.launch { prog.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 420f)); onBack() } }
    }
    BackHandler(enabled = !landscape) { minimize() }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val miniW = with(density) { 220.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val barPx = with(density) { 24.dp.toPx() } + androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
        val topPx = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density).toFloat()
        val miniScale = miniW / sw
        val miniH = miniW * 9f / 16f
        val tx = if (controller.miniRight) sw - miniW - gap else gap
        val ty = (if (controller.miniBottom) sh - barPx - miniH - gap else gap) - topPx
        val dragState = androidx.compose.foundation.gestures.rememberDraggableState { d ->
            dragScope.launch { prog.snapTo((prog.value + d / (sh * 0.55f)).coerceIn(0f, 1f)) }
        }
        Box(Modifier.matchParentSize().graphicsLayer { alpha = (1f - prog.value * 1.5f).coerceIn(0f, 1f) }.background(Color.Black))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                Modifier
                    .graphicsLayer {
                        val q = prog.value
                        val sc = 1f + q * (miniScale - 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        scaleX = sc; scaleY = sc
                        translationX = q * tx; translationY = q * ty
                        shape = RoundedCornerShape((12f * q).dp); clip = true
                        shadowElevation = 28f * q
                    }
                    .draggable(
                        state = dragState,
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        onDragStopped = { v ->
                            if (prog.value > 0.3f || v > 1100f) minimize()
                            else dragScope.launch { prog.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
                        },
                    ),
            ) {
                WatchPlayer(
                    controller = controller, fullscreen = false, title = title,
                    onToggleFullscreen = toggleFullscreen, onMinimize = minimize,
                    onQuality = viewModel::setQuality, extraActions = listOf(downloadAction, shareAction),
                    chapters = chapters, storyboard = px.storyboard, onNext = nextId?.let { id -> { onOpenVideo(id) } },
                )
            }
            StateScaffold(state, viewModel::load, Modifier.fillMaxSize().graphicsLayer { val q = prog.value; alpha = (1f - q * 1.8f).coerceIn(0f, 1f); translationY = q * sh * 0.1f }) { page ->
                val s = page.stream
                val extras = page.extras
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(s.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                            val meta = listOfNotNull(extras.views, extras.published).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(meta, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).clickable(enabled = s.channelId != null) {
                                        s.channelId?.let(onOpenChannel)
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    YpAvatar(extras.channelAvatarUrl, s.channelName.ifBlank { "?" }, 40.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            s.channelName.ifBlank { "Channel" }, color = Color.White,
                                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        extras.subscriberText?.let {
                                            Text(it, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                                if (s.channelId != null) {
                                    YpSubscribe(subscribed, onClick = { viewModel.toggleSubscribe(s) }, compact = true)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                YpActionPill(
                                    if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    (state as? UiState.Success)?.data?.extras?.likeCountText ?: "Like",
                                    { viewModel.toggleLike(s) }, active = liked,
                                )
                                YpActionPill(
                                    if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                    votes?.let { compactCount(it.dislikes + if (disliked) 1 else 0) } ?: "Dislike",
                                    viewModel::toggleDislike, active = disliked,
                                )
                                YpActionPill(
                                    if (dl?.status == app.youplay.data.local.DOWNLOAD_DONE) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                    dlLabel, { viewModel.toggleDownload(s) },
                                )
                                YpActionPill(Icons.Filled.Share, "Share", shareAction.onClick)
                                Box {
                                    YpActionPill(Icons.Filled.PlaylistAdd, "Save", {
                                        viewModel.loadPlaylists(); saveMenu = true
                                    })
                                    if (saveMenu) {
                                        Column(
                                            Modifier
                                                .padding(top = 42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF212121))
                                                .width(220.dp),
                                        ) {
                                            playlists.forEach { p ->
                                                Text(
                                                    p.title, color = Color.White, fontSize = 14.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { saveMenu = false; viewModel.saveTo(p) }
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (playlists.isEmpty()) {
                                                Text("No playlists", color = Color(0xFFAAAAAA), modifier = Modifier.padding(14.dp))
                                            }
                                        }
                                    }
                                }
                                YpActionPill(Icons.Filled.Forum, (state as? UiState.Success)?.data?.extras?.commentCountText ?: "Comments", { showComments = true })
                            }
                            extras.description?.takeIf { it.isNotBlank() }?.let { d ->
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A1A1A))
                                        .clickable { showDesc = true }
                                        .padding(12.dp),
                                ) {
                                    Column {
                                        val head = listOfNotNull(extras.views, extras.published).joinToString("  ")
                                        if (head.isNotBlank()) Text(head, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            d, color = Color(0xFFDDDDDD), fontSize = 13.sp, lineHeight = 18.sp,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        Text("…more", color = Color(0xFFAAAAAA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (chapters.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Chapters", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    chapters.forEach { c ->
                                        Column(
                                            Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F1F1F))
                                                .clickable { controller.seekTo(c.startMs) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            Text(chapterTime(c.startMs), color = YouPlayRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(c.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .clickable { showComments = true }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Comments", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.weight(1f))
                                Text("View all", color = YouPlayRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Up next", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val relatedLong = extras.related.filter { !it.isShort }
                    val relatedShorts = extras.related.filter { it.isShort }.ifEmpty { moreShorts }
                    items(relatedLong.take(3), key = { it.id }) { v ->
                        RelatedVideoRow(
                            id = v.id, title = v.title, channelName = v.channelName,
                            views = v.viewCountText, published = v.publishedText,
                            thumbnailUrl = v.thumbnailUrl, durationText = v.durationText,
                            onClick = { onOpenVideo(v.id) },
                        )
                    }
                    if (relatedShorts.isNotEmpty()) item(key = "related_shorts") {
                        app.youplay.ui.components.ShortsRowSection(relatedShorts, {
                            viewModel.setShortsQueue(relatedShorts); onOpenShort(it.id)
                        })
                    }
                    items(relatedLong.drop(3), key = { it.id }) { v ->
                        RelatedVideoRow(
                            id = v.id, title = v.title, channelName = v.channelName,
                            views = v.viewCountText, published = v.publishedText,
                            thumbnailUrl = v.thumbnailUrl, durationText = v.durationText,
                            onClick = { onOpenVideo(v.id) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
        if (showComments) app.youplay.ui.screens.comments.CommentsSheet(viewModel.videoId, (state as? UiState.Success)?.data?.extras?.commentCountText) { showComments = false }
        if (showDesc) (state as? app.youplay.domain.UiState.Success)?.data?.let { pg ->
            app.youplay.ui.components.DescriptionSheet(
                title = pg.stream.title, channelName = pg.stream.channelName,
                channelAvatarUrl = pg.extras.channelAvatarUrl, subscriberText = pg.extras.subscriberText,
                views = pg.extras.views, published = pg.extras.published, description = pg.extras.description,
                chapters = chapters, onSeek = { controller.seekTo(it) }, onDismiss = { showDesc = false },
            )
        }
        toast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xEE323232))
                    .clickable { toast = null }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(msg, color = Color.White, fontSize = 13.sp)
            }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2200)
                if (toast == msg) toast = null
            }
        }
    }
}

private fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1e3)
    else -> n.toString()
}

private fun chapterTime(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}
