package app.youplay.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import app.youplay.domain.StreamInfo
import app.youplay.ui.theme.YouPlayRed

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    stream: StreamInfo,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    resizeModeFill: Boolean = false,
    speed: Float = 1f,
    onError: ((String) -> Unit)? = null,
    paused: Boolean = false,
    muted: Boolean = false,
    loop: Boolean = true,
    onPlayer: (ExoPlayer) -> Unit = {},
    onEnded: () -> Unit = {},
    dataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null,
) {
    val context = LocalContext.current
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember {
        val httpFactory = dataSourceFactory ?: StreamHttp.factory(context)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                repeatMode = if (resizeModeFill) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                this.playWhenReady = playWhenReady
            }
    }

    LaunchedEffect(stream.videoId, stream.videoUrl, stream.audioUrl, stream.hlsUrl) {
        error = null
        buffering = true
        // never hand ExoPlayer a URL java.net.URL would reject (MalformedURLException)
        val raw = stream
        val stream = app.youplay.data.remote.UrlGuard.sanitize(raw) ?: run {
            error = "Stream address was invalid"
            onError?.invoke(error!!)
            return@LaunchedEffect
        }
        player.stop()
        player.clearMediaItems()
        try {
            val httpFactory = dataSourceFactory ?: StreamHttp.factory(context)
            val progressive = ProgressiveMediaSource.Factory(httpFactory)
            when {
                !stream.hlsUrl.isNullOrBlank() && stream.videoUrl.isNullOrBlank() -> {
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setUri(stream.hlsUrl)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build(),
                    )
                }
                !stream.videoUrl.isNullOrBlank() && !stream.audioUrl.isNullOrBlank() &&
                    stream.videoUrl != stream.audioUrl -> {
                    val videoSource = progressive.createMediaSource(MediaItem.fromUri(stream.videoUrl))
                    val audioSource = progressive.createMediaSource(MediaItem.fromUri(stream.audioUrl))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                }
                !stream.videoUrl.isNullOrBlank() -> {
                    player.setMediaItem(MediaItem.fromUri(stream.videoUrl))
                }
                !stream.audioUrl.isNullOrBlank() -> {
                    player.setMediaItem(MediaItem.fromUri(stream.audioUrl))
                }
                !stream.hlsUrl.isNullOrBlank() -> {
                    player.setMediaItem(MediaItem.Builder().setUri(stream.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                }
                else -> { error = "No playable stream URL"; onError?.invoke(error!!) }
            }
            player.prepare()
        } catch (t: Throwable) {
            error = t.message
            onError?.invoke(t.message ?: "Playback error")
        }
    }

    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(playWhenReady, paused) { player.playWhenReady = playWhenReady && !paused }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(loop, resizeModeFill) { player.repeatMode = if (resizeModeFill && loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(player) { onPlayer(player) }
    val endedCb by androidx.compose.runtime.rememberUpdatedState(onEnded)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_ENDED) endedCb()
            }
            override fun onPlayerError(e: PlaybackException) {
                error = StreamHttp.describe(e)
                onError?.invoke(error!!)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (resizeModeFill) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                // TextureView surface: a SurfaceView renders black inside a pager / clipped, translated layout.
                val v = if (resizeModeFill) {
                    android.view.LayoutInflater.from(ctx).inflate(app.youplay.R.layout.yp_texture_player, null) as PlayerView
                } else PlayerView(ctx)
                v.apply {
                    useController = !resizeModeFill
                    if (resizeModeFill) resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && error == null) {
            CircularProgressIndicator(color = YouPlayRed)
        }
    }
}
