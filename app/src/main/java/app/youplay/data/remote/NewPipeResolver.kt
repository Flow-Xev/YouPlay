package app.youplay.data.remote

import app.youplay.domain.StreamInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Stream resolver built on NewPipeExtractor — the same engine the Flow app (github.com/A-EDev/Flow) uses.
 * NewPipe is actively maintained against YouTube's signature / throttling / PO-token changes, so it is the
 * dependable path when the other resolvers break. Used as a fallback leg of [InnerTubeDataSource.resolveStream].
 */
@Singleton
class NewPipeResolver @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var ready = false

    private fun ensureInit() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(OkDownloader(client))
                ready = true
            }
        }
    }

    suspend fun resolve(videoId: String, maxHeight: Int): StreamInfo = withContext(Dispatchers.IO) {
        ensureInit()
        val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
            ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId",
        )

        val audio = info.audioStreams
            .filter { UrlGuard.clean(it.getContent()) != null }
            .let { all -> all.filter { it.getFormat() == MediaFormat.M4A }.ifEmpty { all } }
            .maxByOrNull { it.averageBitrate }

        // adaptive (video-only) preferred: higher quality; muxed progressive as fallback
        val adaptive = info.videoOnlyStreams
            .filter { UrlGuard.clean(it.getContent()) != null && it.height in 1..maxHeight }
            .let { all -> all.filter { it.getFormat() == MediaFormat.MPEG_4 }.ifEmpty { all } }
            .maxByOrNull { it.height }
        val muxed = info.videoStreams
            .filter { UrlGuard.clean(it.getContent()) != null && !it.isVideoOnly }
            .maxByOrNull { if (it.height <= maxHeight) it.height else -it.height }

        val hls = UrlGuard.clean(info.hlsUrl)

        val videoUrl: String?
        val audioUrl: String?
        val mime: String?
        if (adaptive != null && audio != null) {
            videoUrl = adaptive.getContent(); audioUrl = audio.getContent(); mime = adaptive.getFormat()?.mimeType
        } else if (muxed != null) {
            videoUrl = muxed.getContent(); audioUrl = muxed.getContent(); mime = muxed.getFormat()?.mimeType
        } else {
            videoUrl = null; audioUrl = audio?.getContent(); mime = null
        }

        val channelId = info.uploaderUrl?.substringAfter("/channel/", "")?.takeIf { it.isNotBlank() }
        val result = StreamInfo(
            videoId = videoId,
            title = info.name ?: "Video",
            channelName = info.uploaderName.orEmpty(),
            channelId = channelId,
            thumbnailUrl = info.thumbnails.lastOrNull()?.url,
            description = null,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            hlsUrl = hls,
            mimeType = mime,
        )
        UrlGuard.require(result, "NewPipe")
    }

    private val extrasCache = java.util.concurrent.ConcurrentHashMap<String, app.youplay.domain.PlayerExtras>()

    /** Chapters + seek-preview storyboard (NewPipe's previewFrames / streamSegments). */
    suspend fun extras(videoId: String): app.youplay.domain.PlayerExtras = withContext(Dispatchers.IO) {
        extrasCache[videoId]?.let { return@withContext it }
        ensureInit()
        val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
            ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId",
        )
        val chapters = info.streamSegments.map { app.youplay.domain.Chapter(it.title.orEmpty(), it.startTimeSeconds * 1000L) }
        val fs = info.previewFrames.filter { it.frameWidth >= 160 }.minByOrNull { it.frameWidth } ?: info.previewFrames.lastOrNull()
        val sb = fs?.let {
            app.youplay.domain.Storyboard(
                it.urls.toList(), it.frameWidth, it.frameHeight, it.framesPerPageX, it.framesPerPageY,
                it.totalCount, maxOf(1, it.durationPerFrame),
            )
        }
        app.youplay.domain.PlayerExtras(chapters, sb).also { extrasCache[videoId] = it }
    }

    private class OkDownloader(private val client: OkHttpClient) : Downloader() {
        @Throws(java.io.IOException::class, ReCaptchaException::class)
        override fun execute(request: Request): Response {
            val url = request.url()
            val builder = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT)
            for ((key, values) in request.headers()) {
                builder.removeHeader(key)
                for (v in values) builder.addHeader(key, v)
            }
            if (request.httpMethod() == "POST") {
                builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody(null))
            } else {
                builder.get()
            }
            return client.newCall(builder.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
                Response(r.code, r.message, r.headers.toMultimap(), r.body?.string().orEmpty(), r.request.url.toString())
            }
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
