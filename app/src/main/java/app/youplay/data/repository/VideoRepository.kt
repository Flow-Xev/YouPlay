package app.youplay.data.repository

import app.youplay.data.auth.AuthManager
import app.youplay.domain.FeedEntry
import app.youplay.domain.SearchFilter
import app.youplay.domain.SearchPage
import app.youplay.domain.WatchExtras
import app.youplay.domain.FeedPage
import app.youplay.domain.PlaylistItem
import app.youplay.data.local.LikedVideoDao
import app.youplay.data.local.LikedVideoEntity
import app.youplay.data.local.SearchHistoryDao
import app.youplay.data.local.SearchHistoryEntity
import app.youplay.data.local.SettingsDataStore
import app.youplay.data.local.SubscriptionDao
import app.youplay.data.local.SubscriptionEntity
import app.youplay.data.local.WatchHistoryDao
import app.youplay.data.local.WatchHistoryEntity
import app.youplay.data.remote.InnerTubeDataSource
import app.youplay.domain.ChannelItem
import app.youplay.domain.CommentItem
import app.youplay.domain.HomeFeed
import app.youplay.domain.StreamInfo
import app.youplay.domain.VideoItem
import javax.inject.Inject
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class VideoRepository @Inject constructor(
    private val remote: InnerTubeDataSource,
    private val watchHistoryDao: WatchHistoryDao,
    private val likedVideoDao: LikedVideoDao,
    private val subscriptionDao: SubscriptionDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val settingsDataStore: SettingsDataStore,
    private val auth: AuthManager,
    private val sync: app.youplay.data.sync.AccountSync,
    private val commentApi: app.youplay.data.remote.CommentApi,
    private val dislikedDao: app.youplay.data.local.DislikedVideoDao,
    private val extrasApi: app.youplay.data.remote.ExtrasApi,
    private val savedDao: app.youplay.data.local.SavedVideoDao,
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    val pendingSync = sync.pending
    suspend fun flushPending(): Int = sync.flush()
    fun observeDisliked(id: String): Flow<Boolean> = dislikedDao.observe(id)

    val watchHistory = watchHistoryDao.observe()
    val likedVideos = likedVideoDao.observe()
    val subscriptions = subscriptionDao.observe()
    val searchHistory = searchHistoryDao.observe()
    val settings = settingsDataStore.settings
    val account = auth.account
    val accounts = auth.accounts
    val savedVideos = savedDao.observe()
    fun observeSaved(id: String): Flow<Boolean> = savedDao.observeSaved(id)

    /** Bumped after every sign-in / sign-out / account switch, once caches and local mirrors are consistent again. */
    private val _accountEpoch = kotlinx.coroutines.flow.MutableStateFlow(0)
    val accountEpoch: kotlinx.coroutines.flow.StateFlow<Int> = _accountEpoch

    suspend fun isSignedIn() = auth.isSignedIn()
    suspend fun signIn(cookie: String, visitorData: String?, dataSyncId: String?) {
        val previous = auth.activeId()
        val id = auth.signIn(cookie, visitorData, dataSyncId)
        // Guest -> first account keeps what the guest did on this device; account -> other account starts clean.
        if (previous != null && previous != id) clearAccountMirror()
        accountChanged()
        scope.launch { runCatching { sync.flush() }; runCatching { syncFromAccount() } }
    }

    /** Signs out the active account (another saved account becomes active, if any). */
    suspend fun signOut() {
        val hasNext = auth.signOut()
        clearAccountMirror()
        accountChanged()
        if (hasNext) scope.launch { runCatching { syncFromAccount() } }
    }

    suspend fun signOutAll() {
        auth.signOutAll()
        clearAccountMirror()
        accountChanged()
    }

    suspend fun switchAccount(id: String) {
        if (id == auth.activeId()) return
        if (!auth.switchTo(id)) return
        clearAccountMirror()
        accountChanged()
        scope.launch { runCatching { syncFromAccount() } }
    }

    private suspend fun accountChanged() {
        lastAccountSync = 0L
        remote.onAccountChanged()
        _accountEpoch.value = _accountEpoch.value + 1
    }

    /** Likes, subscriptions, history and queued changes mirror one account; drop them when another takes over. */
    private suspend fun clearAccountMirror() {
        watchHistoryDao.clear(); likedVideoDao.clear(); subscriptionDao.clear(); dislikedDao.clear(); savedDao.clear()
        sync.clearPending()
    }

    // ---- feeds (Home / Subscriptions / History / Liked ...) ----
    suspend fun feed(browseId: String?, continuation: String? = null, fresh: Boolean = false): FeedPage {
        applyLocale()
        // Signed-out Home (and its topic chips / next pages) is built by us; see GuestFeed.
        if (continuation != null && app.youplay.data.remote.GuestFeed.owns(continuation)) return remote.guestFeed(continuation)
        if (browseId == "FEwhat_to_watch") {
            if (!isSignedIn()) return remote.guestHome(guestInterests())
            runCatching { sync.flush() }
            val page = remote.feed(browseId, continuation, fresh = fresh)
            // A brand-new account (or paused history) can have an empty Home: show discovery instead of a blank screen.
            if (continuation == null && page.entries.none { it is FeedEntry.Video })
                runCatching { remote.guestHome(guestInterests()) }.getOrNull()?.let { return it }
            return page
        }
        return remote.feed(browseId, continuation, fresh = fresh)
    }

    /** Channels the viewer watched most recently; they seed the first page of the signed-out Home. */
    private suspend fun guestInterests(): List<String> =
        watchHistoryDao.all().sortedByDescending { it.watchedAt }.map { it.channelName }
            .filter { it.isNotBlank() }.distinct().take(2)
    suspend fun playlists(): List<PlaylistItem> { applyLocale(); return remote.playlists("FEplaylist_aggregation") }
    suspend fun playlistVideos(id: String): List<VideoItem> {
        applyLocale()
        // Watch later and Liked also exist on the device, so they work signed out (and offline).
        suspend fun localList(): List<VideoItem>? = when (id) {
            "WL" -> savedDao.all().sortedByDescending { it.savedAt }.map { VideoItem(it.videoId, it.title, it.channelName, thumbnailUrl = it.thumbnailUrl) }
            "LL" -> likedVideoDao.all().sortedByDescending { it.likedAt }.map { VideoItem(it.videoId, it.title, it.channelName, thumbnailUrl = it.thumbnailUrl) }
            else -> null
        }
        if (!isSignedIn()) localList()?.let { return it }
        return try { remote.playlistVideos(id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) {
            localList()?.takeIf { it.isNotEmpty() } ?: throw t
        }
    }

    // ---- shorts queue shared between Home shorts row and the Shorts player ----
    private var shortsQueue: List<VideoItem> = emptyList()
    fun setShortsQueue(list: List<VideoItem>) { shortsQueue = list }
    fun shortsQueue(): List<VideoItem> = shortsQueue

    private var lastAccountSync = 0L

    /** Pulls from the account at most once per [maxAgeMs] (used by Home so likes / subs / history stay current). */
    suspend fun syncIfStale(maxAgeMs: Long = 15 * 60_000L): Int {
        if (System.currentTimeMillis() - lastAccountSync < maxAgeMs) return 0
        return syncFromAccount()
    }

    /** Lightweight: re-read just the subscribed-channel list from the account (pull-to-refresh on Subscriptions). */
    suspend fun refreshSubscribedChannels() {
        if (!isSignedIn()) return
        applyLocale()
        val now = System.currentTimeMillis()
        remote.subscribedChannels().takeIf { it.isNotEmpty() }?.let { subs ->
            subs.forEach { subscriptionDao.upsert(SubscriptionEntity(it.id, it.title, it.thumbnailUrl, now)) }
            val ids = subs.mapTo(HashSet()) { it.id }
            subscriptionDao.all().filter { it.channelId !in ids }.forEach { subscriptionDao.delete(it.channelId) }
        }
    }

    /** Pull subscriptions, liked videos and history from the account into local storage. */
    suspend fun syncFromAccount(): Int {
        if (!isSignedIn()) return 0
        lastAccountSync = System.currentTimeMillis()
        applyLocale()
        runCatching { sync.flush() } // push pending local changes first so the mirror below can't undo them
        var n = 0
        val now = System.currentTimeMillis()
        // The account is the source of truth: what it has is added, and what it no longer has is removed here.
        runCatching { remote.subscribedChannels() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { subs ->
            subs.forEach { subscriptionDao.upsert(SubscriptionEntity(it.id, it.title, it.thumbnailUrl, now)); n++ }
            val ids = subs.mapTo(HashSet()) { it.id }
            subscriptionDao.all().filter { it.channelId !in ids }.forEach { subscriptionDao.delete(it.channelId) }
        }
        val (liked, likedAll) = importFeed("VLLL", pages = 6)
        var i = 0
        for (v in liked) { likedVideoDao.upsert(LikedVideoEntity(v.id, v.title, v.channelName, v.thumbnailUrl, now - (i++) * 1000L)); n++ }
        if (likedAll && liked.isNotEmpty()) {
            val ids = liked.mapTo(HashSet()) { it.id }
            likedVideoDao.all().filter { it.videoId !in ids && now - it.likedAt > 600_000 }.forEach { likedVideoDao.delete(it.videoId) }
        }
        val (hist, histAll) = importFeed("FEhistory", pages = 10)
        i = 0
        for (v in hist) {
            val old = watchHistoryDao.get(v.id)
            val at = maxOf(old?.watchedAt ?: 0L, now - (i++) * 1000L)
            watchHistoryDao.upsert(WatchHistoryEntity(v.id, v.title, v.channelName, v.thumbnailUrl, at, old?.progressMs ?: 0L)); n++
        }
        if (histAll && hist.isNotEmpty()) {
            val ids = hist.mapTo(HashSet()) { it.id }
            watchHistoryDao.all().filter { it.videoId !in ids && now - it.watchedAt > 600_000 }.forEach { watchHistoryDao.delete(it.videoId) }
        }
        auth.refreshProfile()
        return n
    }

    /** Walks a feed's continuation pages (fresh, uncached) and flattens videos and Shorts, newest first. */
    private suspend fun importFeed(browseId: String, pages: Int): Pair<List<VideoItem>, Boolean> {
        val out = LinkedHashMap<String, VideoItem>()
        var cont: String? = null
        repeat(pages) {
            val page = runCatching { remote.feed(browseId, cont, fresh = true) }.getOrNull() ?: return@repeat
            page.entries.forEach { e ->
                when (e) {
                    is FeedEntry.Video -> out.putIfAbsent(e.item.id, e.item)
                    is FeedEntry.ShortsRow -> e.shorts.forEach { out.putIfAbsent(it.id, it) }
                }
            }
            cont = page.continuation
            if (cont == null) return out.values.toList() to true
        }
        return out.values.toList() to false
    }

    /** Push local-only likes and subscriptions up to the account. */
    suspend fun pushLocalToAccount(): Int {
        if (!isSignedIn()) return 0
        applyLocale()
        var n = 0
        likedVideoDao.observe().first().forEach { if (runCatching { remote.like(it.videoId, true) }.isSuccess) n++ }
        subscriptionDao.observe().first().forEach { if (runCatching { remote.subscribe(it.channelId, true) }.isSuccess) n++ }
        return n
    }

    suspend fun home(): HomeFeed {
        applyLocale()
        return remote.homeFeed()
    }

    suspend fun search(query: String): List<VideoItem> {
        applyLocale()
        searchHistoryDao.upsert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        return remote.search(query.trim())
    }

    suspend fun searchPage(query: String, filter: SearchFilter, continuation: String? = null): SearchPage {
        applyLocale()
        if (continuation == null) searchHistoryDao.upsert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        return remote.searchPage(query.trim(), filter, continuation)
    }
    suspend fun suggestions(q: String) = remote.suggestions(q)
    suspend fun channelTab(id: String, params: String) = run { applyLocale(); remote.channelTab(id, params) }
    suspend fun playerExtras(id: String) = remote.playerExtras(id)
    suspend fun watchExtras(id: String): WatchExtras { applyLocale(); return remote.watchExtras(id).first }
    suspend fun voteStats(id: String) = extrasApi.votes(id)
    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean =
        isSignedIn() && runCatching { remote.addToPlaylist(playlistId, videoId) }.isSuccess

    /** Watch later: always kept on the device, and sent to the account through the retry queue when signed in. */
    suspend fun saveToWatchLater(video: VideoItem): app.youplay.data.sync.SyncResult {
        savedDao.upsert(app.youplay.data.local.SavedVideoEntity(video.id, video.title, video.channelName, video.thumbnailUrl, System.currentTimeMillis()))
        return sync.send(app.youplay.data.sync.AccountSync.WATCH_LATER, video.id, "add")
    }

    suspend fun saveToWatchLater(videoId: String): app.youplay.data.sync.SyncResult {
        if (savedDao.observeSaved(videoId).first().not()) {
            val h = watchHistoryDao.get(videoId)
            savedDao.upsert(app.youplay.data.local.SavedVideoEntity(videoId, h?.title ?: "Video", h?.channelName ?: "", h?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg", System.currentTimeMillis()))
        }
        return sync.send(app.youplay.data.sync.AccountSync.WATCH_LATER, videoId, "add")
    }

    suspend fun removeSaved(videoId: String) = savedDao.delete(videoId)

    suspend fun toggleDislike(videoId: String): Pair<Boolean, app.youplay.data.sync.SyncResult> {
        val was = dislikedDao.observe(videoId).first()
        return if (was) {
            dislikedDao.delete(videoId)
            false to sync.send(app.youplay.data.sync.AccountSync.REACT, videoId, "none")
        } else {
            dislikedDao.upsert(app.youplay.data.local.DislikedVideoEntity(videoId, System.currentTimeMillis()))
            likedVideoDao.delete(videoId)
            true to sync.send(app.youplay.data.sync.AccountSync.REACT, videoId, "dislike")
        }
    }

    suspend fun searchChannels(query: String): List<ChannelItem> {
        applyLocale()
        return remote.searchChannels(query.trim())
    }

    /** Account-personalised Shorts when signed in, with already-watched Shorts filtered out. */
    suspend fun shortsFeed(continuation: String? = null): app.youplay.domain.ShortsPage {
        applyLocale()
        val page = remote.shortsFeed(isSignedIn(), continuation)
        val unseen = page.videos.filter { watchHistoryDao.get(it.id) == null }
        return page.copy(videos = unseen.ifEmpty { page.videos })
    }

    suspend fun shorts(): List<VideoItem> {
        applyLocale()
        return remote.shorts()
    }

    suspend fun channel(id: String): Pair<ChannelItem?, List<VideoItem>> {
        applyLocale()
        return remote.channel(id)
    }

    suspend fun channelHome(id: String) = run { applyLocale(); remote.channelHome(id) }

    suspend fun comments(videoId: String): List<CommentItem> {
        applyLocale()
        return remote.comments(videoId)
    }

    suspend fun commentsPage(videoId: String, continuation: String? = null): app.youplay.domain.CommentsPage {
        applyLocale()
        return remote.commentsPage(videoId, continuation)
    }
    suspend fun postComment(createParams: String, text: String) = run { auth.ensure(); commentApi.post(createParams, text) }
    suspend fun replyToComment(replyParams: String, text: String) = run { auth.ensure(); commentApi.reply(replyParams, text) }
    suspend fun commentAction(token: String) { auth.ensure(); commentApi.action(token) }

    suspend fun related(videoId: String): List<VideoItem> {
        applyLocale()
        return remote.relatedAndComments(videoId).first
    }

    suspend fun stream(
        videoId: String, maxHeight: Int = 1080, record: Boolean = true, fresh: Boolean = false,
        purpose: app.youplay.data.remote.Purpose = app.youplay.data.remote.Purpose.PLAYBACK,
    ): StreamInfo {
        applyLocale()
        val info = remote.resolveStream(videoId, maxHeight, fresh, purpose)
        if (!record) return info
        val oldProgress = watchHistoryDao.get(videoId)?.progressMs ?: 0L
        watchHistoryDao.upsert(
            WatchHistoryEntity(
                videoId = info.videoId,
                title = info.title,
                channelName = info.channelName,
                thumbnailUrl = info.thumbnailUrl,
                watchedAt = System.currentTimeMillis(),
                progressMs = oldProgress,
            ),
        )
        if (info.playbackUrl != null && info.cpn != null && isSignedIn()) {
            runCatching { remote.reportPlayback(info.playbackUrl, info.cpn) }
        }
        return info
    }

    suspend fun savedProgress(videoId: String): Long = watchHistoryDao.get(videoId)?.progressMs ?: 0L

    fun observeLiked(videoId: String): Flow<Boolean> = likedVideoDao.observeLiked(videoId)

    suspend fun toggleLike(video: VideoItem): Pair<Boolean, app.youplay.data.sync.SyncResult> {
        val liked = likedVideoDao.observeLiked(video.id).first()
        return if (liked) {
            likedVideoDao.delete(video.id)
            false to sync.send(app.youplay.data.sync.AccountSync.REACT, video.id, "none")
        } else {
            likedVideoDao.upsert(
                LikedVideoEntity(video.id, video.title, video.channelName, video.thumbnailUrl, System.currentTimeMillis()),
            )
            dislikedDao.delete(video.id)
            true to sync.send(app.youplay.data.sync.AccountSync.REACT, video.id, "like")
        }
    }

    fun observeSubscribed(channelId: String): Flow<Boolean> = subscriptionDao.observeSubscribed(channelId)

    suspend fun toggleSubscribe(channel: ChannelItem): Pair<Boolean, app.youplay.data.sync.SyncResult> {
        val sub = subscriptionDao.observeSubscribed(channel.id).first()
        return if (sub) {
            subscriptionDao.delete(channel.id)
            false to sync.send(app.youplay.data.sync.AccountSync.SUB, channel.id, "off")
        } else {
            subscriptionDao.upsert(SubscriptionEntity(channel.id, channel.title, channel.thumbnailUrl, System.currentTimeMillis()))
            true to sync.send(app.youplay.data.sync.AccountSync.SUB, channel.id, "on")
        }
    }

    suspend fun clearHistory() = watchHistoryDao.clear()
    suspend fun removeFromHistory(id: String) = watchHistoryDao.delete(id)
    suspend fun clearSearchHistory() = searchHistoryDao.clear()
    suspend fun removeSearch(q: String) = searchHistoryDao.delete(q)

    suspend fun updateSettings(transform: (app.youplay.data.local.AppSettings) -> app.youplay.data.local.AppSettings) =
        settingsDataStore.update(transform)

    private suspend fun applyLocale() {
        auth.ensure()
        val s = settingsDataStore.settings.first()
        remote.configureLocale(s.language, s.regionCode)
    }
}
