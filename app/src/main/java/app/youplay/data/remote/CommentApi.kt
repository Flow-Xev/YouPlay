package app.youplay.data.remote

import app.youplay.data.local.SessionStore
import app.youplay.domain.CommentsPage
import app.youplay.util.JsonWalk
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Comment writes (post, reply, like, delete) sent straight to YouTube's internal API with the
 * signed-in session. InnerTubeX has no comment endpoints, so the request is signed here.
 */
@Singleton
class CommentApi @Inject constructor(
    private val http: HttpClient,
    private val store: SessionStore,
) {
    private val origin = "https://www.youtube.com"

    private fun sapisidOf(cookie: String): String? {
        val m = cookie.split(';').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
        }.toMap()
        return m["SAPISID"] ?: m["__Secure-3PAPISID"] ?: m["__Secure-1PAPISID"]
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun call(endpoint: String, extra: (kotlinx.serialization.json.JsonObjectBuilder) -> Unit): JsonObject =
        withContext(Dispatchers.IO) {
            val raw = store.raw() ?: throw IOException("Sign in to do this")
            val sapisid = sapisidOf(raw.cookie) ?: throw IOException("Session expired, sign in again")
            val ts = System.currentTimeMillis() / 1000
            val web = YouTubeClient.WEB
            val body = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", web.clientName)
                        put("clientVersion", web.clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                        raw.visitorData?.let { put("visitorData", it) }
                    })
                    put("user", buildJsonObject { raw.dataSyncId?.let { put("onBehalfOfUser", it) } })
                })
                extra(this)
            }
            val resp = http.post("$origin/youtubei/v1/$endpoint?prettyPrint=false") {
                header(HttpHeaders.Authorization, "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $origin")}")
                header(HttpHeaders.Cookie, raw.cookie)
                header(HttpHeaders.Origin, origin)
                header("X-Origin", origin)
                header(HttpHeaders.Referrer, "$origin/")
                header("X-Goog-AuthUser", "0")
                header("X-YouTube-Client-Name", web.clientId)
                header("X-YouTube-Client-Version", web.clientVersion)
                raw.visitorData?.let { header("X-Goog-Visitor-Id", it) }
                header(HttpHeaders.UserAgent, web.userAgent)
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            val text = resp.bodyAsText()
            val root = runCatching { JsonWalk.parseObject(text) }.getOrNull()
            val err = (root?.get("error") as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
            if (!resp.status.isSuccess() || err != null) throw IOException(err ?: "YouTube returned HTTP ${resp.status.value}")
            root ?: JsonObject(emptyMap())
        }

    /** Posts a top-level comment; returns the new comment when YouTube echoes it back. */
    suspend fun post(createParams: String, text: String): CommentsPage {
        val r = call("comment/create_comment") { it.put("commentText", text); it.put("createCommentParams", createParams) }
        return JsonWalk.parseCommentsPage(r)
    }

    suspend fun reply(replyParams: String, text: String): CommentsPage {
        val r = call("comment/create_comment_reply") { it.put("commentText", text); it.put("createReplyParams", replyParams) }
        return JsonWalk.parseCommentsPage(r)
    }

    /** Delete, like and unlike all go through perform_comment_action with the comment's own token. */
    suspend fun action(token: String) {
        call("comment/perform_comment_action") { it.put("actions", JsonArray(listOf(JsonPrimitive(token)))) }
    }
}
