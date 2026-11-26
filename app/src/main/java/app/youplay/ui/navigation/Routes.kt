package app.youplay.ui.navigation

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Shorts : Route("shorts")
    data object Search : Route("search")
    data object Library : Route("library")
    data object Subscriptions : Route("subscriptions")
    data object Playlist : Route("playlist/{playlistId}") {
        fun create(id: String) = "playlist/$id"
    }
    data object Account : Route("account")
    data object Settings : Route("settings")
    data object Channels : Route("channels")
    data object Login : Route("login")
    data object Downloads : Route("downloads")
    data object History : Route("history")
    data object ShortsPlayer : Route("shorts_player/{videoId}") {
        fun create(videoId: String) = "shorts_player/$videoId"
    }
    data object Watch : Route("watch/{videoId}") {
        fun create(videoId: String) = "watch/$videoId"
    }
    data object ChannelDetail : Route("channel/{channelId}") {
        fun create(channelId: String) = "channel/$channelId"
    }
}

val bottomDestinations = listOf(
    Route.Home,
    Route.Shorts,
    Route.Subscriptions,
    Route.Library,
)
