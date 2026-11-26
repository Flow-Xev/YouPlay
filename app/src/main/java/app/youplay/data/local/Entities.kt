package app.youplay.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val watchedAt: Long,
    val progressMs: Long = 0,
)

@Entity(tableName = "liked_videos")
data class LikedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val likedAt: Long,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val subscribedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

const val DOWNLOAD_QUEUED = 0
const val DOWNLOAD_RUNNING = 1
const val DOWNLOAD_DONE = 2
const val DOWNLOAD_FAILED = 3

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val videoPath: String?,
    val audioPath: String?,
    val sizeBytes: Long,
    val status: Int,
    val progress: Int,
    val createdAt: Long,
)

/** A like / dislike / subscribe / watch-later change waiting to be sent to the YouTube account. */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val targetId: String,
    val value: String,
    val createdAt: Long,
)

@Entity(tableName = "disliked_videos")
data class DislikedVideoEntity(
    @PrimaryKey val videoId: String,
    val dislikedAt: Long,
)

@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val savedAt: Long,
)
