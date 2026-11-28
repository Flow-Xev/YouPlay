package app.youplay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youplay.ui.theme.YouPlayRed
import coil.compose.AsyncImage

/** Press-and-release squish used by every tappable. Put it FIRST in the chain so the whole element scales. */
fun Modifier.bounceClick(enabled: Boolean = true, pressed: Float = 0.93f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (down) pressed else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Custom chrome — YouTube density, Flow thinness, no Material buttons/chips. */

@Composable
fun YpAvatar(url: String?, name: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A2A)),
        )
    } else ChannelInitial(name, size)
}

@Composable
fun YpSubscribe(
    subscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val bg by animateColorAsState(if (subscribed) Color(0xFF272727) else YouPlayRed, label = "sub")
    val fg = Color.White
    val pop = remember { Animatable(1f) }
    LaunchedEffect(subscribed) { pop.snapTo(0.9f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
    Row(
        modifier.bounceClick(onClick = onClick)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f))
            .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (subscribed) Icon(Icons.Filled.NotificationsNone, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(if (subscribed) "Subscribed" else "Subscribe", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun YpActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val bg by animateColorAsState(if (active) YouPlayRed.copy(0.22f) else Color(0xFF242424), label = "pillbg")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) { if (active) { pop.snapTo(0.6f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) } }
    Row(
        modifier.bounceClick(onClick = onClick)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (active) YouPlayRed.copy(0.45f) else Color.White.copy(0.08f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            icon, label, tint = if (active) YouPlayRed else Color.White,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Like | dislike joined in one pill with a hairline divider; the active side lights up in the accent. */
@Composable
fun YpLikeDislikePill(
    liked: Boolean,
    disliked: Boolean,
    likeLabel: String,
    dislikeLabel: String?,
    likeIcon: ImageVector,
    dislikeIcon: ImageVector,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val likePop = remember { Animatable(1f) }
    val dislikePop = remember { Animatable(1f) }
    LaunchedEffect(liked) { if (liked) { likePop.snapTo(0.55f); likePop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f)) } }
    LaunchedEffect(disliked) { if (disliked) { dislikePop.snapTo(0.55f); dislikePop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f)) } }
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(50)).background(Color(0xFF242424)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.bounceClick(onClick = onLike).fillMaxHeight().padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                likeIcon, "Like", tint = if (liked) YouPlayRed else Color.White,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = likePop.value; scaleY = likePop.value },
            )
            Text(likeLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.18f)))
        Row(
            Modifier.bounceClick(onClick = onDislike).fillMaxHeight().padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                dislikeIcon, "Dislike", tint = if (disliked) YouPlayRed else Color.White,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = dislikePop.value; scaleY = dislikePop.value },
            )
            if (!dislikeLabel.isNullOrBlank()) Text(dislikeLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun YpIconTap(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.bounceClick(pressed = 0.85f, onClick = onClick)
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun YpChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) Color.White else Color(0xFF272727), label = "chip")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, label = "chipfg")
    Text(
        label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.bounceClick(pressed = 0.9f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun YpPrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(YouPlayRed)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun YpGhostButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF3F3F3F), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun YpScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Color.Black), content = { content() })
}

@Composable
fun RelatedVideoRow(
    id: String,
    title: String,
    channelName: String,
    views: String?,
    published: String?,
    thumbnailUrl: String?,
    durationText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.bounceClick(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(168.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            AsyncImage(
                model = thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (!durationText.isNullOrBlank()) {
                Text(
                    durationText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(0.8f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(channelName.takeIf { it.isNotBlank() }, views, published).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun CompactSearchRow(
    id: String,
    title: String,
    channelName: String,
    meta: String?,
    thumbnailUrl: String?,
    durationText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = RelatedVideoRow(id, title, channelName, meta, null, thumbnailUrl, durationText, onClick, modifier)

@Composable
fun ShortsRailButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(tint, icon) { pop.snapTo(0.6f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f)) }
    Column(
        modifier.bounceClick(pressed = 0.88f, onClick = onClick).widthIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (filled) Color.White.copy(0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Non-Material drop-ins (same call shape as the old IconButton/Button) so screens keep compiling. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.bounceClick(enabled, 0.85f, onClick)
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier.bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(YouPlayRed)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White,
            ) { content() }
        },
    )
}
