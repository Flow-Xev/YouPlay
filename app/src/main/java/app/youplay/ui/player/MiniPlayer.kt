package app.youplay.ui.player

import androidx.compose.ui.graphics.graphicsLayer
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.youplay.ui.components.YpIconTap
import app.youplay.ui.theme.YouPlayRed
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Floating mini player in the style of Flow's / YouTube's: a small video window with its
 * controls drawn on top (play/pause top-left, close top-right, -10s / +10s along the bottom,
 * thin progress line). Drag it anywhere; on release it springs to the nearest corner.
 * Drag it off the left/right edge to dismiss. Tap = expand to the full player.
 * Place it inside a Box that covers the screen content area (above the bottom bar).
 */
@Composable
fun FloatingMiniPlayer(
    controller: PlayerController,
    onExpand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream by controller.current.collectAsState()
    val s = stream ?: return
    val playing by controller.isPlaying.collectAsState()
    val buffering by controller.buffering.collectAsState()
    val (pos, dur) = rememberPlayerProgress(controller).value
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val miniW = 220.dp
        val miniH = miniW * 9 / 16
        val margin = 12.dp
        val wPx = with(density) { miniW.toPx() }
        val hPx = with(density) { miniH.toPx() }
        val mPx = with(density) { margin.toPx() }
        val minX = mPx
        val maxX = with(density) { maxWidth.toPx() } - wPx - mPx
        val minY = mPx
        val maxY = with(density) { maxHeight.toPx() } - hPx - mPx

        val offset = remember { Animatable(Offset(if (controller.miniRight) maxX else minX, if (controller.miniBottom) maxY else minY), Offset.VectorConverter) }
        LaunchedEffect(maxX, maxY) {
            offset.snapTo(Offset(offset.value.x.coerceIn(minX, maxX), offset.value.y.coerceIn(minY, maxY)))
        }

        fun snapTarget(p: Offset): Offset {
            val right = p.x + wPx / 2 > (minX + maxX + wPx) / 2
            val bottom = p.y + hPx / 2 > (minY + maxY + hPx) / 2
            controller.miniRight = right; controller.miniBottom = bottom
            return Offset(if (right) maxX else minX, if (bottom) maxY else minY)
        }

        val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
        var acc by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        var lifted by remember { androidx.compose.runtime.mutableStateOf(false) }
        val lift by androidx.compose.animation.core.animateFloatAsState(
            if (lifted) 1.06f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
        Box(
            Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(miniW, miniH)
                .graphicsLayer {
                    scaleX = lift; scaleY = lift
                    // fade out as it is dragged off the side, hinting that letting go dismisses it
                    val p = offset.value.x
                    val off = if (p < minX) minX - p else if (p > maxX) p - maxX else 0f
                    alpha = (1f - off / (wPx * 0.9f)).coerceIn(0.25f, 1f)
                }
                .shadow(if (lifted) 22.dp else 12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand(s.videoId) }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { lifted = true; acc = Offset.Zero; tracker.resetTracking() },
                        onDragCancel = { lifted = false },
                        onDragEnd = {
                            lifted = false
                            val vel = tracker.calculateVelocity()
                            val p = offset.value
                            val off = if (p.x < minX) minX - p.x else if (p.x > maxX) p.x - maxX else 0f
                            if (off > wPx * 0.45f) controller.stop()
                            else scope.launch {
                                offset.animateTo(snapTarget(Offset(p.x + vel.x * 0.18f, p.y + vel.y * 0.18f)), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onDrag = { change, d ->
                            change.consume()
                            acc += Offset(d.x, d.y)
                            tracker.addPosition(change.uptimeMillis, acc)
                            scope.launch {
                                val n = offset.value + Offset(d.x, d.y)
                                offset.snapTo(Offset(n.x, n.y.coerceIn(minY, maxY)))
                            }
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        player = controller.player
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { it.player = controller.player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Transparent, Color.Black.copy(0.45f))),
                ),
            )
            YpIconTap(
                Icons.Filled.Close, "Close", controller::stop,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(0.45f), CircleShape),
                size = 30.dp, iconSize = 18.dp,
            )
            // centred transport: -10s · play/pause · +10s
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                YpIconTap(
                    Icons.Filled.Replay10, "Back 10s", { controller.seekBy(-10_000) },
                    modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape), size = 32.dp, iconSize = 18.dp,
                )
                Box(
                    Modifier.size(42.dp).background(Color.Black.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    else YpIconTap(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Pause" else "Play", controller::toggle, size = 42.dp, iconSize = 26.dp,
                    )
                }
                YpIconTap(
                    Icons.Filled.Forward10, "Forward 10s", { controller.seekBy(10_000) },
                    modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape), size = 32.dp, iconSize = 18.dp,
                )
            }
            val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)) {
                val cy = size.height / 2
                drawLine(Color.White.copy(0.25f), Offset(0f, cy), Offset(size.width, cy), size.height, StrokeCap.Butt)
                drawLine(YouPlayRed, Offset(0f, cy), Offset(size.width * progress, cy), size.height, StrokeCap.Butt)
            }
        }
    }
}
