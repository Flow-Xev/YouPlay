package app.youplay.domain

data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String = "",
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val viewCountText: String? = null,
    val publishedText: String? = null,
    val description: String? = null,
    val isShort: Boolean = false,
    val channelAvatarUrl: String? = null,
)

data class ChannelItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val subscriberCountText: String? = null,
    val videoCountText: String? = null,
    val description: String? = null,
)

data class CommentItem(
    val id: String,
    val author: String,
    val authorThumbUrl: String? = null,
    val text: String,
    val likeCountText: String? = null,
    val publishedText: String? = null,
    val replyCount: Int = 0,
    /** Action tokens YouTube attaches to a comment; null when the action isn't available to this account. */
    val deleteToken: String? = null,
    val likeToken: String? = null,
    val unlikeToken: String? = null,
    val replyParams: String? = null,
)

data class CommentsPage(
    val comments: List<CommentItem>,
    val continuation: String?,
    val createParams: String?,
)

data class StreamInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val videoUrl: String?,
    val audioUrl: String?,
    val hlsUrl: String? = null,
    val mimeType: String? = null,
    val playbackUrl: String? = null,
    val cpn: String? = null,
)

data class ChipItem(val label: String, val token: String?, val selected: Boolean = false)

data class PlaylistItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val subtitle: String? = null,
)

/** Home feed = ordered rows. Shorts rows render as a horizontal carousel. */
sealed interface FeedEntry {
    data class Video(val item: VideoItem) : FeedEntry
    data class ShortsRow(val shorts: List<VideoItem>, val title: String = "Shorts") : FeedEntry
}

data class FeedPage(
    val entries: List<FeedEntry>,
    val chips: List<ChipItem> = emptyList(),
    val continuation: String? = null,
)

data class AccountState(
    val signedIn: Boolean = false,
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null,
)

data class HomeFeed(
    val sections: List<HomeSection>,
)

data class HomeSection(
    val title: String,
    val videos: List<VideoItem>,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

enum class SearchType(val label: String) { ALL("All"), VIDEOS("Videos"), SHORTS("Shorts"), CHANNELS("Channels"), PLAYLISTS("Playlists") }

/** date: 1 hour,2 today,3 week,4 month,5 year | duration: 1 <4m, 3 4-20m, 2 >20m | sort: 0 relevance,2 date,3 views,1 rating */
data class SearchFilter(
    val type: SearchType = SearchType.ALL,
    val date: Int? = null,
    val duration: Int? = null,
    val sort: Int? = null,
) {
    /** Encodes the filter as YouTube's `params` protobuf (base64). */
    fun toParams(): String? {
        var t = when (type) {
            SearchType.ALL -> null
            SearchType.VIDEOS, SearchType.SHORTS -> 1
            SearchType.CHANNELS -> 2
            SearchType.PLAYLISTS -> 3
        }
        val dur = if (type == SearchType.SHORTS) 1 else duration
        if (dur != null && t == null) t = 1
        if (t == null && date == null && dur == null && sort == null) return null
        val inner = java.io.ByteArrayOutputStream()
        date?.let { inner.write(0x08); inner.write(it) }
        t?.let { inner.write(0x10); inner.write(it) }
        dur?.let { inner.write(0x18); inner.write(it) }
        val out = java.io.ByteArrayOutputStream()
        sort?.let { out.write(0x08); out.write(it) }
        if (inner.size() > 0) { out.write(0x12); out.write(inner.size()); out.write(inner.toByteArray()) }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }
}

data class SearchPage(
    val entries: List<FeedEntry>,
    val channels: List<ChannelItem>,
    val playlists: List<PlaylistItem>,
    val shorts: List<VideoItem>,
    val continuation: String?,
)

data class WatchExtras(
    val related: List<VideoItem>,
    val description: String? = null,
    val views: String? = null,
    val published: String? = null,
    val channelAvatarUrl: String? = null,
    val subscriberText: String? = null,
    val commentCountText: String? = null,
    val likeCountText: String? = null,
)

/** One shelf of a creator's channel Home (e.g. "Popular uploads", "Summer playlist", "Shorts"). */
data class ChannelShelf(
    val title: String,
    val videos: List<VideoItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val isShorts: Boolean = false,
    val featured: Boolean = false,
    /** Set when the shelf is a playlist; tapping the title opens it. */
    val playlistId: String? = null,
)

/** One page of the Shorts feed plus the token for the next page (null = end). */
data class ShortsPage(val videos: List<VideoItem>, val next: String? = null)

data class Chapter(val title: String, val startMs: Long)
data class Storyboard(val urls: List<String>, val frameW: Int, val frameH: Int, val perX: Int, val perY: Int, val count: Int, val durPerFrameMs: Int)
data class PlayerExtras(val chapters: List<Chapter> = emptyList(), val storyboard: Storyboard? = null)
