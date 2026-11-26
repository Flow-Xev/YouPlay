package app.youplay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        LikedVideoEntity::class,
        SubscriptionEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        PendingActionEntity::class,
        DislikedVideoEntity::class,
        SavedVideoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class YouPlayDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun likedVideoDao(): LikedVideoDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun dislikedVideoDao(): DislikedVideoDao
    abstract fun savedVideoDao(): SavedVideoDao
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`channelName` TEXT NOT NULL, `thumbnailUrl` TEXT, `videoPath` TEXT, `audioPath` TEXT, " +
                "`sizeBytes` INTEGER NOT NULL, `status` INTEGER NOT NULL, `progress` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
        )
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `targetId` TEXT NOT NULL, `value` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `disliked_videos` (`videoId` TEXT NOT NULL, `dislikedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
        )
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_videos` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`channelName` TEXT NOT NULL, `thumbnailUrl` TEXT, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
        )
    }
}
