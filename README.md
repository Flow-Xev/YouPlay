# YouPlay

Native Kotlin/Compose YouTube client (InnerTubeX, Media3, Room, Hilt). Local.properties must point to your Android SDK.

Build: `./gradlew :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`

---
## Progress log

### Stage 1 - Login, personalized Home, Shorts rows, chips  (DONE)
**What changed**
| Area | Change |
|---|---|
| Login | `ui/screens/login/LoginScreen.kt` - Google sign-in in a WebView (desktop UA so Google allows it). On reaching youtube.com it reads cookies (SAPISID) + `VISITOR_DATA` + `DATASYNC_ID` and calls `VideoRepository.signIn`. |
| Session | `data/local/SessionStore.kt` (DataStore: cookie, ids, cached profile), `data/auth/AuthManager.kt` (applies session to InnerTube with `replaceSession(..., useLoginForBrowse = true)`, profile via `accountMenu`, sign-out). Session restored on every request via `applyLocale()` -> `auth.ensure()`. Cloud backup disabled so cookies never leave the device. |
| Account sync | `VideoRepository.syncFromAccount()` pulls subscriptions (FEchannels), liked videos (VLLL), history (FEhistory) into Room. `pushLocalToAccount()` pushes local likes/subs up. Like/subscribe toggles now also hit YouTube when signed in. Buttons are on the Account screen. |
| Home | Rebuilt: ordered feed (`FeedEntry.Video` / `FeedEntry.ShortsRow`), filter chips (real YouTube chips via continuation tokens, plus "All"), infinite scroll, pull-to-refresh. If YouTube sends no Shorts shelf, one is built from the Shorts feed and inserted after 4 videos. Signed in = personalised recommendations. |
| Shorts row | `ShortsRowSection` in `ui/components/Common.kt` (horizontal 9:16 carousel). Tap opens `shorts_player/{id}` which starts at that short and continues. |
| Parsing fixes | `JsonWalk`: new-style `lockupViewModel` titles/channel/views/duration now parse (previously showed "Video"); playlists no longer mis-parsed as videos; added chips, continuation, shorts-shelf, playlist, profile parsers. Fixed a compile error in `VideoCard` (`Spacer(Modifier = ...)`). |

**How to test**
1. Build & install. Home loads as guest (chips + Shorts row).
2. Account tab -> Sign in -> log in with Google. You return automatically; Home refreshes with your recommendations.
3. Account -> "Get everything from account" / "Push local likes & subs".

**Known risks (I could not compile or run on a device in this environment)**
- Fix any small compile errors Gradle reports (paste them to me and I'll fix).
- YouTube changes its JSON often; if a section is empty the parser in `JsonWalk` is the place to look.
- Google may show extra verification on WebView logins; use a normal account, complete 2FA in the page.

### Stage 2 - Subscriptions, Library from account, history push  (DONE)
| Area | Change |
|---|---|
| Subscriptions tab | New bottom tab `ui/screens/subscriptions/`. Signed in: avatar strip of your channels + your real subscriptions feed (`FEsubscriptions`) with infinite scroll and Shorts rows. Signed out: local channel strip + sign-in prompt. |
| Library | Playlists row (Watch later `WL` + your account playlists via `FEplaylist_aggregation`), then History / Liked / Subscriptions (synced from account into Room). New `PlaylistScreen` (route `playlist/{id}`). |
| History push | `StreamInfo` now carries YouTube playback-tracking URL + nonce; `VideoRepository.stream()` calls `registerPlayback` when signed in, so watched videos appear in your YouTube history and improve recommendations. |
| Navigation | Bottom bar is now Home, Shorts, Subs, Library, Account. Search moved to a magnifier icon in the top bar (`BrandBar(onSearch=...)`), route `search`. |
| Shared UI | `feedEntries()` in `Common.kt` renders videos + Shorts carousels for any feed. |

**How to test**: sign in, open Subs (feed loads), open Library (Playlists row appears), play a video then check youtube.com history.
**Notes**: Watch-later/playlist add buttons arrive in Stage 3 (Watch screen).
### Stage 3 - Search, Channel page, Watch actions  (DONE)
| Area | Change |
|---|---|
| Search | Rewritten (`ui/screens/search/`). Live suggestions, history, type chips (All / Videos / Shorts / Channels / Playlists), Filters dialog (upload date, duration, sort - encoded into YouTube's `params` protobuf by `SearchFilter.toParams()`), infinite scroll, channel cards on top, a Shorts carousel inside results, Shorts-only grid, playlist results. Uses your login when signed in. |
| Channel page | New `ui/screens/channel/ChannelPageScreen.kt`: header + Subscribe (pushes to account), tabs Home / Videos / Shorts / Playlists (tab `params` are YouTube's known channel-tab tokens). |
| Watch | Rewritten: views/date, expandable description, Subscribe, Like, Dislike, Share, Save (Watch later or any playlist, pushed to account), Comments, playback speed (0.5x-2x), snackbar feedback, up-next list. |
| Shorts player | Channel name, Like (pushed to account), Comments, Share; videos loop. |

**How to test**: tap the magnifier (top bar) -> type a query -> try the Shorts chip and Filters. Open a channel -> switch tabs. Open a video -> Save to Watch later, check on youtube.com.

### Round 2 - Stage 1: Player engine, mini player, overlay menu (DONE)
- `ui/player/PlayerController.kt`: one app-wide ExoPlayer (survives navigation). `PlaybackService` is now a real MediaSessionService (notification / lock-screen controls, background audio).
- `ui/player/WatchPlayer.kt`: custom overlay. Tap = controls, double-tap = -/+10s, **press & hold = 2x while held**, **3-dots menu** (Quality, Speed 0.25-2x, Loop, Share), fullscreen button, minimize.
- `ui/player/MiniPlayer.kt` (`FloatingMiniPlayer`): floating mini window in the style of Flow (github.com/A-EDev/Flow) / YouTube: controls drawn on the video (play/pause, close, -10s/+10s, progress line), drag anywhere and it springs to the nearest corner, drag off the side to dismiss, tap = expand. Pull the full player down to minimize. Re-implemented natively, not copied (Flow is GPL-3.0).
- Landscape: rotate the phone (or tap fullscreen) -> immersive fullscreen player; back exits fullscreen.
- Quality = re-resolves the stream at the chosen max height and resumes at the same position.

### Round 2 - Stage 2: Downloads + merged You tab (DONE)
- **Downloads**: `data/download/OfflineDownloads.kt` (ranged chunk download of video + audio into app storage, progress in Room table `downloads`, DB migration 1->2). Download button on the Watch screen and in the player's 3-dots menu (uses current quality, default 720p). Downloaded videos play offline automatically (`localStream`). `Downloads` screen lists/cancels/deletes/retries. Downloads run while the app process is alive.
- **You tab** (`ui/screens/you/YouScreen.kt`): Library + Account merged. Profile header with Sign in/out, Sync/Push, Settings; then horizontal rows for History, Playlists, Downloads, Liked, Subscriptions. Bottom bar is now Home / Shorts / Subs / You.

### Round 2 - Stage 3: Home redesign + extra features (DONE)
- **New video card** (all screens): rounded thumbnail, channel initial avatar, 2-line title, meta, **⋮ menu** (Save to Watch later, Download, Share) - `ui/components/VideoActions.kt`.
- **Home**: sticky YouTube-style pill chips, shimmer skeleton while loading (`PillChips`, `FeedSkeleton` in `Common.kt`).
- **Resume playback**: position saved every 5s / on pause into watch history and restored next time (reset when finished).
- **Autoplay next**: when a video ends, plays the first "Up next" video (Settings -> Autoplay).

### Round 3 - Live sync to your YouTube account (DONE)
- `data/sync/AccountSync.kt`: every Like, Dislike (and removing either), Subscribe / Unsubscribe and Watch-later save is applied on the device instantly and sent to YouTube right away. If YouTube is unreachable or rejects it, the change is queued in Room (`pending_actions`, DB migration 2->3) and retried automatically (on sign-in, on Home refresh, or via "Sync now" on the You tab). Newest state per video/channel wins.
- Dislike is now a real toggle with its own saved state; liking clears dislike and vice versa. Removing a reaction calls YouTube's remove-like.
- Every action shows what happened: "on YouTube", "will sync when YouTube is reachable", or "sign in to sync".
- Shorts got a Dislike button; the card menu's Save to Watch later uses the queue.

### Round 4 - Comments: post, reply, like, delete (DONE)
- Comments now load properly (watch response -> comment-section continuation -> pages, "Load more").
- `data/remote/CommentApi.kt` signs and sends `comment/create_comment`, `create_comment_reply` and `perform_comment_action` with your session (InnerTubeX has no comment endpoints). Comment box at the bottom of the Comments screen; Reply / Like / Delete (with confirm) appear per comment only when YouTube provides that action for your account (Delete only on your own comments).
- Risk: YouTube's comment JSON changes often. Parsing lives in `JsonWalk.parseCommentsPage`; if a button never appears, the token lookup there is the place to adjust.

---
## Build & troubleshooting
1. Open the folder in Android Studio (or `./gradlew :app:assembleDebug`), JDK 21, set `sdk.dir` in `local.properties`.
2. I could not compile in my sandbox (no Android SDK), so expect possibly a few small compile errors. Send me the Gradle error text and I'll fix them quickly.
3. If a feed shows nothing, YouTube probably changed its JSON; the parsers are all in `util/JsonWalk.kt`.
4. Login uses a WebView with a desktop user agent; the cookie is stored in app-private DataStore (backup disabled).

## Still missing vs YouTube (ideas for next round)
Comment posting/replies, creating playlists, notifications, downloads, picture-in-picture/background play UI, captions, quality picker, live chat, Trending/Explore tab, video chapters, offline cache of feeds.


## Round 5 — Settings & themes
- 4 themes (Red, Mono, Ocean, Violet) with live swatch cards, plus a Dark | AMOLED toggle (AMOLED = pure black backgrounds).
- Grouped Settings: Appearance, Playback (autoplay, background play, default quality/speed), Downloads (quality, Wi-Fi only), Content (region, language, restricted), History & privacy, About.
- Settings are wired in: default quality/speed on new videos, download quality + Wi-Fi-only check, hide Shorts shelf on Home, background-play pause in MainActivity.
- Not compiled (no Android SDK in the sandbox) — expect small fixes on first build.

## Round 6 — Flow-inspired features
- SponsorBlock auto-skip (hash-prefix API, so the server never sees the video id) with a "Skipped sponsor" pill.
- Return YouTube Dislike counts on the Like / Dislike buttons.
- Sleep timer in the player menu, picture-in-picture when leaving the app from the Watch screen.
- Home: "Continue watching" shelf. Search: remove single history items, tap-arrow to edit a suggestion.
- Settings toggles: SponsorBlock, Return YouTube Dislike, Picture-in-picture.
(Ideas are from Flow's feature list; the code is original. Still uncompiled.)

## Round 7 — Smart Auto quality
- **Starts right:** first quality comes from the measured throughput (ExoPlayer bandwidth meter, shared app-wide), capped by network type, Data Saver, Battery Saver and the size of the player view (no 1080p in a 600px-tall portrait player).
- **Adapts while playing** every 2 s: drops fast when the buffer runs low or a stall happens, climbs only when the buffer is healthy and bandwidth has 1.6x headroom (30 s cool-down, max 3 up-switches/video). Shows "Auto: 480p · slow network".
- **Buffer follows the network:** deep (90 s) on Wi-Fi or weak links, shallow on Data Saver / mobile; a 256 MB disk cache keeps already-fetched bytes.
- **Preloads the next video:** ~20 s in it resolves the Up next stream, and on unmetered networks caches its first seconds, so autoplay starts almost instantly.
- **Background audio:** video download stops while the screen is off / app is in the background.
- Settings: Auto maximum (up to 4K), Auto cap on mobile data, Preload next video.
- Auto works by re-resolving the stream at a new height (the app plays separate progressive video/audio, not DASH), so each switch is a brief re-buffer.

## Round 8 — Stage 1: Player + Auto
- Auto: harmonic-mean bandwidth estimator, cold start at 480p then fast ramp, one-rung climbs, panic drop on long stalls, no climbing back to a quality that just failed, seamless in-place switching (last frame stays, pause state kept), expired-URL auto-recovery.
- Player: slim seek bar with buffered range, 10s buttons, chained double-tap skipping, fullscreen swipe for brightness/volume, fit/fill toggle, quality chip, network speed in the quality menu.

## Round 8 — Stage 2: Channel screen
Blurred-avatar banner, overlapping avatar, centered name/stats/expandable description, pill Subscribe button, sticky tab bar with underline, empty/error/retry states, floating back button.

## Round 8 — Stage 3: YouPlay
App name is now YouPlay (launcher label, home header, About). Logo background removed (transparent PNG) in the home header. Package and applicationId are now `app.youplay`.

## Round 8 — Channel Home like YouTube
Featured latest upload, then shelves: Latest videos, Shorts, Popular videos, Playlists, and More videos.

## Round 8 — Creator shelves
Channel Home now renders the creator's own shelves in their order (featured video, custom shelves, playlist shelves with "Play all", Shorts), parsed by JsonWalk.extractShelves; a Playlists shelf is appended if the creator has none. Falls back to the generated layout if YouTube returns no shelves.

## Build fix
Kotlin 2.4.20, KSP 2.3.8, Hilt 2.57.2, Room 2.8.4, Ktor 3.6.0, serialization 1.11.0 (innertubex needs Kotlin 2.4 metadata); kotlin-metadata-jvm forced to 2.4.20 for the annotation processors.

## Resilient InnerTube layer
data/remote/Resilience.kt: TtlCache, SingleFlight, retrying, hedged. Stream resolve = cache -> single-flight -> hedged (extractor vs player-client chain) -> retry -> stale cache; channel pages cached 2 min; feeds retried; expired-URL recovery and Retry re-resolve fresh.

## Caching + account Shorts
CacheManager: gzip API cache (stale fallback when offline/failed), 200 MB image cache, media cache clear hook, auto-clean at start + every 6 h (6-hour expiry, 40 MB API cap, low-space purge), Settings > Storage & cache. Shorts feed: signed in = FEshorts + account Home/Subscriptions Shorts interleaved, continuation paging, watched Shorts filtered, next 2 streams pre-resolved.

## Crash screen + icon
crash/CrashHandler + CrashActivity (plain views, own :crash process): report, Copy/Share/Restart/Reset app data. Proper adaptive launcher icon (transparent logo foreground, monochrome) and Android 12 splash icon. Compose BOM 2025.10.01 (compiler 2.4 needs a newer Compose runtime than 2024.12).

## Round 9 — Stage A: Custom chrome + layouts (DONE)
- New `YpChrome.kt`: custom subscribe / action pills / chips / avatars / related rows (no Material buttons/chips/nav pills).
- **Thin bottom bar** ~40dp, icon + 9sp label, no Material selection pill.
- **Shorts**: YouTube/Flow-style right rail (like/dislike/comment/share), spinning channel disc, Subscribe chip, poster while loading.
- **Watch**: YouTube meta (avatar + subs + custom pills), comment teaser, compact related rows, drag-to-mini shrink animation.
- **Search**: custom search field (BasicTextField), compact result rows, custom filter dialog.
- **Home/Channel**: no Scaffold chrome; custom subscribe on channel.
- Full↔mini: AnimatedVisibility scale/fade/slide when mini appears.
- InnerTube: feed soft TTL 5 min, per-item parse isolation, `extractWatchOwner` for channel avatar/subs.

## Round 9 — Stage B: Big player YouTube feel (DONE)
- Watch drag-down shrinks toward bottom-right (mini handoff feel); player overlay kept custom (not Material Media3 controller).

## Round 9 — Stage C: Stronger InnerTube (DONE)
- Safer watch extras + feed parse; soft-TTL browse cache; owner avatar/subscriber extraction.

## Round 10 — Shorts polish + permanent playback fix
- **Shorts rail** is smaller (22dp icons, 9sp labels, 32dp disc) and hugs the right edge; the caption stack was narrowed to match.
- **Long-press anywhere** on a Short opens the 3-dots menu: Share, Copy link, Save to Watch later. Vertical swiping and the Subscribe chip / rail still work.
- **`MalformedURLException` ("Source error [IO_NETWORK_CONNECTION_FAILED …]") fixed at the root:**
  - `data/remote/UrlGuard.kt` cleans and validates every stream URL (escapes, protocol-relative, http→https, `java.net.URL` check). A resolver returning a bad URL now counts as *failed*, so the next resolver is used. The same guard sits in `PlayerController.play` and `VideoPlayer`.
  - `data/remote/NewPipeResolver.kt`: NewPipeExtractor (the engine the Flow app uses) is the backup leg of the hedged resolve, ahead of the direct player-client chain.
  - Build: NewPipeExtractor v0.26.5 via JitPack, OkHttp, core-library desugaring.
- **CI:** `.github/workflows/build.yml` builds the debug APK on every push / PR and uploads it as an artifact (and uploads reports when it fails), so compile errors show up on GitHub instead of on your phone. Push this folder to a GitHub repo to use it.

## Round 11 — Full InnerTube layer with per-purpose clients + fallback
- `data/remote/InnerTubeClients.kt`: client profiles (WEB, MWEB, WEB_EMBEDDED, ANDROID_VR, IOS, ANDROID, TVHTML5, TV_EMBEDDED) and an ordered **chain per purpose**:
  BROWSE / SEARCH / NEXT / COMMENTS = WEB → MWEB; PLAYBACK / DOWNLOAD = ANDROID_VR → IOS → TV_EMBEDDED → TV → MWEB → ANDROID → WEB_EMBEDDED; SHORTS = IOS → ANDROID_VR → TV_EMBEDDED → …
  Client versions are only defined here (bump them here when YouTube retires one). The googlevideo `c=` → User-Agent mapping used by `StreamHttp` comes from the same table.
- `data/remote/InnerTubeRaw.kt`: client-parameterised `browse` / `search` / `next` / `player` calls (anonymous, own context, anonymous visitorData).
- `InnerTubeDataSource.viaClients(...)`: every feed / search / channel / watch / comments request runs as the signed-in WEB session first and, if it throws or returns an empty body, is retried through the rest of that purpose's chain.
- Stream resolve: extractor ‖ (NewPipe → client chain → legacy player calls), every leg URL-validated. Shorts use the SHORTS chain.

## Round 12 (3 stages)
- **Stage 1 – full↔mini player:** `WatchScreen` owns a progress value (0 full … 1 mini). Dragging the player scales/moves it to the mini window's exact corner (remembered in `PlayerController.miniRight/miniBottom`), page content fades, release past 30 % hands off to `FloatingMiniPlayer` with no pop animation; tapping the mini expands from the same rect. Bottom bar 30dp → 24dp.
- **Stage 2 – player + cards:** always-visible 2dp progress line when controls hide, softer control fade/scale, double-tap skip feedback on its own side, feed cards fade/slide in, duplicate LazyColumn key fixed.
- **Stage 3 – Auto + InnerTube:** faster cold start (no 480p cap when meter > 6 Mbps), quicker drops (3 s stall / 5 s low buffer), faster ramp-up (3.5 s cooldown, 7 s buffer), 1 s tick; stream cache 15 min, hedge at 1.6 s, 3 retries with exponential backoff + jitter.

## Round 13 — Player features, Library, Subscriptions
- Pinch: out = fullscreen (portrait) / fill, in = fit / exit fullscreen. Chapters (NewPipe segments, else description timestamps): ticks on the seek bar, current chapter in the time row, tappable chapter chips on the Watch page. Seek preview thumbnails from the storyboard (NewPipe previewFrames). Next-video button in the transport row.
- Related: Shorts shelf always shown (falls back to the account Shorts feed when YouTube sends none).
- You tab: quick tiles (Watch later, Liked, Downloads, Channels). Subscriptions: All / Videos / Shorts filter chips, Shorts grid.
- Not compiled; `NewPipeResolver.extras` uses Frameset/StreamSegment getters from NewPipeExtractor 0.26.5.

## Round 14 (3 stages)
- **Stage 1 – Home:** `HomeViewModel` is a Kotlin Flow pipeline: raw account videos + Shorts pool -> `composeHomeFeed` (video, 6 Shorts, video, video, 6 Shorts, ... until the account runs out) -> UI state; a `collectLatest` Flow tops up Shorts from the account's Shorts feed, a conflated Flow serves scroll-to-end paging. New Home UI (`HomeComponents.kt`): account header + greeting, chips that stay while loading, big cards, Shorts panel rows, guest sign-in card, "all caught up" footer. Account sync (likes/subs/history) runs on Home load, throttled to 15 min.
- **Stage 2 – Sheets:** Comments and Description are bottom sheets (`CommentsSheet`, `DescriptionSheet`) on Watch and Shorts. Comments post/reply/like/delete through the signed-in account. The Comments route/screen is gone.
- **Stage 3 – Account state:** Shorts like/dislike/subscribe read the account-synced database (survive swipes, match Watch page).
- Not compiled (no Android SDK in the sandbox): paste any Gradle errors.

## Round 14b
- Home header reverted to logo + YouPlay + search (no avatar/greeting).
- Account is the source of truth: `syncFromAccount` now mirrors subscriptions, liked videos and watch history (removes local rows the account no longer has; likes/history only when every page was read and older than 10 min). Home "From your history" shelf = top of the account history when signed in.
- Description sheet redesigned (Views / Date strip, channel row, chapters card, text card); Watch preview shows views + date; `extractWatchMeta` also reads `videoDescriptionHeaderRenderer` and the microformat (views, publish date). Shorts load these on opening the sheet.

## Round 14c — Shorts player
Own design (Flow-inspired features only, no side rail): tap pause/resume with pop glyph, double-tap like (heart burst, sent to account), hold = 2x, bottom glass action dock (Like/Dislike/Comments/Share/Save), draggable seek line with time bubble, top bar with Mute + menu (speed 0.5–2x, auto-scroll on end, description, share, copy link, watch later). Mute/auto-scroll/speed persist across Shorts. `VideoPlayer` got `paused/muted/loop/onPlayer/onEnded`.

## Changes in this version
- **Signed-out Home fixed.** YouTube returns an empty anonymous Home that looked like a success, so no fallback ran. Guests now get YouTube's Home when it has videos, otherwise a feed built from public searches (seeded by channels you watched), with topic chips and infinite scroll (`GuestFeed.kt`).
- **Flow-driven Home.** Home, Subscriptions and Library react to a repository `accountEpoch` Flow, so sign-in, sign-out and account switches reload them with the right identity.
- **Multiple accounts.** `SessionStore` keeps every login; switch, add or sign out from the avatar on Home or the You tab (`AccountSheet`). Existing single logins migrate automatically.
- **Library.** Watch later and Liked work signed out (Room v4, `saved_videos`); new account card, Watch later shelf and Continue watching on Home.
- Signing out or switching accounts clears the local mirror of the previous account (history, likes, subs, queued changes). Downloads are kept.
