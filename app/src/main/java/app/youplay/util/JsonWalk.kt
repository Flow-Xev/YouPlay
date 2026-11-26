package app.youplay.util

import app.youplay.domain.ChannelItem
import app.youplay.domain.ChipItem
import app.youplay.domain.CommentItem
import app.youplay.domain.CommentsPage
import app.youplay.domain.PlaylistItem
import app.youplay.domain.VideoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Safe JSON walkers for Innertube responses.
 * Never use throwing `.jsonObject` / `.jsonArray` — YouTube often returns a plain string
 * (JsonLiteral) where an object was expected (e.g. channelMetadataRenderer.title).
 */
object JsonWalk {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun parseElement(text: String): JsonElement = json.parseToJsonElement(text)

    fun parseObject(text: String): JsonObject =
        json.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray
    private fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray

    fun walk(element: JsonElement?, visitor: (String, JsonObject) -> Unit) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    if (value is JsonObject) runCatching { visitor(key, value) }
                    walk(value, visitor)
                }
            }
            is JsonArray -> element.forEach { walk(it, visitor) }
            else -> Unit
        }
    }

    /** Accepts runs object, attributed string, or a plain JsonPrimitive title. */
    fun textRuns(el: JsonElement?): String? = when (el) {
        null -> null
        is JsonPrimitive -> el.contentOrNull
        is JsonObject -> {
            el["simpleText"]?.asStr()
                ?: (el["content"] as? JsonPrimitive)?.contentOrNull
                ?: el.arr("runs")?.mapNotNull { it.asObject()?.str("text") }?.joinToString("")?.takeIf { it.isNotEmpty() }
                ?: el.obj("accessibility")?.obj("accessibilityData")?.str("label")
        }
        else -> null
    }

    fun textRuns(obj: JsonObject?): String? = textRuns(obj as JsonElement?)

    fun bestThumb(el: JsonElement?): String? {
        val obj = el.asObject() ?: return null
        val thumbs = obj.arr("thumbnails") ?: obj.obj("image")?.arr("sources") ?: return null
        return thumbs.mapNotNull { it.asObject()?.str("url") }
            .maxByOrNull { it.length }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    fun bestThumb(obj: JsonObject?): String? = bestThumb(obj as JsonElement?)

    fun extractVideos(root: JsonElement): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        walk(root) { key, obj ->
            when (key) {
                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer" -> {
                    parseVideoRenderer(obj)?.let { out.putIfAbsent(it.id, it) }
                }
                "reelItemRenderer", "shortsLockupViewModel" -> {
                    parseShort(obj)?.let { out.putIfAbsent(it.id, it) }
                }
                "lockupViewModel" -> parseLockup(obj)?.let { out.putIfAbsent(it.id, it) }
            }
        }
        return out.values.toList()
    }

    fun extractChannels(root: JsonElement): List<ChannelItem> {
        val out = LinkedHashMap<String, ChannelItem>()
        walk(root) { key, obj ->
            if (key == "channelRenderer" || key == "gridChannelRenderer") {
                parseChannel(obj)?.let { out.putIfAbsent(it.id, it) }
            }
        }
        return out.values.toList()
    }

    fun extractComments(root: JsonElement): List<CommentItem> {
        val out = LinkedHashMap<String, CommentItem>()
        walk(root) { key, obj ->
            if (key == "commentRenderer" || key == "commentEntityPayload" || key == "commentViewModel") {
                parseComment(obj)?.let { out.putIfAbsent(it.id, it) }
            }
        }
        return out.values.toList()
    }

    /** Like count read from YouTube's own InnerTube watch response (not Return YouTube Dislike). */
    fun extractLikeCountText(root: JsonElement): String? {
        var count: String? = null
        walk(root) { key, obj ->
            if (count != null) return@walk
            when (key) {
                "segmentedLikeDislikeButtonRenderer" -> {
                    val like = obj.obj("likeButton")?.obj("toggleButtonRenderer")
                    count = textRuns(like?.get("defaultText"))
                        ?: textRuns(like?.get("accessibility"))
                        ?: like?.obj("accessibility")?.obj("accessibilityData")?.str("label")
                }
                "likeButtonViewModel" -> {
                    val model = obj.obj("likeButtonViewModel") ?: obj
                    val button = model.obj("toggleButtonViewModel")
                        ?.obj("defaultButtonViewModel")?.obj("buttonViewModel")
                    count = textRuns(button?.get("title"))
                        ?: textRuns(button?.get("accessibilityText"))
                }
            }
        }
        return count?.takeIf { it.isNotBlank() }
    }

    /** Total comment count shown by YouTube's comment-section header, when available. */
    fun extractCommentCountText(root: JsonElement): String? {
        var count: String? = null
        walk(root) { key, obj ->
            if (count == null && key == "commentsHeaderRenderer") {
                count = textRuns(obj["countText"])
                    ?: textRuns(obj["commentsCount"] )
                    ?: textRuns(obj["title"])
            }
        }
        return count?.takeIf { it.isNotBlank() }
    }

    fun profile(root: JsonElement): app.youplay.domain.AccountState? {
        var res: app.youplay.domain.AccountState? = null
        walk(root) { key, obj ->
            if (key == "activeAccountHeaderRenderer" && res == null) {
                res = app.youplay.domain.AccountState(
                    signedIn = true,
                    name = textRuns(obj["accountName"]),
                    email = textRuns(obj["email"]),
                    handle = textRuns(obj["channelHandle"]),
                    avatarUrl = bestThumb(obj["accountPhoto"]),
                )
            }
        }
        return res
    }

    fun extractWatchMeta(root: JsonElement): Triple<String?, String?, String?> {
        var desc: String? = null; var views: String? = null; var date: String? = null
        walk(root) { key, obj ->
            when (key) {
                "attributedDescription" -> if (desc == null) desc = obj.str("content")
                "videoSecondaryInfoRenderer" -> if (desc == null) desc = textRuns(obj["description"])
                "videoPrimaryInfoRenderer" -> {
                    views = textRuns(obj.obj("viewCount")?.obj("videoViewCountRenderer")?.get("viewCount")) ?: views
                    date = textRuns(obj["dateText"]) ?: date
                }
                // newer watch layout
                "videoDescriptionHeaderRenderer" -> {
                    if (views == null) views = textRuns(obj["views"])
                    if (date == null) date = textRuns(obj["publishDate"]) ?: textRuns(obj["dateText"])
                }
                // always-present fallback: exact view count + publish date from the microformat
                "playerMicroformatRenderer" -> {
                    if (views == null) obj.str("viewCount")?.toLongOrNull()?.let { views = "%,d views".format(it) }
                    if (date == null) date = obj.str("publishDate") ?: obj.str("uploadDate")
                }
            }
        }
        return Triple(desc, views, date)
    }

    /** Channel avatar + subscriber line from watch page owner renderers. */
    fun extractWatchOwner(root: JsonElement): Pair<String?, String?> {
        var avatar: String? = null
        var subs: String? = null
        walk(root) { key, obj ->
            when (key) {
                "videoOwnerRenderer" -> {
                    if (avatar == null) avatar = bestThumb(obj["thumbnail"] ?: obj["avatar"])
                    if (subs == null) subs = textRuns(obj["subscriberCountText"])
                }
                "videoSecondaryInfoRenderer" -> {
                    val owner = obj.obj("owner")?.obj("videoOwnerRenderer")
                    if (owner != null) {
                        if (avatar == null) avatar = bestThumb(owner["thumbnail"] ?: owner["avatar"])
                        if (subs == null) subs = textRuns(owner["subscriberCountText"])
                    }
                }
            }
        }
        return avatar to subs
    }

    fun parseLockup(obj: JsonObject): VideoItem? {
        val type = obj.str("contentType").orEmpty()
        if (type.isNotEmpty() && !type.endsWith("VIDEO")) return null
        val id = obj.str("contentId") ?: return null
        val meta = obj.obj("metadata")?.obj("lockupMetadataViewModel")
        val title = textRuns(meta?.get("title")) ?: "Video"
        val rows = meta?.obj("metadata")?.obj("contentMetadataViewModel")?.arr("metadataRows")
        val parts = rows?.flatMap { r ->
            r.asObject()?.arr("metadataParts")?.mapNotNull { textRuns(it.asObject()?.get("text")) }.orEmpty()
        }.orEmpty()
        val thumbVm = obj.obj("contentImage")?.obj("thumbnailViewModel")
        var duration: String? = null
        thumbVm?.arr("overlays")?.forEach { ov ->
            ov.asObject()?.obj("thumbnailBottomOverlayViewModel")?.arr("badges")?.forEach { b ->
                val badge = b.asObject()?.obj("thumbnailBadgeViewModel")
                val t = textRuns(badge?.get("text"))
                if (duration == null && t != null && t.any { it.isDigit() }) duration = t
            }
        }
        val channelId = meta?.obj("image")?.obj("decoratedAvatarViewModel")?.obj("rendererContext")
            ?.obj("commandContext")?.obj("onTap")?.obj("innertubeCommand")?.obj("browseEndpoint")?.str("browseId")
        return VideoItem(
            id = id, title = title,
            channelName = parts.getOrNull(0).orEmpty(),
            channelId = channelId,
            viewCountText = parts.getOrNull(1),
            publishedText = parts.getOrNull(2),
            thumbnailUrl = bestThumb(thumbVm?.get("image")),
            durationText = duration,
            channelAvatarUrl = findObj(meta?.obj("image"), "avatarViewModel")?.let { bestThumb(it) },
        )
    }

    fun extractPlaylists(root: JsonElement): List<PlaylistItem> {
        val out = LinkedHashMap<String, PlaylistItem>()
        walk(root) { key, obj ->
            when (key) {
                "lockupViewModel" -> if (obj.str("contentType")?.endsWith("PLAYLIST") == true) {
                    val id = obj.str("contentId") ?: return@walk
                    val meta = obj.obj("metadata")?.obj("lockupMetadataViewModel")
                    out.putIfAbsent(
                        id,
                        PlaylistItem(
                            id = id,
                            title = textRuns(meta?.get("title")) ?: "Playlist",
                            thumbnailUrl = bestThumb(
                                obj.obj("contentImage")?.let { it.obj("collectionThumbnailViewModel") ?: it }
                                    ?.let { it.obj("primaryThumbnail") ?: it }?.obj("thumbnailViewModel")?.get("image"),
                            ),
                            subtitle = meta?.obj("metadata")?.obj("contentMetadataViewModel")
                                ?.arr("metadataRows")?.firstOrNull()?.asObject()
                                ?.arr("metadataParts")?.firstOrNull()?.asObject()?.get("text")?.let { textRuns(it) },
                        ),
                    )
                }
                "playlistRenderer", "gridPlaylistRenderer" -> {
                    val id = obj.str("playlistId") ?: return@walk
                    out.putIfAbsent(
                        id,
                        PlaylistItem(
                            id,
                            textRuns(obj["title"]) ?: "Playlist",
                            bestThumb(obj.arr("thumbnails")?.firstOrNull()) ?: bestThumb(obj["thumbnail"]),
                            obj.str("videoCount")?.let { "$it videos" },
                        ),
                    )
                }
            }
        }
        return out.values.toList()
    }

    fun extractShelves(root: JsonElement): List<app.youplay.domain.ChannelShelf> {
        val out = ArrayList<app.youplay.domain.ChannelShelf>()
        walk(root) { key, obj ->
            if (key == "channelVideoPlayerRenderer") {
                val id = obj.str("videoId") ?: return@walk
                out += app.youplay.domain.ChannelShelf(
                    "Featured",
                    listOf(
                        VideoItem(
                            id = id, title = textRuns(obj["title"]) ?: "Video",
                            viewCountText = textRuns(obj["viewCountText"]),
                            publishedText = textRuns(obj["publishedTimeText"]),
                            description = textRuns(obj["description"]),
                        ),
                    ),
                    featured = true,
                )
            } else if (key == "itemSectionRenderer") {
                val shelf = findObj(obj, "shelfRenderer") ?: findObj(obj, "richShelfRenderer") ?: findObj(obj, "reelShelfRenderer")
                    ?: return@walk
                val isShorts = shelf.toString().contains("shortsLockupViewModel") || shelf.toString().contains("reelItemRenderer")
                val title = textRuns(shelf["title"]) ?: if (isShorts) "Shorts" else return@walk
                val vl = findStr(shelf["title"], "browseId") ?: findStr(shelf["endpoint"], "browseId")
                val playlists = extractPlaylists(shelf)
                val videos = extractVideos(shelf).let { l -> if (isShorts) l.map { it.copy(isShort = true) } else l }
                if (videos.isEmpty() && playlists.isEmpty()) return@walk
                out += app.youplay.domain.ChannelShelf(
                    title = title, videos = videos, playlists = playlists, isShorts = isShorts,
                    playlistId = vl?.takeIf { it.startsWith("VL") }?.drop(2),
                )
            }
        }
        return out
    }

    fun extractChips(root: JsonElement): List<ChipItem> {
        val out = ArrayList<ChipItem>()
        walk(root) { key, obj ->
            if (key == "chipCloudChipRenderer") {
                val label = textRuns(obj["text"]) ?: return@walk
                val token = obj.obj("navigationEndpoint")?.obj("continuationCommand")?.str("token")
                    ?: obj.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("params")
                out += ChipItem(label, token, obj["isSelected"].asStr() == "true")
            }
        }
        return out
    }

    fun extractContinuation(root: JsonElement): String? {
        var token: String? = null
        walk(root) { key, obj ->
            if (key == "continuationItemRenderer") {
                obj.obj("continuationEndpoint")?.obj("continuationCommand")?.str("token")?.let { token = it }
            }
        }
        return token
    }

    fun extractShortsShelves(root: JsonElement): List<List<VideoItem>> {
        val shelves = ArrayList<List<VideoItem>>()
        walk(root) { key, obj ->
            if (key == "reelShelfRenderer" || (key == "richShelfRenderer" && obj.toString().contains("shortsLockupViewModel"))) {
                val list = extractVideos(obj).map { it.copy(isShort = true) }
                if (list.size >= 3) shelves += list
            }
        }
        return shelves
    }

    private fun parseVideoRenderer(obj: JsonObject): VideoItem? {
        val id = obj.str("videoId") ?: return null
        val title = textRuns(obj["title"]) ?: return null
        val channel = textRuns(obj["ownerText"] ?: obj["shortBylineText"] ?: obj["longBylineText"]).orEmpty()
        val channelId = obj.obj("ownerText")?.arr("runs")?.firstOrNull()?.asObject()
            ?.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
            ?: obj.str("channelId")
        val length = textRuns(obj["lengthText"])
            ?: obj.arr("thumbnailOverlays")?.firstOrNull()?.asObject()
                ?.obj("thumbnailOverlayTimeStatusRenderer")?.get("text")?.let { textRuns(it) }
        return VideoItem(
            id = id,
            title = title,
            channelName = channel,
            channelId = channelId,
            thumbnailUrl = bestThumb(obj["thumbnail"]),
            durationText = length,
            viewCountText = textRuns(obj["viewCountText"] ?: obj["shortViewCountText"]),
            publishedText = textRuns(obj["publishedTimeText"]),
            description = textRuns(obj["descriptionSnippet"]),
            isShort = obj.toString().let {
                it.contains("\"reelWatchEndpoint\"") || it.contains("\"/shorts/") ||
                    it.contains("\"style\":\"SHORTS\"")
            },
            channelAvatarUrl = bestThumb(findObj(obj, "channelThumbnailWithLinkRenderer")?.get("thumbnail"))
                ?: bestThumb(obj["channelThumbnail"]),
        )
    }

    private fun parseShort(obj: JsonObject): VideoItem? {
        val id = obj.str("videoId")
            ?: obj.obj("onTap")?.obj("innertubeCommand")?.obj("reelWatchEndpoint")?.str("videoId")
            ?: obj.str("entityId")?.substringAfter("shorts-shelf-item-")
            ?: return null
        val title = textRuns(obj["headline"])
            ?: textRuns(obj.obj("overlayMetadata")?.get("primaryText"))
            ?: obj.str("accessibilityText")
            ?: "Short"
        return VideoItem(
            id = id,
            title = title,
            thumbnailUrl = bestThumb(obj["thumbnail"])
                ?: bestThumb(obj.obj("thumbnailViewModel")?.get("image"))
                ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
            viewCountText = textRuns(obj.obj("overlayMetadata")?.get("secondaryText")),
            isShort = true,
        )
    }

    private fun parseChannel(obj: JsonObject): ChannelItem? {
        val id = obj.str("channelId") ?: return null
        val title = textRuns(obj["title"]) ?: return null
        return ChannelItem(
            id = id,
            title = title,
            thumbnailUrl = bestThumb(obj["thumbnail"]),
            subscriberCountText = textRuns(obj["subscriberCountText"]),
            videoCountText = textRuns(obj["videoCountText"]),
            description = textRuns(obj["descriptionSnippet"]),
        )
    }

    private fun parseComment(obj: JsonObject): CommentItem? {
        val id = obj.str("commentId")
            ?: obj.obj("properties")?.str("commentId")
            ?: return null
        val author = textRuns(obj["authorText"])
            ?: obj.obj("author")?.str("displayName")
            ?: "User"
        val text = textRuns(obj["contentText"])
            ?: textRuns(obj.obj("properties")?.get("content"))
            ?: obj.str("content")
            ?: return null
        val author2 = obj.obj("author")
        val toolbar = obj.obj("toolbar")
        return CommentItem(
            id = id,
            author = author,
            authorThumbUrl = bestThumb(obj["authorThumbnail"])
                ?: author2?.str("avatarThumbnailUrl"),
            text = text,
            likeCountText = textRuns(obj["voteCount"])
                ?: obj.str("likeCountNotliked")
                ?: toolbar?.str("likeCountNotliked"),
            publishedText = textRuns(obj["publishedTimeText"])
                ?: obj.obj("properties")?.str("publishedTime"),
            replyCount = obj.str("replyCount")?.toIntOrNull()
                ?: toolbar?.str("replyCount")?.toIntOrNull() ?: 0,
        )
    }

    private fun findObj(el: JsonElement?, key: String): JsonObject? {
        when (el) {
            is JsonObject -> {
                (el[key] as? JsonObject)?.let { return it }
                for ((_, v) in el) findObj(v, key)?.let { return it }
            }
            is JsonArray -> for (x in el) findObj(x, key)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun findStr(el: JsonElement?, key: String): String? {
        when (el) {
            is JsonObject -> {
                (el[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
                for ((_, v) in el) findStr(v, key)?.let { return it }
            }
            is JsonArray -> for (x in el) findStr(x, key)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun actionOf(el: JsonElement?): String? = findObj(el, "performCommentActionEndpoint")?.let { findStr(it, "action") }

    fun commentsToken(root: JsonElement): String? {
        var t: String? = null
        walk(root) { key, obj ->
            if (t == null && key == "itemSectionRenderer") {
                val id = obj.str("sectionIdentifier") ?: obj.str("targetId")
                if (id != null && id.contains("comment", ignoreCase = true)) t = findStr(obj, "token")
            }
        }
        return t
    }

    fun parseCommentsPage(root: JsonElement): CommentsPage {
        val byId = LinkedHashMap<String, CommentItem>()
        walk(root) { key, obj ->
            if (key == "commentEntityPayload" || key == "commentRenderer") parseComment(obj)?.let { byId.putIfAbsent(it.id, it) }
        }
        val ordered = ArrayList<CommentItem>()
        walk(root) { key, obj ->
            if (key == "commentThreadRenderer") {
                val id = findStr(obj, "commentId")
                val base = id?.let { byId[it] }
                if (base != null && ordered.none { it.id == base.id }) {
                    val like = findObj(obj, "likeButton")
                    ordered += base.copy(
                        deleteToken = actionOf(findObj(obj, "confirmDialogRenderer")),
                        likeToken = like?.let { findObj(it, "defaultServiceEndpoint") }?.let(::actionOf),
                        unlikeToken = like?.let { findObj(it, "toggledServiceEndpoint") }?.let(::actionOf),
                        replyParams = findStr(obj, "createReplyParams"),
                    )
                }
            }
        }
        var cont: String? = null
        fun scan(el: JsonElement?) {
            when (el) {
                is JsonObject -> {
                    (el["continuationItems"] as? JsonArray)?.lastOrNull()?.let { last ->
                        findStr((last as? JsonObject)?.get("continuationItemRenderer"), "token")?.let { cont = it }
                    }
                    el.values.forEach(::scan)
                }
                is JsonArray -> el.forEach(::scan)
                else -> Unit
            }
        }
        scan(root)
        return CommentsPage(
            comments = ordered.ifEmpty { byId.values.toList() },
            continuation = cont,
            createParams = findStr(root, "createCommentParams"),
        )
    }
}
