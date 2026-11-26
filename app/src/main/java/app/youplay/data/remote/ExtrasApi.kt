package app.youplay.data.remote

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SkipSegment(val start: Long, val end: Long, val category: String)
data class VoteStats(val likes: Long, val dislikes: Long)

/** Community APIs Flow also uses: SponsorBlock (skip segments) and Return YouTube Dislike (vote counts). */
@Singleton
class ExtrasApi @Inject constructor() {
    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000
            c.setRequestProperty("User-Agent", "youplay/1.0")
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally { c.disconnect() }
    }

    /** SponsorBlock via the privacy-preserving hash-prefix endpoint (the server never sees the video id). */
    suspend fun segments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(4)
            val cats = java.net.URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"intro\",\"outro\",\"interaction\"]", "UTF-8")
            val body = get("https://sponsor.ajay.app/api/skipSegments/$prefix?categories=$cats&actionTypes=%5B%22skip%22%5D")
                ?: return@runCatching emptyList()
            val arr = JSONArray(body)
            val out = mutableListOf<SkipSegment>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("videoID") != videoId) continue
                val segs = o.optJSONArray("segments") ?: continue
                for (j in 0 until segs.length()) {
                    val s = segs.getJSONObject(j)
                    val t = s.getJSONArray("segment")
                    out += SkipSegment((t.getDouble(0) * 1000).toLong(), (t.getDouble(1) * 1000).toLong(), s.optString("category"))
                }
            }
            out.sortedBy { it.start }
        }.getOrDefault(emptyList())
    }

    suspend fun votes(videoId: String): VoteStats? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(get("https://returnyoutubedislikeapi.com/votes?videoId=$videoId") ?: return@runCatching null)
            VoteStats(o.optLong("likes"), o.optLong("dislikes"))
        }.getOrNull()
    }
}
