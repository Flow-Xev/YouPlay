package app.youplay.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.youplay.domain.StreamInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One app-wide ExoPlayer. The Watch screen, the mini player and the background
 * media-session service all share it, so playback survives navigation.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: app.youplay.data.local.WatchHistoryDao,
    private val extras: app.youplay.data.remote.ExtrasApi,
    private val repository: app.youplay.data.repository.VideoRepository,
    private val net: app.youplay.data.net.NetworkMonitor,
    private val caches: app.youplay.data.cache.CacheManager,
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private suspend fun saveProgress(reset: Boolean = false) {
        val id = _current.value?.videoId ?: return
        val d = player.duration
        val p = player.currentPosition
        runCatching { historyDao.setProgress(id, if (reset || (d > 0 && p > d - 10_000)) 0L else p) }
    }

    private val meter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()
    private val plain = StreamHttp.factory(context)
    private val upstream = StreamHttp.factory(context, meter.transferListener)
    private val mediaCache = app.youplay.ui.player.auto.PlaybackCache(context)
    private val cached = mediaCache.factory(upstream)
    init { caches.addClearHook { mediaCache.clear() } }

    /** Shared cached source used by Shorts playback and its ahead-of-time preloader. */
    fun shortsDataSourceFactory(): androidx.media3.datasource.DataSource.Factory = cached

    /** Cache a short opening segment so the next swipe can start without waiting for the network. */
    suspend fun preloadShortStream(stream: StreamInfo, quality: Int, seconds: Int) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val videoBytes = (app.youplay.ui.player.auto.AutoLadder.bps(quality) / 8L * seconds).coerceAtLeast(250_000L)
            val audioBytes = 24_000L * seconds
            stream.videoUrl?.let { mediaCache.prefetch(cached, it, videoBytes) }
            stream.audioUrl?.let { mediaCache.prefetch(cached, it, audioBytes) }
            // HLS URLs are manifests; ExoPlayer fetches the media segments when playback begins.
        }
    }

    // ---- Auto quality settings (pushed from Settings) ----
    @Volatile var autoMax = 1080
    @Volatile var mobileCap = 720
    @Volatile var preloadEnabled = true
    @Volatile var viewHeightPx = 0
    @Volatile var watchVisible = false
    @Volatile var miniRight = true
    @Volatile var miniBottom = true

    /** Buffer ceiling that follows the network: deep on Wi-Fi or a weak link, shallow on Data Saver / Battery Saver. */
    private fun bufferCapMs(): Long {
        val p = net.profile.value
        var cap = when (p.kind) {
            app.youplay.data.net.NetKind.WIFI, app.youplay.data.net.NetKind.ETHERNET -> 90_000L
            app.youplay.data.net.NetKind.CELLULAR -> 45_000L
            else -> 40_000L
        }
        if (p.metered) cap = minOf(cap, 45_000L)
        if (meter.bitrateEstimate < 1_500_000L) cap = maxOf(cap, 60_000L) // weak link: buffer deeper to ride out stalls
        if (p.dataSaver) cap = minOf(cap, 20_000L)
        if (p.powerSave) cap = minOf(cap, 30_000L)
        return cap
    }

    private val loadControl = app.youplay.ui.player.auto.DynamicLoadControl { bufferCapMs() }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cached))
        .setBandwidthMeter(meter)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC) // snappier scrubbing
        .setWakeMode(C.WAKE_MODE_NETWORK) // keep Wi-Fi awake during background playback
        .build()

    private val _current = MutableStateFlow<StreamInfo?>(null)
    val current: StateFlow<StreamInfo?> = _current.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _quality = MutableStateFlow(0) // 0 = auto (best up to 1080p)
    val quality: StateFlow<Int> = _quality.asStateFlow()
    private val _loop = MutableStateFlow(false)
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    // ---- SponsorBlock ----
    @Volatile var sponsorBlock = true
    private var segments: List<app.youplay.data.remote.SkipSegment> = emptyList()
    private val skippedStarts = HashSet<Long>()
    private val _skipped = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val skipped: kotlinx.coroutines.flow.SharedFlow<String> = _skipped

    // ---- sleep timer / PiP ----
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) { _sleepAt.value = null; return }
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch { kotlinx.coroutines.delay(minutes * 60_000L); player.pause(); _sleepAt.value = null }
    }

    // ---- Smart Auto quality ----
    private val _playingHeight = MutableStateFlow(0)
    val playingHeight: StateFlow<Int> = _playingHeight.asStateFlow()
    private var requestedHeight = 0
    private var lastSwitchAt = 0L
    private var playStartedAt = 0L
    private var lastSeekAt = 0L
    private var upSwitches = 0
    private var switching = false
    private val rebuffers = ArrayDeque<Long>()
    private val est = app.youplay.ui.player.auto.BandwidthEstimator()
    private var stallSince = 0L
    private var lastDownAt = 0L
    private var blockedFrom = 0
    private var blockedUntil = 0L
    private var recoveries = 0
    private val _bandwidth = MutableStateFlow(0L)
    /** Smoothed network speed in bits per second (for the player menu). */
    val bandwidth: StateFlow<Long> = _bandwidth.asStateFlow()

    /** Smoothed throughput; falls back to the raw meter until a few samples exist. */
    private fun bandwidthBps(): Long = if (est.count >= 3) est.estimate() else meter.bitrateEstimate

    /** Highest quality Auto may use right now: user limits, network type, Data/Battery Saver, and the size of the view. */
    private fun capHeight(): Int {
        val p = net.read()
        var cap = autoMax
        if (p.kind == app.youplay.data.net.NetKind.CELLULAR || p.metered) cap = minOf(cap, mobileCap)
        if (p.dataSaver) cap = minOf(cap, 480)
        if (p.powerSave) cap = minOf(cap, 720)
        if (!watchVisible) cap = minOf(cap, 360) // mini player
        val dm = context.resources.displayMetrics
        val px = if (viewHeightPx > 0 && watchVisible) viewHeightPx else minOf(dm.widthPixels, dm.heightPixels) * 9 / 16
        cap = minOf(cap, app.youplay.ui.player.auto.AutoLadder.coveringRung(px))
        // Cold start (no real throughput measured yet): begin at 480p for a fast first frame, then ramp up quickly.
        if (est.count < 3 && meter.bitrateEstimate < 6_000_000L) cap = minOf(cap, 480)
        return cap
    }

    /** Quality to start a new video at, from the measured throughput and current limits. */
    fun autoHeight(): Int =
        app.youplay.ui.player.auto.AutoLadder.affordable((bandwidthBps() * 0.75).toLong(), 1.3, capHeight())

    private fun autoTick() {
        if (player.isLoading || player.isPlaying) est.add(meter.bitrateEstimate)
        val bw = bandwidthBps()
        _bandwidth.value = bw
        if (_quality.value != 0 || switching) return
        val s = _current.value ?: return
        if (s.videoUrl?.startsWith("file:") == true || player.playbackState == Player.STATE_IDLE) return
        if (net.profile.value.kind == app.youplay.data.net.NetKind.NONE) return
        val now = android.os.SystemClock.elapsedRealtime()
        val ladder = app.youplay.ui.player.auto.AutoLadder
        val cap = capHeight()
        val size = player.videoSize
        val cur = minOf(size.width, size.height).takeIf { it > 0 } ?: requestedHeight.takeIf { it > 0 } ?: return
        val buf = player.totalBufferedDuration
        while (rebuffers.isNotEmpty() && now - rebuffers.first() > 60_000) rebuffers.removeFirst()
        val dur = player.duration
        val remaining = if (dur > 0) dur - player.currentPosition else Long.MAX_VALUE

        // how long the current stall has lasted (not the first load, not a seek)
        val stalling = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady &&
            now - playStartedAt > 4_000 && now - lastSeekAt > 5_000 && player.currentPosition > 0
        if (stalling) { if (stallSince == 0L) stallSince = now } else stallSince = 0L
        val stallMs = if (stallSince > 0L) now - stallSince else 0L

        var target: Int? = null
        var why = ""
        var up = false
        if (cur > cap) { target = ladder.affordable(bw, 1.25, cap); why = "limit" }
        else if (player.playWhenReady) {
            val sustainable = ladder.bps(cur) * 1.15 <= bw * 0.9
            if (stallMs > 3_000 || (buf < 5_000 && rebuffers.isNotEmpty()) || (buf < 9_000 && !sustainable)) {
                var t = minOf(ladder.affordable((bw * 0.7).toLong(), 1.25, cap), ladder.below(cur))
                if (stallMs > 8_000 || rebuffers.size >= 3) t = minOf(t, ladder.below(ladder.below(cur))) // panic: drop two rungs
                target = t; why = "slow network"
            } else if (cur < cap && now - playStartedAt > 4_000 && remaining > 30_000 && now - lastDownAt > 30_000) {
                val fastRamp = upSwitches == 0
                val cooldown = if (fastRamp) 3_500L else 15_000L
                val needBuf = if (fastRamp) 7_000L else 12_000L
                if (now - lastSwitchAt > cooldown && buf >= needBuf && upSwitches < 10) {
                    // don't climb back to a quality that just failed us
                    val ceiling = if (now < blockedUntil) minOf(cap, ladder.below(blockedFrom)) else cap
                    val best = ladder.affordable((bw * 0.8).toLong(), 1.6, ceiling)
                    if (best > cur) {
                        // one rung at a time, unless there is a lot of headroom
                        val step = if (bw * 0.8 >= ladder.bps(best) * 2.5) best else minOf(best, ladder.above(cur))
                        target = step; why = "faster network"; up = true
                    }
                }
            }
        }
        if (target != null && target != cur && (why == "limit" || now - lastSwitchAt > 5_000)) {
            if (why == "slow network") { lastDownAt = now; blockedFrom = cur; blockedUntil = now + 120_000 }
            switchTo(target, why, up)
        }
    }

    private fun switchTo(h: Int, why: String, up: Boolean) {
        val s = _current.value ?: return
        switching = true
        scope.launch {
            try {
                val ns = repository.stream(s.videoId, h, record = false)
                if (_current.value?.videoId != s.videoId) return@launch
                play(ns, player.currentPosition, height = h, fromSwitch = true)
                if (up) upSwitches++
                _skipped.tryEmit("Auto: ${h}p · $why")
            } catch (_: Throwable) {
                // keep playing the current quality
            } finally {
                lastSwitchAt = android.os.SystemClock.elapsedRealtime()
                switching = false
            }
        }
    }

    // ---- preloading the next video ----
    private class Pre(val stream: StreamInfo, val height: Int, val at: Long)
    private val preloaded = java.util.concurrent.ConcurrentHashMap<String, Pre>()
    private var nextJob: kotlinx.coroutines.Job? = null

    /** Stream already resolved (and partly cached) for [id], if any; consumed on use. */
    fun takePreloaded(id: String): StreamInfo? =
        preloaded.remove(id)?.takeIf { System.currentTimeMillis() - it.at < 20 * 60_000 }?.stream

    /** Once the current video is ~20s in, resolve the next one; on unmetered networks also cache its first seconds. */
    fun preloadNext(id: String?) {
        nextJob?.cancel()
        if (id == null || !preloadEnabled || preloaded.containsKey(id)) return
        nextJob = scope.launch {
            while (true) {
                val d = player.duration
                val p = player.currentPosition
                if (p > 20_000 || (d > 0 && p > d * 0.3)) break
                kotlinx.coroutines.delay(2_000)
            }
            val prof = net.read()
            if (prof.kind == app.youplay.data.net.NetKind.NONE) return@launch
            val h = if (_quality.value == 0) autoHeight() else _quality.value
            val st = runCatching { repository.stream(id, h, record = false) }.getOrNull() ?: return@launch
            preloaded[id] = Pre(st, h, System.currentTimeMillis())
            if (!prof.metered && !prof.dataSaver && !prof.powerSave) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    st.videoUrl?.takeIf { it.startsWith("http") }?.let { mediaCache.prefetch(cached, it, 3_000_000L) }
                    st.audioUrl?.takeIf { it.startsWith("http") && it != st.videoUrl }?.let { mediaCache.prefetch(cached, it, 800_000L) }
                }
            }
        }
    }

    /** Stop fetching video while only the audio is audible (screen off / app in background). */
    fun setVideoEnabled(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on).build()
    }

    /** Invoked when playback reaches the end (used for autoplay-next). */
    var onEnded: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) scope.launch { saveProgress() }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                _buffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    // a stall mid-playback (not the first load, not a seek)
                    if (now - playStartedAt > 4_000 && now - lastSeekAt > 5_000 && player.currentPosition > 0) rebuffers.addLast(now)
                }
                if (playbackState == Player.STATE_READY) _error.value = null
                if (playbackState == Player.STATE_ENDED) { scope.launch { saveProgress(reset = true) }; onEnded?.invoke() }
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _playingHeight.value = minOf(videoSize.width, videoSize.height)
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
            ) { if (reason == Player.DISCONTINUITY_REASON_SEEK) lastSeekAt = android.os.SystemClock.elapsedRealtime() }
            override fun onPlayerError(error: PlaybackException) {
                val cur = _current.value
                val msg = (error.message ?: "") + " " + (error.cause?.message ?: "")
                // Recover Source / 403 / 410 / network stalls by re-resolving streams (up to 3x).
                val recoverable = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
                    || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    || msg.contains("403") || msg.contains("410") || msg.contains("Source error", ignoreCase = true)
                    || msg.contains("Unable to connect", ignoreCase = true)
                if (cur != null && recoverable && recoveries < 3 && cur.videoUrl?.startsWith("file:") != true) {
                    recoveries++
                    val pos = player.currentPosition
                    val h = if (_quality.value == 0) autoHeight() else _quality.value
                    scope.launch {
                        runCatching { repository.stream(cur.videoId, h, record = false, fresh = true) }
                            .onSuccess { info ->
                                if (_current.value?.videoId != cur.videoId) return@onSuccess
                                // Prefer progressive / HLS if a merged A/V pair caused the failure.
                                val safe = if (!info.hlsUrl.isNullOrBlank() && recoveries >= 2) {
                                    info.copy(videoUrl = null, audioUrl = null)
                                } else if (info.videoUrl != null && info.audioUrl != null && info.videoUrl != info.audioUrl && recoveries >= 2) {
                                    info.copy(audioUrl = info.videoUrl) // force single progressive if available later
                                } else info
                                play(safe, pos, height = h, fromSwitch = true)
                            }
                            .onFailure { _error.value = StreamHttp.describe(error) }
                    }
                } else _error.value = StreamHttp.describe(error)
            }
        })
        scope.launch { while (true) { kotlinx.coroutines.delay(5_000); if (player.isPlaying) saveProgress() } }
        scope.launch { while (true) { kotlinx.coroutines.delay(1_000); runCatching { autoTick() } } }
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(400)
                if (!sponsorBlock || !player.isPlaying || segments.isEmpty()) continue
                val pos = player.currentPosition
                val seg = segments.firstOrNull { pos >= it.start && pos < it.end - 400 && it.start !in skippedStarts } ?: continue
                skippedStarts += seg.start
                player.seekTo(seg.end)
                _skipped.tryEmit("Skipped " + when (seg.category) {
                    "sponsor" -> "sponsor"; "selfpromo" -> "self-promo"; "intro" -> "intro"
                    "outro" -> "outro"; "interaction" -> "reminder"; else -> "segment"
                })
            }
        }
    }

    fun play(rawStream: StreamInfo, startMs: Long = 0L, height: Int = 0, fromSwitch: Boolean = false) {
        _error.value = null
        // never hand ExoPlayer a URL java.net.URL would reject (MalformedURLException -> "Source error")
        val stream = app.youplay.data.remote.UrlGuard.sanitize(rawStream) ?: run {
            _current.value = rawStream
            _error.value = "Stream address was invalid. Tap retry."
            return
        }
        _current.value = stream
        val nowMs = android.os.SystemClock.elapsedRealtime()
        playStartedAt = nowMs
        if (height > 0) requestedHeight = height
        val keepPlaying = if (fromSwitch) player.playWhenReady else true
        if (!fromSwitch) {
            recoveries = 0; stallSince = 0L
            segments = emptyList(); skippedStarts.clear()
            upSwitches = 0; lastSwitchAt = nowMs; rebuffers.clear()
            if (sponsorBlock) scope.launch {
                val found = extras.segments(stream.videoId)
                if (_current.value?.videoId == stream.videoId) segments = found
            }
        }
        val meta = MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.channelName)
            .setArtworkUri(stream.thumbnailUrl?.let { Uri.parse(it) })
            .build()
        fun item(u: String) = MediaItem.Builder().setUri(u).setMediaMetadata(meta).build()
        fun src(url: String, mi: MediaItem) =
            ProgressiveMediaSource.Factory(if (url.startsWith("file:")) plain else cached).createMediaSource(mi)
        // A quality switch replaces the media in place (no stop), so the last frame stays on screen.
        if (!fromSwitch) { player.stop(); player.clearMediaItems() }
        val start = startMs.coerceAtLeast(0L)
        try {
            when {
                !stream.hlsUrl.isNullOrBlank() && stream.videoUrl.isNullOrBlank() ->
                    player.setMediaItem(
                        MediaItem.Builder().setUri(stream.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8)
                            .setMediaMetadata(meta).build(),
                        start,
                    )
                !stream.videoUrl.isNullOrBlank() && !stream.audioUrl.isNullOrBlank() && stream.videoUrl != stream.audioUrl ->
                    player.setMediaSource(
                        MergingMediaSource(
                            src(stream.videoUrl, item(stream.videoUrl)),
                            src(stream.audioUrl, MediaItem.fromUri(stream.audioUrl)),
                        ),
                        start,
                    )
                !stream.videoUrl.isNullOrBlank() -> player.setMediaSource(src(stream.videoUrl, item(stream.videoUrl)), start)
                !stream.audioUrl.isNullOrBlank() -> player.setMediaSource(src(stream.audioUrl, item(stream.audioUrl)), start)
                !stream.hlsUrl.isNullOrBlank() -> player.setMediaItem(item(stream.hlsUrl), start)
                else -> { _error.value = "No playable stream URL"; return }
            }
            player.repeatMode = if (_loop.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.setPlaybackSpeed(_speed.value)
            player.prepare()
            player.playWhenReady = keepPlaying
            startService()
        } catch (t: Throwable) {
            _error.value = t.message ?: "Playback error"
        }
    }

    private fun startService() {
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }

    /** Retry with a freshly resolved stream (the old URL may have expired); falls back to the old one. */
    fun retry() {
        val cur = _current.value ?: return
        val pos = player.currentPosition
        val h = if (_quality.value == 0) autoHeight() else _quality.value
        scope.launch {
            val fresh = runCatching { repository.stream(cur.videoId, h, record = false, fresh = true) }.getOrNull()
            play(fresh ?: cur, pos, height = h)
        }
    }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun pause() = player.pause()
    fun seekBy(ms: Long) {
        val d = player.duration
        val t = (player.currentPosition + ms).coerceAtLeast(0L)
        player.seekTo(if (d > 0) t.coerceAtMost(d) else t)
    }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun setSpeed(v: Float) { _speed.value = v; player.setPlaybackSpeed(v) }
    /** Temporary 2x while the finger is held down; restores the chosen speed afterwards. */
    fun boost(on: Boolean) { player.setPlaybackSpeed(if (on) 2f else _speed.value) }
    fun setQualityValue(h: Int) { _quality.value = h }
    fun setLoop(v: Boolean) { _loop.value = v; player.repeatMode = if (v) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _current.value = null
        _isPlaying.value = false
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }
}

@HiltViewModel
class PlayerHostViewModel @Inject constructor(val controller: PlayerController) : ViewModel()
