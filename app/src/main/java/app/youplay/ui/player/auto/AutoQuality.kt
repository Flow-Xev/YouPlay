package app.youplay.ui.player.auto

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.LoadControl
import java.io.File

/** Resolution ladder with typical audio+video bitrates (bits per second). */
object AutoLadder {
    val rungs = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)

    fun bps(h: Int): Long = when {
        h <= 144 -> 300_000L
        h <= 240 -> 500_000L
        h <= 360 -> 850_000L
        h <= 480 -> 1_400_000L
        h <= 720 -> 2_700_000L
        h <= 1080 -> 5_000_000L
        h <= 1440 -> 10_000_000L
        else -> 20_000_000L
    }

    /** Highest rung <= cap whose bitrate times [headroom] fits in [bandwidth]. */
    fun affordable(bandwidth: Long, headroom: Double, cap: Int): Int =
        rungs.lastOrNull { it <= cap && bps(it) * headroom <= bandwidth } ?: rungs.first()

    fun below(h: Int): Int = rungs.lastOrNull { it < h } ?: rungs.first()

    fun above(h: Int): Int = rungs.firstOrNull { it > h } ?: rungs.last()

    /** Smallest rung that fully covers [px] screen pixels of height (no point streaming more than the view can show). */
    fun coveringRung(px: Int): Int = rungs.firstOrNull { it >= px } ?: rungs.last()
}

/**
 * Conservative bandwidth estimate: harmonic mean of the last few samples, so one slow sample pulls the
 * estimate down much more than one fast sample lifts it (the standard approach in adaptive streaming).
 */
class BandwidthEstimator(private val window: Int = 8) {
    private val samples = ArrayDeque<Long>()
    @Volatile var count = 0
        private set

    @Synchronized fun add(bps: Long) {
        if (bps <= 0) return
        samples.addLast(bps)
        while (samples.size > window) samples.removeFirst()
        count++
    }

    @Synchronized fun estimate(): Long {
        if (samples.isEmpty()) return 0L
        val inv = samples.sumOf { 1.0 / it }
        return (samples.size / inv).toLong()
    }
}

/**
 * DefaultLoadControl that also stops fetching once the buffer reaches a cap that changes with the
 * network (deep buffer on Wi-Fi or a weak link, shallow on Data Saver).
 * Extends DefaultLoadControl (instead of delegating with `by`) because Kotlin delegation skips Java
 * default methods, which made newer Media3 throw "getBackBufferDurationUs not implemented".
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DynamicLoadControl(
    private val capMs: () -> Long,
) : androidx.media3.exoplayer.DefaultLoadControl(
    androidx.media3.exoplayer.upstream.DefaultAllocator(true, androidx.media3.common.C.DEFAULT_BUFFER_SEGMENT_SIZE),
    15_000, 120_000, 1_000, 3_000,
    androidx.media3.common.C.LENGTH_UNSET, true,
    20_000, true,
) {
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (parameters.bufferedDurationUs / 1000 >= capMs()) return false
        return super.shouldContinueLoading(parameters)
    }
}

/** Disk cache for media bytes: backs preloading the next video and re-reading after a quality switch. */
class PlaybackCache(context: Context) {
    val cache = SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
        StandaloneDatabaseProvider(context),
    )

    /** googlevideo URLs carry expiring tokens; key on the stable media id + itag so re-resolved URLs still hit. */
    private val keys = CacheKeyFactory { spec: DataSpec ->
        val u: Uri = spec.uri
        val id = u.getQueryParameter("id"); val itag = u.getQueryParameter("itag")
        if (id != null && itag != null) "$id-$itag" else u.toString()
    }

    fun factory(upstream: DataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory(keys)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Remove every cached media resource (best effort; entries currently being read are skipped). */
    fun clear() { runCatching { cache.keys.toList().forEach { runCatching { cache.removeResource(it) } } } }

    /** Download the first [bytes] of [url] into the cache (blocking; call off the main thread). */
    fun prefetch(factory: CacheDataSource.Factory, url: String, bytes: Long) {
        runCatching {
            CacheWriter(
                factory.createDataSource(),
                DataSpec.Builder().setUri(url).setPosition(0).setLength(bytes).build(),
                null, null,
            ).cache()
        }
    }
}
