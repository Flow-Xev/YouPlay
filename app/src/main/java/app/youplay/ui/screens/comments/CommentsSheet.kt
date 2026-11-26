package app.youplay.ui.screens.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.domain.CommentItem
import app.youplay.domain.UiState
import app.youplay.ui.components.IconButton
import app.youplay.ui.components.YpAvatar
import app.youplay.ui.theme.YouPlayRed

private val SheetBg = Color(0xFF1A1A1A)
private val Muted = Color(0xFFAAAAAA)

/**
 * Comments as a bottom sheet (YouTube style). Loads, posts, replies, likes and deletes with the signed-in account.
 * Used from the Watch page and from Shorts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(videoId: String, countText: String? = null, onDismiss: () -> Unit) {
    val vm: CommentsViewModel = hiltViewModel(key = "comments_$videoId")
    LaunchedEffect(videoId) { vm.start(videoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var confirmDelete by remember { mutableStateOf<CommentItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val ui = (state as? UiState.Success)?.data
    LaunchedEffect(message) { message?.let { toast = it; vm.messageShown() } }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2400); toast = null } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = SheetBg, contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull("Comments", countText?.takeIf { it.isNotBlank() }).joinToString(" · "), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
            toast?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val st = state) {
                    UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = YouPlayRed)
                    is UiState.Error -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.message, color = Muted, fontSize = 13.sp)
                        TextButton(onClick = vm::refresh) { Text("Retry", color = YouPlayRed) }
                    }
                    is UiState.Success -> {
                        val data = st.data
                        if (data.comments.isEmpty()) Text("No comments yet.", color = Muted, modifier = Modifier.align(Alignment.Center))
                        else LazyColumn(Modifier.fillMaxWidth()) {
                            items(data.comments, key = { it.id }) { c ->
                                CommentRow(
                                    c, liked = c.id in data.liked, canReply = signedIn && c.replyParams != null,
                                    onLike = { vm.toggleLike(c) }, onReply = { replyTo = c }, onDelete = { confirmDelete = c },
                                )
                            }
                            if (data.continuation != null) item {
                                TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (data.loadingMore) "Loading…" else "Load more comments", color = YouPlayRed)
                                }
                            }
                        }
                    }
                }
            }
            // Composer: posts to the signed-in account.
            Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).navigationBarsPadding().imePadding().padding(10.dp)) {
                replyTo?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to ${r.author}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        IconButton(onClick = { replyTo = null }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Cancel reply", tint = Muted) }
                    }
                }
                if (!signedIn) {
                    Text("Sign in (You tab) to comment", color = Muted, modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                } else if (ui != null && ui.createParams == null && replyTo == null) {
                    Text("Comments are unavailable for this video", color = Muted, modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        YpAvatar(account.avatarUrl, account.name ?: "?", 34.dp)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text(if (replyTo != null) "Add a reply…" else "Add a comment…", color = Muted) },
                            modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp),
                        )
                        if (ui?.posting == true) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp, color = YouPlayRed)
                        else IconButton(onClick = { vm.post(text, replyTo) { text = ""; replyTo = null } }, enabled = text.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (text.isNotBlank()) YouPlayRed else Muted)
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete comment?") },
            text = { Text("This removes it from YouTube.") },
            confirmButton = { TextButton(onClick = { vm.delete(c); confirmDelete = null }) { Text("Delete", color = YouPlayRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CommentRow(
    c: CommentItem, liked: Boolean, canReply: Boolean,
    onLike: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        YpAvatar(c.authorThumbUrl, c.author, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(c.author, c.publishedText).joinToString(" · "), color = Muted, fontSize = 12.sp)
            Text(c.text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (c.likeToken != null || c.unlikeToken != null) {
                    IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                        Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", tint = if (liked) YouPlayRed else Muted, modifier = Modifier.size(18.dp))
                    }
                }
                c.likeCountText?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 12.sp) }
                if (canReply) TextButton(onClick = onReply) { Text("Reply", color = Muted) }
                if (c.replyCount > 0) Text("${c.replyCount} replies", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (c.deleteToken != null) IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
