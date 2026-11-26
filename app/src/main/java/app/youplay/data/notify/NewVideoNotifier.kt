package app.youplay.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.youplay.MainActivity
import app.youplay.R
import app.youplay.data.local.SettingsDataStore
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.FeedEntry
import app.youplay.domain.VideoItem
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Checks the subscriptions feed for uploads we haven't seen yet and posts YouTube-style notifications for them.
 * The first run only records what is already there (so installing the app doesn't fire a burst of old videos).
 */
@Singleton
class NewVideoNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VideoRepository,
    private val settings: SettingsDataStore,
) {
    private val prefs = context.getSharedPreferences("yp_new_video_notifications", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private fun videosOf(entries: List<FeedEntry>): List<VideoItem> =
        entries.filterIsInstance<FeedEntry.Video>().map { it.item }.filter { !it.isShort }

    private fun loadSeen(): LinkedHashSet<String> =
        LinkedHashSet((prefs.getString(KEY_SEEN, "") ?: "").split('\n').filter { it.isNotBlank() })

    private fun saveSeen(seen: LinkedHashSet<String>) {
        val trimmed = if (seen.size > MAX_SEEN) seen.toList().takeLast(MAX_SEEN) else seen.toList()
        prefs.edit().putString(KEY_SEEN, trimmed.joinToString("\n")).putBoolean(KEY_SEEDED, true).apply()
    }

    /** Mark what the user is looking at in the app as seen so it is never notified about afterwards. */
    suspend fun markSeen(entries: List<FeedEntry>) = lock.withLock {
        val seen = loadSeen()
        videosOf(entries).forEach { seen.add(it.id) }
        saveSeen(seen)
    }

    /** One background check. Returns how many notifications were posted. */
    suspend fun check(): Int = lock.withLock {
        if (!settings.settings.first().notifyNewVideos) return 0
        if (!repository.isSignedIn()) return 0
        val page = repository.feed("FEsubscriptions", fresh = true)
        val videos = videosOf(page.entries)
        val seen = loadSeen()
        val seeded = prefs.getBoolean(KEY_SEEDED, false)
        val fresh = videos.filter { it.id !in seen }
        videos.forEach { seen.add(it.id) }
        saveSeen(seen)
        if (!seeded || fresh.isEmpty()) return 0
        // Only things that are actually new: a feed reshuffle can resurface old uploads.
        val recent = fresh.filter { isRecent(it.publishedText) }.take(MAX_NOTIFICATIONS)
        if (recent.isEmpty() || !canNotify()) return 0
        ensureChannel(context)
        recent.forEach { post(it) }
        if (recent.size > 1) postSummary(recent)
        recent.size
    }

    private fun isRecent(published: String?): Boolean {
        val t = published?.lowercase() ?: return true
        return !(t.contains("week") || t.contains("month") || t.contains("year") || t.contains("day") && !t.startsWith("1 day"))
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private suspend fun thumbnail(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(640, 360).build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    private fun openIntent(videoId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_VIDEO
            putExtra(EXTRA_VIDEO_ID, videoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, videoId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun post(v: VideoItem) {
        val pic = thumbnail(v.thumbnailUrl)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(v.channelName.ifBlank { "New video" })
            .setContentText(v.title)
            .setSubText(v.publishedText)
            .setContentIntent(openIntent(v.id))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (pic != null) setStyle(NotificationCompat.BigPictureStyle().bigPicture(pic).setBigContentTitle(v.title))
                else setStyle(NotificationCompat.BigTextStyle().bigText(v.title))
            }
            .build()
        NotificationManagerCompat.from(context).notify(v.id.hashCode(), n)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun postSummary(items: List<VideoItem>) {
        val style = NotificationCompat.InboxStyle()
        items.take(5).forEach { style.addLine("${it.channelName}: ${it.title}") }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${items.size} new videos")
            .setContentText("From your subscriptions")
            .setStyle(style)
            .setGroup(GROUP).setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(items.first().id))
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "new_videos"
        const val ACTION_OPEN_VIDEO = "app.youplay.OPEN_VIDEO"
        const val EXTRA_VIDEO_ID = "open_video_id"
        private const val GROUP = "app.youplay.NEW_VIDEOS"
        private const val SUMMARY_ID = 0x5EED
        private const val KEY_SEEN = "seen"
        private const val KEY_SEEDED = "seeded"
        private const val MAX_SEEN = 400
        private const val MAX_NOTIFICATIONS = 6

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "New videos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Uploads from channels you subscribe to"
                    },
                )
            }
        }
    }
}
