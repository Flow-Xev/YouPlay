package app.youplay.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.download.OfflineDownloads
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class VideoActionsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val downloads: OfflineDownloads,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun watchLater(v: VideoItem) {
        viewModelScope.launch { toast(repository.saveToWatchLater(v).describe("Saved to Watch later")) }
    }

    fun download(v: VideoItem) {
        downloads.start(v.id, v.title, v.channelName, v.thumbnailUrl)
        toast("Downloading…")
    }
}

/** The ⋮ menu shown on every video card. */
@Composable
fun VideoMenu(video: VideoItem, expanded: Boolean, onDismiss: () -> Unit) {
    val vm: VideoActionsViewModel = hiltViewModel()
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Save to Watch later") }, leadingIcon = { Icon(Icons.Filled.WatchLater, null) },
            onClick = { onDismiss(); vm.watchLater(video) },
        )
        DropdownMenuItem(
            text = { Text("Download") }, leadingIcon = { Icon(Icons.Filled.Download, null) },
            onClick = { onDismiss(); vm.download(video) },
        )
        DropdownMenuItem(
            text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
            onClick = {
                onDismiss()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                context.startActivity(Intent.createChooser(send, "Share"))
            },
        )
    }
}
