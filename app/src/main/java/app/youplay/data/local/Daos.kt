package app.youplay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observe(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE videoId = :id")
    suspend fun get(id: String): WatchHistoryEntity?

    @Query("UPDATE watch_history SET progressMs = :ms WHERE videoId = :id")
    suspend fun setProgress(id: String, ms: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    @Query("SELECT * FROM watch_history")
    suspend fun all(): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE videoId = :id")
    suspend fun delete(id: String)
}

@Dao
interface LikedVideoDao {
    @Query("SELECT * FROM liked_videos ORDER BY likedAt DESC")
    fun observe(): Flow<List<LikedVideoEntity>>

    @Query("SELECT * FROM liked_videos")
    suspend fun all(): List<LikedVideoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_videos WHERE videoId = :id)")
    fun observeLiked(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LikedVideoEntity)

    @Query("DELETE FROM liked_videos WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM liked_videos")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observe(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun all(): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :id)")
    fun observeSubscribed(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observe(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :q")
    suspend fun delete(q: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :id")
    fun observe(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE videoId = :id")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("UPDATE downloads SET progress = :p, sizeBytes = :size WHERE videoId = :id")
    suspend fun setProgress(id: String, p: Int, size: Long)

    @Query("UPDATE downloads SET status = :status WHERE videoId = :id")
    suspend fun setStatus(id: String, status: Int)

    @Query("UPDATE downloads SET status = 3 WHERE status IN (0, 1)")
    suspend fun failStuck()

    @Query("DELETE FROM downloads WHERE videoId = :id")
    suspend fun delete(id: String)
}

@Dao
interface PendingActionDao {
    @Insert
    suspend fun add(a: PendingActionEntity)

    @Query("SELECT * FROM pending_actions ORDER BY id")
    suspend fun all(): List<PendingActionEntity>

    @Query("SELECT COUNT(*) FROM pending_actions")
    fun count(): Flow<Int>

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_actions WHERE type = :type AND targetId = :target")
    suspend fun clearFor(type: String, target: String)

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}

@Dao
interface DislikedVideoDao {
    @Query("SELECT EXISTS(SELECT 1 FROM disliked_videos WHERE videoId = :id)")
    fun observe(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DislikedVideoEntity)

    @Query("DELETE FROM disliked_videos WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM disliked_videos")
    suspend fun clear()
}

/** Watch later kept on the device, so it works signed out and survives offline. */
@Dao
interface SavedVideoDao {
    @Query("SELECT * FROM saved_videos ORDER BY savedAt DESC")
    fun observe(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos")
    suspend fun all(): List<SavedVideoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_videos WHERE videoId = :id)")
    fun observeSaved(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_videos")
    suspend fun clear()
}
