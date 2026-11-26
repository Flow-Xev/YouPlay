package app.youplay.data.download

import android.content.Context
import android.net.Uri
import app.youplay.data.local.DOWNLOAD_DONE
import app.youplay.data.local.DOWNLOAD_FAILED
import app.youplay.data.local.DOWNLOAD_RUNNING
import app.youplay.data.local.DownloadDao
import app.youplay.data.local.DownloadEntity
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Downloads video (+ audio) streams into app storage and serves them back for offline playback. */
@Singleton
class OfflineDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val repository: VideoRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

    val all: Flow<List<DownloadEntity>> = dao.observeAll()
    fun observe(videoId: String): Flow<DownloadEntity?> = dao.observe(videoId)

    init { scope.launch { dao.failStuck() } }

    private fun isMetered(): Boolean =
        (context.getSystemService(android.net.ConnectivityManager::class.java))?.isActiveNetworkMetered == true

    private val dir: File
        get() = (context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")).also { it.mkdirs() }

    fun start(videoId: String, title: String, channel: String, thumb: String?, height: Int? = null) {
        if (jobs[videoId]?.isActive == true) return
        jobs[videoId] = scope.launch {
            val now = System.currentTimeMillis()
            val thumbUrl = thumb ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
            val base = DownloadEntity(videoId, title, channel, thumbUrl, null, null, 0, DOWNLOAD_RUNNING, 0, now)
            try {
                val st = repository.settings.first()
                if (st.wifiOnlyDownloads && isMetered()) {
                    dao.upsert(base.copy(status = DOWNLOAD_FAILED)) // "Wi-Fi only" is on; retry from Downloads on Wi-Fi
                    return@launch
                }
                dao.upsert(base)
                val s = repository.stream(videoId, height ?: st.downloadQuality, record = false)
                val vUrl = s.videoUrl?.takeIf { it.isNotBlank() }
                val aUrl = s.audioUrl?.takeIf { it.isNotBlank() }
                val mainUrl = vUrl ?: aUrl ?: throw IOException("No downloadable stream")
                val split = vUrl != null && aUrl != null && vUrl != aUrl
                val mainFile = File(dir, "$videoId.v").also { it.delete() }
                val audioFile = File(dir, "$videoId.a").also { it.delete() }
                var last = -1
                val report: suspend (Int, Long) -> Unit = { p, size ->
                    if (p != last) { last = p; dao.setProgress(videoId, p, size) }
                }
                val size1 = fetch(mainUrl, mainFile) { f, bytes -> report((f * (if (split) 88 else 100)).toInt(), bytes) }
                var size2 = 0L
                if (split) size2 = fetch(aUrl!!, audioFile) { f, bytes -> report(88 + (f * 12).toInt(), size1 + bytes) }
                dao.upsert(
                    base.copy(
                        videoPath = mainFile.absolutePath,
                        audioPath = if (split) audioFile.absolutePath else null,
                        sizeBytes = size1 + size2, status = DOWNLOAD_DONE, progress = 100,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                dao.setStatus(videoId, DOWNLOAD_FAILED)
            } finally {
                jobs.remove(videoId)
            }
        }
    }

    /** Sequential ranged GETs so large files don't trip YouTube's per-request limits. Returns total size. */
    private suspend fun fetch(url: String, dest: File, onProgress: suspend (Float, Long) -> Unit): Long {
        val chunk = 4L * 1024 * 1024
        var total = -1L
        var pos = 0L
        val buf = ByteArray(64 * 1024)
        while (total < 0 || pos < total) {
            currentCoroutineContext().ensureActive()
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", ua)
                c.setRequestProperty("Range", "bytes=$pos-${pos + chunk - 1}")
                val code = c.responseCode
                if (code != 206 && code != 200) throw IOException("HTTP $code")
                if (total < 0) {
                    total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: c.contentLengthLong
                    if (total <= 0) throw IOException("Unknown size")
                }
                val partial = code == 206
                FileOutputStream(dest, partial && pos > 0).use { out ->
                    c.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                            if (!partial) { /* whole file arrives in one response */ }
                            currentCoroutineContext().ensureActive()
                            onProgress((pos.toFloat() / total).coerceIn(0f, 1f), pos)
                        }
                    }
                }
                if (!partial) break
            } finally {
                c.disconnect()
            }
        }
        return dest.length()
    }

    fun delete(videoId: String) {
        jobs.remove(videoId)?.cancel()
        scope.launch {
            File(dir, "$videoId.v").delete()
            File(dir, "$videoId.a").delete()
            dao.delete(videoId)
        }
    }

    /** A StreamInfo that points at the local files, or null if this video isn't (fully) downloaded. */
    suspend fun localStream(videoId: String): StreamInfo? {
        val d = dao.get(videoId) ?: return null
        if (d.status != DOWNLOAD_DONE) return null
        val v = d.videoPath?.let(::File)?.takeIf { it.exists() } ?: return null
        val a = d.audioPath?.let(::File)?.takeIf { it.exists() }
        return StreamInfo(
            videoId = videoId, title = d.title, channelName = d.channelName, channelId = null,
            thumbnailUrl = d.thumbnailUrl, description = null,
            videoUrl = Uri.fromFile(v).toString(), audioUrl = a?.let { Uri.fromFile(it).toString() },
        )
    }
}
