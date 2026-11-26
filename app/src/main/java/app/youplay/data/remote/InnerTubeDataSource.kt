package app.youplay.data.remote

import app.youplay.domain.ChannelItem
import app.youplay.domain.CommentItem
import app.youplay.domain.SearchFilter
import app.youplay.domain.SearchPage
import app.youplay.domain.SearchType
import app.youplay.domain.WatchExtras
import app.youplay.domain.FeedEntry
import app.youplay.domain.FeedPage
import app.youplay.domain.PlaylistItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import app.youplay.domain.HomeFeed
import app.youplay.domain.HomeSection
import app.youplay.domain.StreamInfo
import app.youplay.domain.VideoItem
import app.youplay.util.JsonWalk
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
import com.metrolist.innertubex.models.response.PlayerResponse
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import app.youplay.domain.ShortsPage
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import io.ktor.client.request.get
import io.ktor.client.request.parameter

@Singleton
class InnerTubeDataSource @Inject constructor(
    private val innerTube: InnerTube,
    private val extractor: InnerTubeExtractor,
    private val newPipe: NewPipeResolver,
    private val raw: InnerTubeRaw,
    private val http: io.ktor.client.HttpClient,
    private val cache: app.youplay.data.cache.CacheManager,
) {
    suspend fun configureLocale(hl: String, gl: String) = withContext(Dispatchers.IO) {
        innerTube.locale = YouTubeLocale(gl = gl, hl = hl)
        raw.hl = hl; raw.gl = gl
    }


    /**
     * Generic feed loader (Home, Subscriptions, History, Liked, ...). Pass either a browseId
     * or a continuation token (chip tap / infinite scroll). Logged-in sessions are used
     * automatically because the session has useLoginForBrowse = true.
     */
    suspend fun feed(browseId: String?, continuation: String? = null, params: String? = null, fresh: Boolean = false): FeedPage =
        withContext(Dispatchers.IO) {
            val ttl = if (continuation == null && !fresh) 5 * 60_000L else 0L
            val text = browseCached(if (continuation == null) "feed:$browseId:$params" else null, ttl) {
                viaClients(
                    Purpose.BROWSE,
                    primary = {
                        innerTube.browse(
                            YouTubeClient.WEB, browseId = browseId, params = params,
                            continuation = continuation, setLogin = true,
                        ).bodyAsText()
                    },
                    fallback = { c -> raw.browse(c, browseId, params, continuation) },
                )
            }
            parseFeed(JsonWalk.parseObject(text), minShorts = if (fresh) 1 else 3)
        }

    fun parseFeed(root: JsonObject, minShorts: Int = 3): FeedPage {
        val entries = ArrayList<FeedEntry>()
        val seen = HashSet<String>()
        val looseShorts = ArrayList<VideoItem>()
        fun addVideo(v: VideoItem) {
            if (!seen.add(v.id)) return
            if (v.isShort) looseShorts += v else entries += FeedEntry.Video(v)
        }
        for (arr in itemArrays(root)) {
            for (el in arr) {
                runCatching {
                    val o = el as? JsonObject ?: return@runCatching
                    val rich = o["richSectionRenderer"]
                    if (rich != null) {
                        val shorts = JsonWalk.extractShortsShelves(rich).firstOrNull()
                        if (shorts != null) {
                            entries += FeedEntry.ShortsRow(shorts.filter { seen.add(it.id) })
                        } else JsonWalk.extractVideos(rich).forEach(::addVideo)
                    } else JsonWalk.extractVideos(o).forEach(::addVideo)
                }
            }
        }
        if (entries.isEmpty()) runCatching { JsonWalk.extractVideos(root) }.getOrDefault(emptyList()).forEach(::addVideo)
        if (looseShorts.size >= minShorts) entries += FeedEntry.ShortsRow(looseShorts.toList())
        return FeedPage(
            entries,
            runCatching { JsonWalk.extractChips(root).distinctBy { it.label } }.getOrDefault(emptyList()),
            runCatching { JsonWalk.extractContinuation(root) }.getOrNull(),
        )
    }

    private fun itemArrays(root: JsonElement): List<JsonArray> {
        val out = ArrayList<JsonArray>()
        fun rec(e: JsonElement, parentKey: String?) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    if (v is JsonArray && (k == "continuationItems" || (k == "contents" && parentKey == "richGridRenderer"))) out += v
                    else rec(v, k)
                }
                is JsonArray -> e.forEach { rec(it, parentKey) }
                else -> Unit
            }
        }
        rec(root, null)
        // fall back to section/list renderers (subscriptions, history, playlists)
        if (out.isEmpty()) {
            fun rec2(e: JsonElement) {
                when (e) {
                    is JsonObject -> e.forEach { (k, v) ->
                        if (v is JsonArray && k == "contents") out += v else rec2(v)
                    }
                    is JsonArray -> e.forEach { rec2(it) }
                    else -> Unit
                }
            }
            rec2(root)
        }
        return out
    }

    suspend fun playlists(browseId: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val text = innerTube.browse(YouTubeClient.WEB, browseId = browseId, setLogin = true).bodyAsText()
        JsonWalk.extractPlaylists(JsonWalk.parseObject(text))
    }

    suspend fun playlistVideos(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val id = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val text = innerTube.browse(YouTubeClient.WEB, browseId = id, setLogin = true).bodyAsText()
        JsonWalk.extractVideos(JsonWalk.parseObject(text))
    }

    suspend fun subscribedChannels(): List<ChannelItem> = withContext(Dispatchers.IO) {
        val text = innerTube.browse(YouTubeClient.WEB, browseId = "FEchannels", setLogin = true).bodyAsText()
        JsonWalk.extractChannels(JsonWalk.parseObject(text))
    }

    suspend fun like(videoId: String, like: Boolean) = withContext(Dispatchers.IO) {
        if (like) innerTube.likeVideo(YouTubeClient.WEB, videoId) else innerTube.unlikeVideo(YouTubeClient.WEB, videoId)
    }

    /** Tells YouTube a video was watched so it lands in the account history + recommendations. */
    suspend fun reportPlayback(url: String, cpn: String) = withContext(Dispatchers.IO) {
        innerTube.registerPlayback(YouTubeClient.WEB, url, cpn)
    }

    suspend fun searchPage(query: String, filter: SearchFilter, continuation: String?): SearchPage =
        withContext(Dispatchers.IO) {
            val q = if (filter.type == SearchType.SHORTS) "$query #shorts" else query
            val sQuery = if (continuation == null) q else null
            val sParams = if (continuation == null) filter.toParams() else null
            val text = viaClients(
                Purpose.SEARCH,
                primary = {
                    innerTube.search(
                        YouTubeClient.WEB, query = sQuery, params = sParams, continuation = continuation, setLogin = true,
                    ).bodyAsText()
                },
                fallback = { c -> raw.search(c, sQuery, sParams, continuation) },
            )
            val root = JsonWalk.parseObject(text)
            val shelves = JsonWalk.extractShortsShelves(root)
            // Shorts are listed as ordinary videos (same row, opens in the Watch player), in result order.
            val videos = JsonWalk.extractVideos(root)
            val channels = JsonWalk.extractChannels(root)
            val playlists = JsonWalk.extractPlaylists(root)
            val cont = JsonWalk.extractContinuation(root)
            if (filter.type == SearchType.SHORTS) {
                val shorts = (videos + shelves.flatten()).filter { it.isShort || isLikelyShort(it) }
                    .distinctBy { it.id }.map { it.copy(isShort = true) }
                SearchPage(emptyList(), emptyList(), emptyList(), shorts, cont)
            } else {
                val shortsAll = (videos.filter { it.isShort } + shelves.flatten()).distinctBy { it.id }.map { it.copy(isShort = true) }
                val shortIds = shortsAll.map { it.id }.toHashSet()
                val entries = ArrayList<FeedEntry>()
                var rowAdded = false
                for (v in videos) {
                    if (v.id in shortIds) {
                        if (!rowAdded && shortsAll.isNotEmpty()) { entries += FeedEntry.ShortsRow(shortsAll); rowAdded = true }
                    } else entries += FeedEntry.Video(v.copy(isShort = false))
                }
                if (!rowAdded && shortsAll.size >= 3) entries.add(minOf(3, entries.size), FeedEntry.ShortsRow(shortsAll))
                SearchPage(entries, channels, playlists, emptyList(), cont)
            }
        }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val t = http.get("https://suggestqueries.google.com/complete/search") {
                parameter("client", "firefox"); parameter("ds", "yt"); parameter("q", q)
            }.bodyAsText()
            val root = JsonWalk.parseElement(t) as? JsonArray ?: return@runCatching emptyList()
            val list = root.getOrNull(1) as? JsonArray ?: return@runCatching emptyList()
            list.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrDefault(emptyList())
    }

    suspend fun channelTab(browseId: String, params: String): Pair<FeedPage, List<PlaylistItem>> =
        withContext(Dispatchers.IO) {
            cachedPage("tab:$browseId:$params", 120_000L) {
                val text = viaClients(
                    Purpose.BROWSE,
                    primary = { innerTube.browse(YouTubeClient.WEB, browseId = browseId, params = params, setLogin = true).bodyAsText() },
                    fallback = { c -> raw.browse(c, browseId, params, null) },
                )
                val root = JsonWalk.parseObject(text)
                parseFeed(root) to JsonWalk.extractPlaylists(root)
            }
        }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = withContext(Dispatchers.IO) {
        innerTube.addToPlaylist(YouTubeClient.WEB, playlistId, videoId)
    }

    suspend fun watchExtras(videoId: String): Pair<WatchExtras, List<CommentItem>> = withContext(Dispatchers.IO) {
        val root = JsonWalk.parseObject(nextText(videoId, null))
        val (d, v, p) = JsonWalk.extractWatchMeta(root)
        val (avatar, subs) = JsonWalk.extractWatchOwner(root)
        val related = runCatching {
            (JsonWalk.extractVideos(root) + JsonWalk.extractShortsShelves(root).flatten().map { it.copy(isShort = true) })
                .filter { it.id != videoId }.distinctBy { it.id }
        }.getOrDefault(emptyList())
        WatchExtras(related, d, v, p, avatar, subs, JsonWalk.extractCommentCountText(root), JsonWalk.extractLikeCountText(root)) to runCatching { JsonWalk.extractComments(root) }.getOrDefault(emptyList())
    }

    suspend fun dislike(videoId: String) = withContext(Dispatchers.IO) { innerTube.dislikeVideo(YouTubeClient.WEB, videoId) }

    suspend fun subscribe(channelId: String, subscribe: Boolean) = withContext(Dispatchers.IO) {
        if (subscribe) innerTube.subscribeChannel(YouTubeClient.WEB, channelId)
        else innerTube.unsubscribeChannel(YouTubeClient.WEB, channelId)
    }

    suspend fun homeFeed(): HomeFeed = withContext(Dispatchers.IO) {
        val text = viaClients(
            Purpose.BROWSE,
            primary = { innerTube.browse(YouTubeClient.WEB, browseId = "FEwhat_to_watch").bodyAsText() },
            fallback = { c -> raw.browse(c, "FEwhat_to_watch", null, null) },
        )
        val root = JsonWalk.parseObject(text)
        val videos = JsonWalk.extractVideos(root)
        HomeFeed(
            sections = listOf(
                HomeSection("Trending for you", videos.take(24)),
                HomeSection("More videos", videos.drop(24).ifEmpty { videos }),
            ).filter { it.videos.isNotEmpty() }.ifEmpty {
                listOf(HomeSection("Home", emptyList()))
            },
        )
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val text = viaClients(
            Purpose.SEARCH,
            primary = { innerTube.search(YouTubeClient.WEB, query = query, setLogin = false).bodyAsText() },
            fallback = { c -> raw.search(c, query, null, null) },
        )
        JsonWalk.extractVideos(JsonWalk.parseObject(text))
    }

    suspend fun searchChannels(query: String): List<ChannelItem> = withContext(Dispatchers.IO) {
        val text = viaClients(
            Purpose.SEARCH,
            primary = { innerTube.search(YouTubeClient.WEB, query = query, setLogin = false).bodyAsText() },
            fallback = { c -> raw.search(c, query, null, null) },
        )
        JsonWalk.extractChannels(JsonWalk.parseObject(text))
    }

    suspend fun shorts(): List<VideoItem> = withContext(Dispatchers.IO) {
        val attempts = listOf("FEshorts", "UC", "FEwhat_to_watch")
        val collected = LinkedHashMap<String, VideoItem>()
        for (browseId in attempts) {
            runCatching {
                val text = innerTube.browse(YouTubeClient.WEB, browseId = browseId).bodyAsText()
                JsonWalk.extractVideos(JsonWalk.parseObject(text))
                    .filter { it.isShort || it.durationText == null || isLikelyShort(it) }
                    .forEach { collected.putIfAbsent(it.id, it.copy(isShort = true)) }
            }
            if (collected.size >= 12) break
        }
        if (collected.isEmpty()) {
            // Fall back to search-based Shorts discovery
            runCatching { searchPage("popular", SearchFilter(type = SearchType.SHORTS), null).shorts }
                .getOrDefault(emptyList()).forEach { collected.putIfAbsent(it.id, it.copy(isShort = true)) }
        }
        collected.values.toList()
    }

    suspend fun channel(browseId: String): Pair<ChannelItem?, List<VideoItem>> = withContext(Dispatchers.IO) {
        val text = viaClients(
            Purpose.BROWSE,
            primary = { innerTube.browse(YouTubeClient.WEB, browseId = browseId).bodyAsText() },
            fallback = { c -> raw.browse(c, browseId, null, null) },
        )
        val root = JsonWalk.parseObject(text)
        val channels = JsonWalk.extractChannels(root)
        val header = parseChannelHeader(root)
        (header ?: channels.firstOrNull()) to JsonWalk.extractVideos(root)
    }

    /** Channel Home with the creator's own shelves. */
    suspend fun channelHome(browseId: String): Triple<ChannelItem?, List<VideoItem>, List<app.youplay.domain.ChannelShelf>> =
        withContext(Dispatchers.IO) { cachedPage("home:$browseId", 120_000L) { loadChannelHome(browseId) } }

    private suspend fun loadChannelHome(browseId: String): Triple<ChannelItem?, List<VideoItem>, List<app.youplay.domain.ChannelShelf>> =
        withContext(Dispatchers.IO) {
            val root = JsonWalk.parseObject(
                viaClients(
                    Purpose.BROWSE,
                    primary = { innerTube.browse(YouTubeClient.WEB, browseId = browseId).bodyAsText() },
                    fallback = { c -> raw.browse(c, browseId, null, null) },
                ),
            )
            val header = parseChannelHeader(root) ?: JsonWalk.extractChannels(root).firstOrNull()
            Triple(header, JsonWalk.extractVideos(root), JsonWalk.extractShelves(root))
        }

    suspend fun relatedAndComments(videoId: String): Pair<List<VideoItem>, List<CommentItem>> =
        withContext(Dispatchers.IO) {
            val text = nextText(videoId, null)
            val root = JsonWalk.parseObject(text)
            JsonWalk.extractVideos(root) to JsonWalk.extractComments(root)
        }

    suspend fun comments(videoId: String): List<CommentItem> = relatedAndComments(videoId).second

    /** One page of comments. First call (continuation == null) finds the comment section via the watch response. */
    suspend fun commentsPage(videoId: String, continuation: String?): app.youplay.domain.CommentsPage = withContext(Dispatchers.IO) {
        val token = continuation ?: JsonWalk.commentsToken(
            JsonWalk.parseObject(nextText(videoId, null, Purpose.COMMENTS)),
        ) ?: return@withContext app.youplay.domain.CommentsPage(emptyList(), null, null)
        JsonWalk.parseCommentsPage(JsonWalk.parseObject(nextText(null, token, Purpose.COMMENTS)))
    }

    /**
     * Run a request as the primary client (the signed-in innertubex WEB session) and, when it throws or returns an
     * empty / error body, retry the same request as each following client of [purpose]'s chain (see [InnerTubeClients]).
     */
    private suspend fun viaClients(
        purpose: Purpose,
        primary: suspend () -> String,
        fallback: suspend (ClientProfile) -> String,
    ): String {
        var first: Throwable? = null
        try {
            val t = primary()
            if (usable(t)) return t
            first = IllegalStateException("empty InnerTube response")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            first = t
        }
        for (c in InnerTubeClients.chain(purpose).drop(1)) {
            try {
                val t = fallback(c)
                if (usable(t)) return t
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                // try the next client
            }
        }
        throw first ?: IllegalStateException("all InnerTube clients failed")
    }

    private fun usable(t: String) = t.length > 80 && !t.trimStart().startsWith("{\"error\"")

    private suspend fun nextText(videoId: String?, continuation: String?, purpose: Purpose = Purpose.NEXT): String =
        viaClients(
            purpose,
            primary = {
                if (continuation != null) innerTube.next(YouTubeClient.WEB, continuation = continuation).bodyAsText()
                else innerTube.next(YouTubeClient.WEB, videoId = videoId).bodyAsText()
            },
            fallback = { c -> raw.next(c, videoId, continuation) },
        )

    private val streamCache = TtlCache<String, StreamInfo>(48)
    private val streamFlight = SingleFlight<String, StreamInfo>()
    private val pageCache = TtlCache<String, Any>(24)

    /**
     * Resolve a playable stream, fast and with layered fallbacks:
     *  1. fresh in-memory cache (4 min) -> instant re-open / quality switch
     *  2. single-flight: preload and watch screen share one request
     *  3. hedged race: InnerTubeX extractor first; NewPipe (Flow's engine), then our per-purpose client chain
     *     (ANDROID_VR / IOS / TV ... see InnerTubeClients), then the legacy player calls start if it fails,
     *     returns a malformed URL, or takes > 2.5 s
     *  4. whole race retried once with backoff
     *  5. last resort: an older cached stream (URLs usually outlive the cache TTL)
     * [fresh] skips the cache (used after an expired-URL error).
     */
    suspend fun playerExtras(id: String): app.youplay.domain.PlayerExtras =
        runCatching { newPipe.extras(id) }.getOrDefault(app.youplay.domain.PlayerExtras())

    suspend fun resolveStream(
        videoId: String, maxHeight: Int = 1080, fresh: Boolean = false, purpose: Purpose = Purpose.PLAYBACK,
    ): StreamInfo = withContext(Dispatchers.IO) {
        val key = "$videoId@$maxHeight"
        if (!fresh) streamCache.get(key, 15 * 60_000L)?.let { return@withContext it }
        try {
            streamFlight.run(key) {
                val info = retrying(3) {
                    // every leg is URL-validated (UrlGuard); a leg with a malformed URL counts as failed.
                    // Backup leg: NewPipe (Flow's engine) first, then the direct player-client chain.
                    hedged(
                        1_600L,
                        { UrlGuard.require(viaExtractor(videoId, maxHeight), "extractor") },
                        {
                            try {
                                newPipe.resolve(videoId, maxHeight)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                try {
                                    resolveViaClients(videoId, maxHeight, purpose)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (t2: Throwable) {
                                    UrlGuard.require(resolveViaPlayer(videoId, maxHeight), "player client")
                                }
                            }
                        },
                    )
                }
                streamCache.put(key, info)
                info
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (!fresh) streamCache.getStale(key)?.let { return@withContext it }
            throw t
        }
    }

    /**
     * Network first (with retry); successful responses are stored on disk and used as a fallback when the
     * network or YouTube fails. With [ttlMs] > 0 a recent stored response is returned without any request.
     */
    private suspend fun browseCached(key: String?, ttlMs: Long, call: suspend () -> String): String {
        if (key != null && ttlMs > 0) cache.apiGet(key, ttlMs)?.let { return it }
        return try {
            retrying(2) { call() }.also { if (key != null) cache.apiPut(key, it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            key?.let { cache.apiGetStale(it) } ?: throw t
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Signed-out Home. YouTube returns an anonymous Home with no recommendations (a valid-looking response
    // that parses to zero videos), so [viaClients] never falls back. Guests therefore get: YouTube's own
    // Home if it actually has videos, otherwise a feed built from public searches.
    // ------------------------------------------------------------------------------------------------

    suspend fun guestHome(interests: List<String>): FeedPage = withContext(Dispatchers.IO) {
        officialGuestHome() ?: guestAll(0, interests)
    }

    /** Next page / topic chip for a feed built by [guestHome]. [token] must satisfy [GuestFeed.owns]. */
    suspend fun guestFeed(token: String): FeedPage = withContext(Dispatchers.IO) {
        when {
            token.startsWith(GuestFeed.ALL) -> guestAll(token.removePrefix(GuestFeed.ALL).toIntOrNull() ?: 0, emptyList())
            token.startsWith(GuestFeed.TOPIC) -> {
                val i = token.removePrefix(GuestFeed.TOPIC).toIntOrNull()?.coerceIn(0, GuestFeed.TOPICS.lastIndex) ?: 0
                guestTopic(GuestFeed.TOPICS[i].second, null)
            }
            else -> guestTopic(null, token.removePrefix(GuestFeed.CONT))
        }
    }

    private suspend fun officialGuestHome(): FeedPage? {
        for (c in listOf(InnerTubeClients.WEB, InnerTubeClients.MWEB)) {
            val page = runCatching {
                val text = browseCached("guest:home:${c.name}", 5 * 60_000L) { raw.browse(c, "FEwhat_to_watch", null, null) }
                parseFeed(JsonWalk.parseObject(text))
            }.getOrNull() ?: continue
            if (page.entries.count { it is FeedEntry.Video } >= 6) {
                return if (page.chips.size > 1) page else page.copy(chips = GuestFeed.chips())
            }
        }
        return null
    }

    private suspend fun guestAll(from: Int, interests: List<String>): FeedPage {
        val queries = GuestFeed.queriesFor(from, interests)
        val lists = kotlinx.coroutines.coroutineScope {
            queries.map { q -> async { runCatching { guestSearch(q.text, q.newest, null).first }.getOrDefault(emptyList()) } }
                .map { it.await() }
        }
        val videos = GuestFeed.interleave(lists)
        if (videos.isEmpty() && from == 0) throw java.io.IOException("Couldn't reach YouTube. Check your connection.")
        return FeedPage(
            videos.map { FeedEntry.Video(it) },
            if (from == 0) GuestFeed.chips() else emptyList(),
            GuestFeed.nextAll(from),
        )
    }

    private suspend fun guestTopic(query: String?, continuation: String?): FeedPage {
        val (videos, cont) = guestSearch(query, false, continuation)
        return FeedPage(
            GuestFeed.interleave(listOf(videos)).map { FeedEntry.Video(it) },
            emptyList(),
            cont?.let { GuestFeed.CONT + it },
        )
    }

    /** One public search page: videos only. [newest] sorts by upload date (interests), otherwise most viewed this month. */
    private suspend fun guestSearch(query: String?, newest: Boolean, continuation: String?): Pair<List<VideoItem>, String?> {
        val filter = if (newest) SearchFilter(type = SearchType.VIDEOS, sort = 2) else SearchFilter(type = SearchType.VIDEOS, date = 4, sort = 3)
        val q = if (continuation == null) query else null
        val params = if (continuation == null) filter.toParams() else null
        val key = if (continuation == null) "guest:q:$newest:$query" else null
        val text = browseCached(key, 10 * 60_000L) {
            viaClients(
                Purpose.SEARCH,
                primary = { innerTube.search(YouTubeClient.WEB, query = q, params = params, continuation = continuation, setLogin = false).bodyAsText() },
                fallback = { c -> raw.search(c, q, params, continuation) },
            )
        }
        val root = JsonWalk.parseObject(text)
        val videos = JsonWalk.extractVideos(root).filter { !it.isShort && !isLikelyShort(it) }
        return videos to JsonWalk.extractContinuation(root)
    }

    /** Sign-in / sign-out: everything cached belongs to the previous account. */
    fun onAccountChanged() {
        cache.apiClear(); streamCache.clear(); pageCache.clear()
    }

    /**
     * Shorts feed. Signed in: personalised from the account (FEshorts with the login, plus the Shorts the account's
     * Home and Subscriptions feeds contain), interleaved; later pages use YouTube's continuation tokens.
     * Signed out: the public Shorts feed. Falls back to [shorts] if every source comes back empty.
     */
    suspend fun shortsFeed(signedIn: Boolean, continuation: String?): ShortsPage = withContext(Dispatchers.IO) {
        suspend fun load(browseId: String?, cont: String?, key: String?): JsonObject? = runCatching {
            JsonWalk.parseObject(browseCached(key, 0L) {
                innerTube.browse(YouTubeClient.WEB, browseId = browseId, continuation = cont, setLogin = signedIn).bodyAsText()
            })
        }.getOrNull()

        fun shortsOf(root: JsonObject, strict: Boolean): List<VideoItem> =
            (JsonWalk.extractVideos(root).filter { it.isShort || (!strict && (it.durationText == null || isLikelyShort(it))) } +
                JsonWalk.extractShortsShelves(root).flatten())
                .distinctBy { it.id }.map { it.copy(isShort = true) }

        if (continuation != null) {
            val root = load(null, continuation, null) ?: return@withContext ShortsPage(emptyList(), null)
            return@withContext ShortsPage(shortsOf(root, strict = false), JsonWalk.extractContinuation(root))
        }
        val tag = if (signedIn) "u" else "g"
        val (main, home, subs) = kotlinx.coroutines.coroutineScope {
            val a = async { load("FEshorts", null, "shorts:$tag:main") }
            val b = if (signedIn) async { load("FEwhat_to_watch", null, "shorts:$tag:home") } else null
            val c = if (signedIn) async { load("FEsubscriptions", null, "shorts:$tag:subs") } else null
            Triple(a.await(), b?.await(), c?.await())
        }
        val lists = listOfNotNull(
            main?.let { shortsOf(it, strict = false) },
            home?.let { shortsOf(it, strict = true) },
            subs?.let { shortsOf(it, strict = true) },
        )
        val merged = LinkedHashMap<String, VideoItem>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(i)?.let { merged.putIfAbsent(it.id, it) } }
        if (merged.isEmpty()) return@withContext ShortsPage(shorts(), null)
        ShortsPage(merged.values.toList(), main?.let { JsonWalk.extractContinuation(it) })
    }

    /** Cache a parsed page for [ttlMs] (channel pages, tabs): back-navigation becomes instant. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cachedPage(key: String, ttlMs: Long, load: suspend () -> T): T {
        (pageCache.get(key, ttlMs) as? T)?.let { return it }
        val v = retrying(2) { load() }
        pageCache.put(key, v)
        return v
    }

    private suspend fun viaExtractor(videoId: String, maxHeight: Int): StreamInfo {
        val extracted = extractor.extract(
            videoId = videoId,
            hints = ContentHints(wantVideo = true, maxVideoHeight = maxHeight),
            audioQuality = AudioQuality.AUTO,
        ) ?: throw IllegalStateException("Extractor returned nothing for $videoId")
        val meta = extracted.mediaMetadata
        val info = StreamInfo(
            videoId = videoId,
            title = meta?.title ?: "Video",
            channelName = meta?.author.orEmpty(),
            channelId = meta?.channelId,
            thumbnailUrl = meta?.thumbnails?.firstOrNull()?.url,
            description = null,
            videoUrl = extracted.videoUrl,
            audioUrl = extracted.audioUrl,
            mimeType = extracted.videoMimeType ?: extracted.mimeType,
            playbackUrl = extracted.playbackTracking?.playbackUrl,
            cpn = extracted.playbackTracking?.clientPlaybackNonce,
        )
        if (info.videoUrl.isNullOrBlank() && info.audioUrl.isNullOrBlank() && info.hlsUrl.isNullOrBlank()) {
            throw IllegalStateException("Extractor produced no playable URL for $videoId")
        }
        return info
    }

    /** Our own client chain ([InnerTubeClients.chain]): first client that yields a playable, validated URL wins. */
    private suspend fun resolveViaClients(videoId: String, maxHeight: Int, purpose: Purpose): StreamInfo {
        var last: Throwable? = null
        for (c in InnerTubeClients.chain(purpose)) {
            try {
                return raw.player(videoId, c, maxHeight)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("No InnerTube client could resolve $videoId")
    }

    private suspend fun resolveViaPlayer(videoId: String, maxHeight: Int = 1080): StreamInfo {
        // TV embedded / IOS often return clear HTTPS URLs; ANDROID may 403 without PO tokens.
        val clients = listOf(
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID,
            YouTubeClient.WEB,
        )
        var lastError: Throwable? = null
        for (client in clients) {
            try {
                val response: PlayerResponse = innerTube.player(client, videoId).body()
                if (response.playabilityStatus.status != "OK") {
                    lastError = IllegalStateException(response.playabilityStatus.reason ?: response.playabilityStatus.status)
                    continue
                }
                val streaming = response.streamingData ?: continue
                val details = response.videoDetails
                val progressive = streaming.formats?.firstOrNull { !it.url.isNullOrBlank() }
                val videoAdaptive = streaming.adaptiveFormats
                    .filter { !it.url.isNullOrBlank() && it.mimeType.startsWith("video/") && (it.height ?: 0) <= maxHeight }
                    .maxByOrNull { it.height ?: 0 }
                val audioAdaptive = streaming.adaptiveFormats
                    .filter { !it.url.isNullOrBlank() && it.mimeType.startsWith("audio/") }
                    .maxByOrNull { it.bitrate }
                val videoUrl = progressive?.url ?: videoAdaptive?.url
                val audioUrl = audioAdaptive?.url ?: progressive?.url
                val hls = streaming.hlsManifestUrl
                if (videoUrl.isNullOrBlank() && audioUrl.isNullOrBlank() && hls.isNullOrBlank()) {
                    lastError = IllegalStateException("No clear URLs from player client")
                    continue
                }
                return StreamInfo(
                    videoId = videoId,
                    title = details?.title ?: "Video",
                    channelName = details?.author.orEmpty(),
                    channelId = details?.channelId,
                    thumbnailUrl = details?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    description = null,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    hlsUrl = hls,
                    mimeType = progressive?.mimeType ?: videoAdaptive?.mimeType,
                )
            } catch (t: Throwable) {
                if (t is StreamResolveException) throw t
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Unable to resolve stream for $videoId")
    }

    private fun isLikelyShort(item: VideoItem): Boolean {
        val d = item.durationText ?: return false
        val parts = d.split(":").mapNotNull { it.toIntOrNull() }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return false
        }
        return seconds in 1..60
    }

    private fun parseChannelHeader(root: JsonObject): ChannelItem? {
        var found: ChannelItem? = null
        JsonWalk.walk(root) { key, obj ->
            if (found != null) return@walk
            if (key == "c4TabbedHeaderRenderer" || key == "pageHeaderRenderer" || key == "channelMetadataRenderer") {
                val id = (obj["channelId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: (obj["externalId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: return@walk
                // title may be a plain string OR a runs object — textRuns handles both
                val title = JsonWalk.textRuns(obj["title"]) ?: return@walk
                found = ChannelItem(
                    id = id,
                    title = title,
                    thumbnailUrl = JsonWalk.bestThumb(obj["avatar"] ?: obj["thumbnail"]),
                    subscriberCountText = JsonWalk.textRuns(obj["subscriberCountText"]),
                    description = (obj["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: JsonWalk.textRuns(obj["description"]),
                )
            }
        }
        return found
    }
}
