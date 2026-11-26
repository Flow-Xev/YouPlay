package app.youplay.ui.screens.home

import app.youplay.domain.FeedEntry
import app.youplay.domain.VideoItem

/** Every Shorts row on Home holds exactly this many Shorts. */
const val SHORTS_PER_ROW = 6

/**
 * Pure layout rule for the Home feed, driven by two lists that come from the signed-in account:
 *
 *   video, [Shorts x6], video, video, [Shorts x6], video, video, [Shorts x6], ...
 *
 * Row k (0-based) always sits after video number 1 + 2k, so a row never moves when more data
 * arrives later; it only appears once its 6 Shorts are available. Once the account has no more
 * videos ([videosDone]) the Shorts rows that are left are appended, so the feed ends only when
 * the account runs out of both.
 */
fun composeHomeFeed(
    videos: List<VideoItem>,
    shorts: List<VideoItem>,
    videosDone: Boolean,
    withShorts: Boolean,
): List<FeedEntry> {
    if (!withShorts) return videos.map { FeedEntry.Video(it) }
    val rows = shorts.chunked(SHORTS_PER_ROW).filter { it.size == SHORTS_PER_ROW }
    val out = ArrayList<FeedEntry>(videos.size + rows.size)
    videos.forEachIndexed { i, v ->
        out += FeedEntry.Video(v)
        val n = i + 1
        if (n % 2 == 1) rows.getOrNull((n - 1) / 2)?.let { out += FeedEntry.ShortsRow(it) }
    }
    if (videosDone) {
        val placed = (videos.size + 1) / 2
        for (k in placed until rows.size) out += FeedEntry.ShortsRow(rows[k])
    }
    return out
}

/** How many Shorts the pool should hold to fill the rows the current videos need, plus one row of look-ahead. */
fun shortsTarget(videoCount: Int, extraRows: Int): Int = ((videoCount + 1) / 2 + 1 + extraRows) * SHORTS_PER_ROW
