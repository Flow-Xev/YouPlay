package app.youplay.data.remote

import app.youplay.domain.StreamInfo
import java.net.URL

/**
 * Last line of defence against `java.net.MalformedURLException` ("Source error [IO_NETWORK_CONNECTION_FAILED]").
 *
 * Every stream URL — from any resolver (InnerTubeX extractor, direct player clients, NewPipe) — is cleaned and
 * validated here *before* it reaches ExoPlayer. A resolver that returns an unusable URL is treated as a failed
 * resolver, so the next one in the chain (or a fresh retry) is used instead of handing the player a bad address.
 */
object UrlGuard {
    private val googleHosts = listOf("googlevideo.com", "youtube.com", "ytimg.com", "ggpht.com", "googleusercontent.com")

    /** Returns a clean, absolute http(s)/file URL, or null when [raw] cannot be repaired. */
    fun clean(raw: String?): String? {
        var s = raw?.trim() ?: return null
        s = s.trim('"', '\'', '<', '>').trim()
        if (s.isEmpty() || s.equals("null", ignoreCase = true)) return null
        if (s.startsWith("file:", true) || s.startsWith("content:", true)) return s // offline downloads

        // JSON / HTML escapes that sometimes survive extraction
        s = s.replace("\\u0026", "&").replace("\\/", "/").replace("&amp;", "&")
        s = s.filterNot { it.isISOControl() }.replace(" ", "%20")

        if (s.startsWith("//")) s = "https:$s"
        // cleartext is blocked by the network security config; googlevideo & friends always speak https
        if (s.startsWith("http://", true)) {
            val host = hostOf(s)
            if (host != null && googleHosts.any { host == it || host.endsWith(".$it") }) s = "https://" + s.substring(7)
        }
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) return null

        return try {
            val u = URL(s) // the exact check DefaultHttpDataSource performs
            if (u.host.isNullOrBlank() || !u.host.contains('.')) null else s
        } catch (e: Exception) {
            null
        }
    }

    private fun hostOf(s: String): String? =
        runCatching { URL(s).host?.lowercase() }.getOrNull()

    /** Cleaned copy of [info], or null if it no longer has any playable URL. */
    fun sanitize(info: StreamInfo): StreamInfo? {
        val v = clean(info.videoUrl)
        val a = clean(info.audioUrl)
        val h = clean(info.hlsUrl)
        if (v == null && a == null && h == null) return null
        return info.copy(videoUrl = v, audioUrl = a, hlsUrl = h)
    }

    /** Like [sanitize] but throws, so a hedged / retried resolver chain moves on to the next resolver. */
    fun require(info: StreamInfo, source: String): StreamInfo =
        sanitize(info) ?: throw IllegalStateException("$source returned an unusable (malformed) stream URL for ${info.videoId}")
}
