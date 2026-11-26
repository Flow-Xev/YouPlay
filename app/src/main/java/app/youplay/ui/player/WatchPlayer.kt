package app.youplay.ui.player

import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.youplay.ui.theme.YouPlayRed
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class PlayerMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private enum class MenuPage { Main, Quality, Speed, Sleep }
private val SleepOptions = listOf(null to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour")

val QualityOptions = listOf(
    0 to "Auto", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p",
    480 to "480p", 360 to "360p", 240 to "240p", 144 to "144p",
)
val SpeedOptions = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatMbps(bps: Long): String =
    if (bps <= 0) "measuring…" else if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} kbps"

@Composable
fun rememberPlayerProgress(c: PlayerController): State<Pair<Long, Long>> =
    produceState(0L to 0L, c) {
        while (true) {
            value = c.player.currentPosition to c.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

@Composable
private fun rememberBuffered(c: PlayerController): State<Long> =
    produceState(0L, c) {
        while (true) { value = c.player.bufferedPosition.coerceAtLeast(0L); delay(750) }
    }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * Player surface with a custom overlay.
 *  - tap = controls; double-tap = -/+10s (keep tapping to skip further); press & hold = 2x while held
 *  - fullscreen: swipe up/down on the left half = brightness, right half = volume
 *  - slim seek bar with buffered range, quality chip, fit/fill toggle, three-dots menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchPlayer(
    controller: PlayerController,
    fullscreen: Boolean,
    title: String,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onQuality: (Int) -> Unit,
    modifier: Modifier = Modifier,
    extraActions: List<PlayerMenuAction> = emptyList(),
    chapters: List<app.youplay.domain.Chapter> = emptyList(),
    storyboard: app.youplay.domain.Storyboard? = null,
    onNext: (() -> Unit)? = null,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val buffering by controller.buffering.collectAsState()
    val error by controller.error.collectAsState()
    val speed by controller.speed.collectAsState()
    val quality by controller.quality.collectAsState()
    val loop by controller.loop.collectAsState()
    val bandwidth by controller.bandwidth.collectAsState()
    val progress by rememberPlayerProgress(controller)
    val buffered by rememberBuffered(controller)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var boosting by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuPage?>(null) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var zoom by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Triple<ImageVector, String, Float>?>(null) }
    var brightness by remember { mutableStateOf(-1f) }
    var volume by remember { mutableStateOf(-1f) }

    LaunchedEffect(controls, isPlaying, menu, tick, scrub) {
        if (controls && isPlaying && menu == null && scrub == null) { delay(3000); controls = false }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(650); flash = null } }
    LaunchedEffect(hud) { if (hud != null) { delay(800); hud = null } }
    var skipMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) { controller.skipped.collect { skipMsg = it; delay(2200); skipMsg = null } }
    val sleepAt by controller.sleepAt.collectAsState()
    val playingH by controller.playingHeight.collectAsState()
    var brightnessTouched by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        onDispose {
            if (brightnessTouched) activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = -1f } }
        }
    }

    fun adjust(left: Boolean, delta: Float) {
        if (left) {
            val w = activity?.window ?: return
            if (brightness < 0f) brightness = w.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
            brightness = (brightness + delta).coerceIn(0.02f, 1f)
            w.attributes = w.attributes.also { it.screenBrightness = brightness }
            brightnessTouched = true
            hud = Triple(Icons.Filled.BrightnessMedium, "Brightness", brightness)
        } else {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (volume < 0f) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            volume = (volume + delta).coerceIn(0f, 1f)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
            hud = Triple(Icons.Filled.VolumeUp, "Volume", volume)
        }
    }

    val shape = Modifier
        .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        .background(Color.Black)
    val (pos, dur) = progress
    val ended = dur > 0 && pos >= dur - 400 && !isPlaying
    val qualityLabel = if (quality == 0) (if (playingH > 0) "Auto · ${playingH}p" else "Auto")
    else QualityOptions.firstOrNull { it.first == quality }?.second ?: "${quality}p"

    Box(modifier.then(shape).onSizeChanged { controller.viewHeightPx = it.height }.pointerInput(fullscreen) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            var z = 1f; var multi = false
            do {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (ev.changes.size >= 2) { multi = true; z *= ev.calculateZoom(); ev.changes.forEach { it.consume() } }
            } while (ev.changes.any { it.pressed })
            if (multi) {
                if (z > 1.12f) { if (!fullscreen) onToggleFullscreen() else if (!zoom) { zoom = true; flash = "Fill" } }
                else if (z < 0.88f) { if (fullscreen && zoom) { zoom = false; flash = "Fit" } else if (fullscreen) onToggleFullscreen() }
            }
        }
    }, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true) // keep the last frame during quality switches
                    player = controller.player
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = {
                it.player = controller.player
                it.resizeMode = if (zoom && fullscreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- gesture layer ----
        Box(
            Modifier.fillMaxSize().pointerInput(fullscreen) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    // 1 = released (tap), 2 = vertical drag started, null = long press
                    val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var r = 0
                        while (r == 0) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull 1
                            if (ch.changedToUp()) r = 1
                            else if (fullscreen && abs(ch.position.y - down.position.y) > slop) r = 2
                        }
                        r
                    }
                    if (first == null) {
                        boosting = true; controller.boost(true)
                        waitForUpOrCancellation()
                        boosting = false; controller.boost(false)
                        return@awaitEachGesture
                    }
                    if (first == 2) {
                        val left = down.position.x < size.width / 2f
                        var lastY = down.position.y
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUp() || !ch.pressed) break
                            val dy = ch.position.y - lastY
                            lastY = ch.position.y
                            ch.consume()
                            adjust(left, -dy / size.height * 1.4f)
                        }
                        return@awaitEachGesture
                    }
                    // tap: wait for more taps; every extra tap skips another 10s on its side
                    var skipped = 0
                    while (true) {
                        val next = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        } ?: break
                        waitForUpOrCancellation()
                        skipped++
                        if (next.position.x < size.width / 2f) { controller.seekBy(-10_000); flash = "« ${skipped * 10}s" }
                        else { controller.seekBy(10_000); flash = "${skipped * 10}s »" }
                    }
                    if (skipped == 0) { controls = !controls; tick++ }
                }
            },
        )

        skipMsg?.let {
            Text(
                it, color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                    .background(YouPlayRed.copy(0.9f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (buffering && error == null) CircularProgressIndicator(color = YouPlayRed)

        flash?.let {
            val pop = remember { androidx.compose.animation.core.Animatable(0.6f) }
            LaunchedEffect(it) { pop.snapTo(0.7f); pop.animateTo(1f, androidx.compose.animation.core.spring(0.35f, 500f)) }
            Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(if (it.startsWith("«")) Alignment.CenterStart else if (it.endsWith("»")) Alignment.CenterEnd else Alignment.Center).padding(horizontal = 40.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (boosting) {
            Text("2x  ▶▶", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color.Black.copy(0.65f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        hud?.let { (icon, label, f) ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(90.dp).height(4.dp).background(Color.White.copy(0.3f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(f).height(4.dp).background(YouPlayRed, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.width(8.dp))
                Text("${(f * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        error?.let {
            Column(Modifier.background(Color.Black.copy(0.7f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = controller::retry) { Icon(Icons.Filled.Replay, null); Text("  Retry") }
            }
        }

        AnimatedVisibility(
            !controls && error == null && dur > 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val f = (pos.toFloat() / dur).coerceIn(0f, 1f)
            val bf = (buffered.toFloat() / dur).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(Color.White.copy(0.18f), Offset(0f, 1f), Offset(size.width, 1f), 2.dp.toPx(), StrokeCap.Butt)
                drawLine(Color.White.copy(0.4f), Offset(0f, 1f), Offset(size.width * bf, 1f), 2.dp.toPx(), StrokeCap.Butt)
                drawLine(YouPlayRed, Offset(0f, 1f), Offset(size.width * f, 1f), 2.dp.toPx(), StrokeCap.Butt)
            }
        }
        // ---- controls overlay ----
        AnimatedVisibility(controls && error == null, enter = fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.scaleIn(initialScale = 0.985f, animationSpec = androidx.compose.animation.core.tween(180)), exit = fadeOut(androidx.compose.animation.core.tween(220)), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.55f), 0.3f to Color.Black.copy(0.08f),
                        0.65f to Color.Black.copy(0.08f), 1f to Color.Black.copy(0.7f),
                    ),
                ),
            ) {
                // top bar
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMinimize) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = Color.White) }
                    Text(if (fullscreen) title else "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!fullscreen) Spacer(Modifier.weight(1f))
                    Text(
                        qualityLabel, color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip50().clickable { menu = MenuPage.Quality }
                            .background(Color.White.copy(0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    if (fullscreen) IconButton(onClick = { zoom = !zoom }) {
                        Icon(Icons.Filled.AspectRatio, "Fit / fill", tint = if (zoom) YouPlayRed else Color.White)
                    }
                    IconButton(onClick = { menu = MenuPage.Main }) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
                }
                // centre transport
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    IconButton(onClick = { controller.seekBy(-10_000); tick++ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = { controller.toggle(); tick++ },
                        modifier = Modifier.size(68.dp).background(Color.Black.copy(0.5f), CircleShape),
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = if (ended) 2 else if (isPlaying) 1 else 0,
                            animationSpec = androidx.compose.animation.core.tween(160), label = "pp",
                        ) { st ->
                            Icon(
                                when (st) { 2 -> Icons.Filled.Replay; 1 -> Icons.Filled.Pause; else -> Icons.Filled.PlayArrow },
                                "Play/Pause", tint = Color.White, modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                    IconButton(onClick = { controller.seekBy(10_000); tick++ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    if (onNext != null) IconButton(onClick = { onNext() }, modifier = Modifier.size(44.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Filled.SkipNext, "Next video", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                // bottom: time row + seek bar
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (scrub != null) (scrub!! * dur).toLong() else pos
                        Text(formatTime(shown), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("  /  ${formatTime(dur)}", color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium)
                        val chap = chapters.lastOrNull { it.startMs <= shown }
                        Text(
                            chap?.let { "  •  ${it.title}" } ?: "", color = Color.White, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (speed != 1f) Text(
                            "${speed}x", color = Color.White, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip50().clickable { menu = MenuPage.Speed }
                                .background(Color.White.copy(0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                            Icon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Fullscreen", tint = Color.White)
                        }
                    }
                    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                    val buf = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
                    scrub?.let { sf ->
                        Box(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                            storyboard?.let {
                                ScrubPreview(it, (sf * dur).toLong(), Modifier.align(androidx.compose.ui.BiasAlignment(sf * 2f - 1f, -1f)).padding(bottom = 26.dp))
                            }
                            Text(
                                formatTime((sf * dur).toLong()), color = Color.White, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.align(androidx.compose.ui.BiasAlignment(sf * 2f - 1f, 1f))
                                    .background(Color.Black.copy(0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    SeekBar(
                        progress = scrub ?: frac, buffered = buf, scrubbing = scrub != null,
                        onScrub = { scrub = it },
                        onDone = { scrub?.let { controller.seekTo((it * progress.second).toLong()) }; scrub = null },
                        marks = if (dur > 0) chapters.drop(1).map { it.startMs.toFloat() / dur } else emptyList(),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }

    // ---- three-dots menu ----
    menu?.let { page ->
        ModalBottomSheet(
            onDismissRequest = { menu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                when (page) {
                    MenuPage.Main -> {
                        MenuRow(Icons.Filled.HighQuality, "Quality", qualityLabel) { menu = MenuPage.Quality }
                        MenuRow(Icons.Filled.Speed, "Playback speed", if (speed == 1f) "Normal" else "${speed}x") { menu = MenuPage.Speed }
                        MenuRow(Icons.Filled.Bedtime, "Sleep timer", sleepAt?.let { "Ends ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it)}" } ?: "Off") { menu = MenuPage.Sleep }
                        ListItem(
                            headlineContent = { Text("Loop video") },
                            leadingContent = { Icon(Icons.Filled.RepeatOne, null) },
                            trailingContent = { Switch(checked = loop, onCheckedChange = controller::setLoop) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        extraActions.forEach { a -> MenuRow(a.icon, a.label, null) { menu = null; a.onClick() } }
                    }
                    MenuPage.Quality -> {
                        QualityOptions.forEach { (h, label) ->
                            MenuRow(
                                null,
                                if (h == 0) "Auto" else label,
                                if (h == 0) "Adapts to your network · ${formatMbps(bandwidth)}" else null,
                                checked = quality == h,
                            ) { menu = null; onQuality(h) }
                        }
                    }
                    MenuPage.Sleep -> SleepOptions.forEach { (m, label) ->
                        MenuRow(null, label, null, checked = (m == null && sleepAt == null)) { menu = null; controller.setSleepTimer(m) }
                    }
                    MenuPage.Speed -> SpeedOptions.forEach { v ->
                        MenuRow(null, if (v == 1f) "Normal" else "${v}x", null, checked = speed == v) { menu = null; controller.setSpeed(v) }
                    }
                }
            }
        }
    }
}

private fun Modifier.clip50(): Modifier = this.then(Modifier.clip(RoundedCornerShape(50)))

/** Slim seek bar: played (accent), buffered (light), thumb that grows while dragging; tap or drag to seek. */
@Composable
private fun SeekBar(
    progress: Float,
    buffered: Float,
    scrubbing: Boolean,
    onScrub: (Float) -> Unit,
    onDone: () -> Unit,
    marks: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var w by remember { mutableStateOf(1f) }
    val scrubCb by rememberUpdatedState(onScrub)
    val doneCb by rememberUpdatedState(onDone)
    val accent = YouPlayRed
    Box(
        modifier.fillMaxWidth().height(30.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o -> scrubCb((o.x / w).coerceIn(0f, 1f)); doneCb() })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> scrubCb((o.x / w).coerceIn(0f, 1f)) },
                    onDragEnd = { doneCb() },
                    onDragCancel = { doneCb() },
                ) { ch, _ -> ch.consume(); scrubCb((ch.position.x / w).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val th = (if (scrubbing) 5.dp else 3.dp).toPx()
            fun bar(frac: Float, color: Color) = drawLine(
                color, Offset(th / 2, cy), Offset(th / 2 + (size.width - th) * frac, cy), th, StrokeCap.Round,
            )
            bar(1f, Color.White.copy(0.28f))
            if (buffered > 0f) bar(buffered, Color.White.copy(0.45f))
            if (progress > 0f) bar(progress, accent)
            marks.forEach { m -> val x = th / 2 + (size.width - th) * m; drawLine(Color.Black, Offset(x, cy - th / 2 - 1f), Offset(x, cy + th / 2 + 1f), 1.5.dp.toPx()) }
            val r = (if (scrubbing) 9.dp else 6.dp).toPx()
            if (scrubbing) drawCircle(accent.copy(0.28f), r * 1.9f, Offset((r + (size.width - 2 * r) * progress).coerceIn(r, size.width - r), cy))
            drawCircle(accent, r, Offset((r + (size.width - 2 * r) * progress).coerceIn(r, size.width - r), cy))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector?, label: String, value: String?, checked: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = if (icon != null) ({ Icon(icon, null) }) else null,
        trailingContent = when {
            checked -> ({ Icon(Icons.Filled.Check, null, tint = YouPlayRed) })
            value != null && icon != null -> ({ Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) })
            else -> null
        },
        supportingContent = if (value != null && icon == null) ({ Text(value) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Sprite-sheet frame shown above the seek bar while scrubbing. */
@Composable
private fun ScrubPreview(sb: app.youplay.domain.Storyboard, ms: Long, modifier: Modifier = Modifier) {
    val per = (sb.perX * sb.perY).coerceAtLeast(1)
    val idx = (ms / sb.durPerFrameMs).toInt().coerceIn(0, (sb.count - 1).coerceAtLeast(0))
    val page = (idx / per).coerceIn(0, (sb.urls.size - 1).coerceAtLeast(0))
    val within = idx % per
    val col = within % sb.perX
    val row = within / sb.perX
    val sc = 128f / sb.frameW
    Box(modifier.size(128.dp, (sb.frameH * sc).dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
        AsyncImage(
            model = sb.urls[page], contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size((sb.perX * sb.frameW * sc).dp, (sb.perY * sb.frameH * sc).dp)
                .offset((-col * sb.frameW * sc).dp, (-row * sb.frameH * sc).dp),
        )
    }
}
