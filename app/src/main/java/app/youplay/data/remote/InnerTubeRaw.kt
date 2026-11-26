package app.youplay.data.remote

import app.youplay.domain.StreamInfo
import app.youplay.util.JsonWalk
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A complete, client-parameterised InnerTube caller (browse / search / next / player) that does not depend on the
 * client list baked into the innertubex library. Every call states which [ClientProfile] it speaks as, so we can
 * (a) use the client that is best for the job and (b) retry with the next client of the chain when one fails.
 * Anonymous by design: signed-in WEB calls still go through the innertubex session; this layer is the fallback.
 */
@Singleton
class InnerTubeRaw @Inject constructor(private val http: HttpClient) {

    @Volatile var hl: String = "en"
    @Volatile var gl: String = "US"
    @Volatile private var visitor: String? = null
    @Volatile private var visitorAt = 0L

    private val base = "https://www.youtube.com/youtubei/v1"
    private val webKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    fun context(p: ClientProfile): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", p.clientName)
            put("clientVersion", p.clientVersion)
            put("hl", hl)
            put("gl", gl)
            put("utcOffsetMinutes", 0)
            if (p.contextUserAgent) put("userAgent", p.userAgent)
            p.deviceMake?.let { put("deviceMake", it) }
            p.deviceModel?.let { put("deviceModel", it) }
            p.osName?.let { put("osName", it) }
            p.osVersion?.let { put("osVersion", it) }
            p.androidSdkVersion?.let { put("androidSdkVersion", it) }
            p.platform?.let { put("platform", it) }
            p.clientScreen?.let { put("clientScreen", it) }
            visitor?.let { put("visitorData", it) }
        }
        putJsonObject("user") { put("lockedSafetyMode", false) }
        putJsonObject("request") { put("useSsl", true) }
        if (p.embed) putJsonObject("thirdParty") { put("embedUrl", "https://www.youtube.com/") }
    }

    /** POST `youtubei/v1/[endpoint]` as [p]; [extra] adds endpoint-specific fields. Returns the raw JSON text. */
    suspend fun call(endpoint: String, p: ClientProfile, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String {
        ensureVisitor(p)
        val body = buildJsonObject {
            put("context", context(p))
            extra()
        }.toString()
        val resp = withTimeout(15_000L) {
            http.post("$base/$endpoint?prettyPrint=false&key=$webKey") {
                header("User-Agent", p.userAgent)
                header("X-YouTube-Client-Name", p.clientNameId.toString())
                header("X-YouTube-Client-Version", p.clientVersion)
                if (p.web) { header("Origin", "https://www.youtube.com"); header("Referer", "https://www.youtube.com/") }
                visitor?.let { header("X-Goog-Visitor-Id", it) }
                setBody(TextContent(body, ContentType.Application.Json))
            }
        }
        val text = resp.bodyAsText()
        if (resp.status.value >= 400) throw IllegalStateException("InnerTube $endpoint as ${p.name}: HTTP ${resp.status.value}")
        if (text.isBlank() || text.trimStart().startsWith("{\"error\"")) {
            throw IllegalStateException("InnerTube $endpoint as ${p.name}: error response")
        }
        return text
    }

    suspend fun browse(p: ClientProfile, browseId: String?, params: String?, continuation: String?): String =
        call("browse", p) {
            browseId?.let { put("browseId", it) }
            params?.let { put("params", it) }
            continuation?.let { put("continuation", it) }
        }

    suspend fun search(p: ClientProfile, query: String?, params: String?, continuation: String?): String =
        call("search", p) {
            query?.let { put("query", it) }
            params?.let { put("params", it) }
            continuation?.let { put("continuation", it) }
        }

    suspend fun next(p: ClientProfile, videoId: String?, continuation: String?): String =
        call("next", p) {
            videoId?.let { put("videoId", it) }
            continuation?.let { put("continuation", it) }
        }

    /** Resolve a playable stream with one specific client. Throws when that client has nothing usable. */
    suspend fun player(videoId: String, p: ClientProfile, maxHeight: Int): StreamInfo {
        val text = call("player", p) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") { put("html5Preference", "HTML5_PREF_WANTS") }
            }
        }
        val root = JsonWalk.parseObject(text)
        val status = root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        if (status != "OK") {
            val reason = root["playabilityStatus"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
            throw IllegalStateException("${p.name}: ${reason ?: status ?: "no playability status"}")
        }
        val sd = root["streamingData"]?.jsonObject ?: throw IllegalStateException("${p.name}: no streamingData")
        val details = root["videoDetails"]?.jsonObject

        class Fmt(val url: String, val mime: String, val height: Int, val bitrate: Int)
        fun formats(key: String): List<Fmt> = (sd[key] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = UrlGuard.clean(o["url"]?.jsonPrimitive?.contentOrNull) ?: return@mapNotNull null // cipher-only: skip
            Fmt(
                url,
                o["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["height"]?.jsonPrimitive?.intOrNull ?: 0,
                o["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        val muxed = formats("formats")
        val adaptive = formats("adaptiveFormats")

        val progressive = muxed.filter { it.height <= maxHeight }.maxByOrNull { it.height } ?: muxed.minByOrNull { it.height }
        val video = adaptive.filter { it.mime.startsWith("video/") && it.height in 1..maxHeight }
            .let { all -> all.filter { it.mime.startsWith("video/mp4") }.ifEmpty { all } }
            .maxByOrNull { it.height }
        val audio = adaptive.filter { it.mime.startsWith("audio/") }
            .let { all -> all.filter { it.mime.startsWith("audio/mp4") }.ifEmpty { all } }
            .maxByOrNull { it.bitrate }
        val hls = UrlGuard.clean(sd["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull)

        // adaptive pair is sharper; the muxed progressive stream is the safe single-URL fallback
        val (videoUrl, audioUrl, mime) = when {
            video != null && audio != null && video.height > (progressive?.height ?: 0) -> Triple(video.url, audio.url, video.mime)
            progressive != null -> Triple(progressive.url, progressive.url, progressive.mime)
            video != null && audio != null -> Triple(video.url, audio.url, video.mime)
            else -> Triple(null, audio?.url, null)
        }
        val thumbs = details?.get("thumbnail")?.jsonObject?.get("thumbnails") as? JsonArray
        val info = StreamInfo(
            videoId = videoId,
            title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: "Video",
            channelName = details?.get("author")?.jsonPrimitive?.contentOrNull.orEmpty(),
            channelId = details?.get("channelId")?.jsonPrimitive?.contentOrNull,
            thumbnailUrl = (thumbs?.lastOrNull() as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull,
            description = null,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            hlsUrl = hls,
            mimeType = mime,
        )
        return UrlGuard.require(info, p.name)
    }

    /** Fetch an anonymous visitorData once per 30 min; many clients return richer / unthrottled data with it. */
    private suspend fun ensureVisitor(p: ClientProfile) {
        val now = System.currentTimeMillis()
        if (visitor != null && now - visitorAt < 30 * 60_000L) return
        if (now - visitorAt < 60_000L) return // do not hammer on failure
        visitorAt = now
        runCatching {
            val resp = withTimeout(8_000L) {
                http.post("$base/browse?prettyPrint=false&key=$webKey") {
                    header("User-Agent", WEB_UA)
                    header("Origin", "https://www.youtube.com")
                    setBody(TextContent(
                        buildJsonObject {
                            putJsonObject("context") {
                                putJsonObject("client") {
                                    put("clientName", "WEB"); put("clientVersion", InnerTubeClients.WEB.clientVersion)
                                    put("hl", hl); put("gl", gl)
                                }
                            }
                            put("browseId", "FEwhat_to_watch")
                        }.toString(),
                        ContentType.Application.Json,
                    ))
                }
            }
            Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"").find(resp.bodyAsText())?.groupValues?.get(1)
        }.getOrNull()?.let { visitor = it }
    }

    private companion object {
        val WEB_UA: String get() = InnerTubeClients.WEB.userAgent
    }
}
