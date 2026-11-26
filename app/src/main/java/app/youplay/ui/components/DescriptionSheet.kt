package app.youplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youplay.domain.Chapter
import app.youplay.ui.theme.YouPlayRed

private val Muted = Color(0xFFAAAAAA)
private val Card = Color(0xFF242424)

/**
 * YouTube-style Description sheet: title, a Views / Date stat strip, channel, chapters, and the full
 * selectable description in a card. Views and date come from the video's page on the account session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionSheet(
    title: String,
    channelName: String,
    channelAvatarUrl: String?,
    subscriberText: String?,
    views: String?,
    published: String?,
    description: String?,
    chapters: List<Chapter> = emptyList(),
    onSeek: (Long) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val v = viewsStat(views)
    val d = dateStat(published)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = Color(0xFF1A1A1A), contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Description", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                Spacer(Modifier.height(14.dp))
                if (v != null || d != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        v?.let { Stat(it.first, it.second) }
                        if (v != null && d != null) Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(0.12f)))
                        d?.let { Stat(it.first, it.second) }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    YpAvatar(channelAvatarUrl, channelName.ifBlank { "?" }, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(channelName.ifBlank { "Channel" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        subscriberText?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (chapters.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
                        Text("Chapters", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        chapters.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSeek(c.startMs); onDismiss() }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stamp(c.startMs), color = YouPlayRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                                Text(c.title, color = Color.White, fontSize = 13.sp, maxLines = 2)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(14.dp)) {
                    SelectionContainer {
                        Text(
                            description?.takeIf { it.isNotBlank() } ?: "No description.",
                            color = Color(0xFFE0E0E0), fontSize = 14.sp, lineHeight = 21.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Stat(big: String, small: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(big, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(small, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** "1,234,567 views" -> ("1.2M", "Views"). Unparseable text is shown as is. */
private fun viewsStat(raw: String?): Pair<String, String>? {
    val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val n = t.filter { it.isDigit() }.toLongOrNull()
    if (n == null || t.any { it.isLetter() && it !in "views" && it !in "View" }) return t to "Views"
    val big = when {
        n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
        n >= 1_000_000 -> "%.1fM".format(n / 1e6)
        n >= 10_000 -> "${n / 1000}K"
        else -> "%,d".format(n)
    }.replace(".0", "")
    return big to "Views"
}

/** "Nov 5, 2025" -> ("Nov 5", "2025"); "2025-11-05" -> same; relative dates are shown as is. */
private fun dateStat(raw: String?): Pair<String, String>? {
    val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    Regex("""^(?:Premiered |Streamed live on |Published on )?([A-Za-z]{3,9}\.? \d{1,2}),? (\d{4})$""").find(t)?.let {
        return it.groupValues[1] to it.groupValues[2]
    }
    Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(t)?.let {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val m = it.groupValues[2].toInt()
        if (m in 1..12) return "${months[m - 1]} ${it.groupValues[3].toInt()}" to it.groupValues[1]
    }
    return t to "Uploaded"
}

private fun stamp(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}
