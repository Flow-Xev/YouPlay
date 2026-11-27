package app.youplay.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Moving highlight shared by every skeleton block. */
@Composable
fun rememberShimmer(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -500f, 1500f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = Color.White.copy(alpha = 0.06f)
    val hi = Color.White.copy(alpha = 0.14f)
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 500f, 250f))
}

/** Placeholder for the watch page while the video details load: title, channel row, action pills, up-next rows. */
@Composable
fun WatchSkeleton(modifier: Modifier = Modifier) {
    val b = rememberShimmer()
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Box(Modifier.fillMaxWidth(0.92f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(b))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(b))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(b))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(b))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.45f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(b))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.3f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(b))
            }
            Box(Modifier.width(96.dp).height(32.dp).clip(RoundedCornerShape(50)).background(b))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(120.dp, 84.dp, 96.dp, 80.dp).forEach { w ->
                Box(Modifier.width(w).height(36.dp).clip(RoundedCornerShape(50)).background(b))
            }
        }
        Spacer(Modifier.height(22.dp))
        repeat(4) {
            Row(Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.width(168.dp).height(94.dp).clip(RoundedCornerShape(8.dp)).background(b))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Box(Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(4.dp)).background(b))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(b))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(b))
                }
            }
        }
    }
}

/** Placeholder rows for the comments sheet. */
@Composable
fun CommentsSkeleton(modifier: Modifier = Modifier) {
    val b = rememberShimmer()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        repeat(6) { i ->
            Row(Modifier.padding(vertical = 10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(b))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(b))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.95f else 0.8f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(b))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.6f else 0.45f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(b))
                }
            }
        }
    }
}
