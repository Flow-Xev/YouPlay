package app.youplay.data.remote

/**
 * What a request is *for*. Each purpose has its own ordered client chain: the first client is tried first and
 * the rest are fallbacks. Different InnerTube clients are good at different things:
 *
 *  - BROWSE / SEARCH / NEXT / COMMENTS: the WEB renderers are what our parsers (JsonWalk) understand. Fallback: MWEB,
 *    which returns the same renderer family from a different client identity (works when WEB is throttled / A-B'd).
 *  - PLAYBACK / SHORTS / DOWNLOAD: clients that return *direct* stream URLs (no signature cipher, no n-throttling,
 *    no PO token) come first. ANDROID_VR and IOS are the most dependable; TV / embedded clients are next.
 *
 * Client versions live here only, so refreshing them when YouTube retires one is a one-line change.
 */
enum class Purpose { BROWSE, SEARCH, NEXT, COMMENTS, PLAYBACK, SHORTS, DOWNLOAD }

data class ClientProfile(
    val name: String,
    val clientNameId: Int,
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val web: Boolean = false,          // sends Origin/Referer, needs a browser-like UA
    val embed: Boolean = false,        // adds context.thirdParty.embedUrl
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val platform: String? = null,
    val clientScreen: String? = null,
    val contextUserAgent: Boolean = false, // put the UA into context.client.userAgent too (web clients)
)

object InnerTubeClients {
    private const val CHROME_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val CHROME_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val WEB_VERSION = "2.20251020.01.00"

    val WEB = ClientProfile(
        "WEB", 1, "WEB", WEB_VERSION, CHROME_DESKTOP, web = true, platform = "DESKTOP", contextUserAgent = true,
    )
    val MWEB = ClientProfile(
        "MWEB", 2, "MWEB", WEB_VERSION, CHROME_MOBILE, web = true, platform = "MOBILE", contextUserAgent = true,
    )
    val WEB_EMBEDDED = ClientProfile(
        "WEB_EMBEDDED_PLAYER", 56, "WEB_EMBEDDED_PLAYER", "1.20251020.01.00", CHROME_DESKTOP,
        web = true, embed = true, platform = "DESKTOP", contextUserAgent = true,
    )
    val ANDROID_VR = ClientProfile(
        "ANDROID_VR", 28, "ANDROID_VR", "1.60.19",
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        deviceMake = "Oculus", deviceModel = "Quest 3", osName = "Android", osVersion = "12L", androidSdkVersion = 32,
    )
    val IOS = ClientProfile(
        "IOS", 5, "IOS", "20.03.02", "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
        deviceMake = "Apple", deviceModel = "iPhone16,2", osName = "iPhone", osVersion = "18.2.1.22C161",
    )
    val ANDROID = ClientProfile(
        "ANDROID", 3, "ANDROID", "20.10.38", "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
        osName = "Android", osVersion = "14", androidSdkVersion = 34,
    )
    val TV = ClientProfile(
        "TVHTML5", 7, "TVHTML5", "7.20250219.14.00", "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        platform = "TV", clientScreen = "WATCH",
    )
    val TV_EMBEDDED = ClientProfile(
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER", 85, "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0",
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version", embed = true, platform = "TV", clientScreen = "EMBED",
    )

    val all: List<ClientProfile> = listOf(WEB, MWEB, WEB_EMBEDDED, ANDROID_VR, IOS, ANDROID, TV, TV_EMBEDDED)

    /** Ordered clients for [purpose]; index 0 is the primary, the rest are fallbacks. */
    fun chain(purpose: Purpose): List<ClientProfile> = when (purpose) {
        Purpose.BROWSE, Purpose.SEARCH, Purpose.NEXT, Purpose.COMMENTS -> listOf(WEB, MWEB)
        Purpose.PLAYBACK, Purpose.DOWNLOAD -> listOf(ANDROID_VR, IOS, TV_EMBEDDED, TV, MWEB, ANDROID, WEB_EMBEDDED)
        // Shorts want a fast muxed progressive stream: IOS / VR return it without any token.
        Purpose.SHORTS -> listOf(IOS, ANDROID_VR, TV_EMBEDDED, MWEB, TV, ANDROID, WEB_EMBEDDED)
    }

    /** User-Agent that matches the `c=` query parameter of a googlevideo URL (a mismatch is answered with HTTP 403). */
    fun userAgentForParam(c: String?): String {
        if (c == null) return WEB.userAgent
        all.firstOrNull { it.clientName.equals(c, ignoreCase = true) }?.let { return it.userAgent }
        return when {
            c.contains("ANDROID_VR", true) -> ANDROID_VR.userAgent
            c.contains("IOS", true) -> IOS.userAgent
            c.contains("ANDROID", true) -> ANDROID.userAgent
            c.contains("TVHTML5", true) -> TV.userAgent
            c.contains("MWEB", true) -> MWEB.userAgent
            else -> WEB.userAgent
        }
    }
}
