package app.youplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youplay.domain.Chapter
import app.youplay.ui.theme.YouPlayAccentSoft
import app.youplay.ui.theme.YouPlayRed

/**
 * Dedicated chapters sheet (chapters never appear in the description sheet).
 * Numbered rows with start time and length; the chapter now playing is tinted with the
 * YouPlay accent and the list opens scrolled to it. Tap a row to jump there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    chapters: List<Chapter>,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentIdx = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIdx > 2) listState.scrollToItem(currentIdx - 1) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = Color(0xFF1A1A1A), contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chapters", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${chapters.size} parts", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                itemsIndexed(chapters, key = { i, c -> "$i-${c.startMs}" }) { i, c ->
                    val active = i == currentIdx
                    val end = chapters.getOrNull(i + 1)?.startMs ?: durationMs
                    val len = (end - c.startMs).takeIf { it > 0 }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) YouPlayAccentSoft else Color.Transparent)
                            .clickable { onSeek(c.startMs); onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (active) YouPlayRed else Color.White.copy(0.12f)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "%02d".format(i + 1), color = if (active) YouPlayRed else Color(0xFF777777),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title, color = Color.White, fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(stampOf(c.startMs), len?.let { "${stampOf(it)} long" }).joinToString("  ·  "),
                                color = Color(0xFFAAAAAA), fontSize = 12.sp,
                            )
                        }
                        if (active) Text(
                            "Now", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(YouPlayRed)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

private fun stampOf(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}
