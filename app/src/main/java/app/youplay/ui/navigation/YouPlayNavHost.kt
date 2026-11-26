package app.youplay.ui.navigation

import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.youplay.ui.player.FloatingMiniPlayer
import app.youplay.ui.player.PlayerHostViewModel
import app.youplay.ui.screens.channel.ChannelPageScreen
import app.youplay.ui.screens.channels.ChannelsScreen
import app.youplay.ui.screens.downloads.DownloadsScreen
import app.youplay.ui.screens.home.HomeScreen
import app.youplay.ui.screens.history.HistoryScreen
import app.youplay.ui.screens.login.LoginScreen
import app.youplay.ui.screens.playlist.PlaylistScreen
import app.youplay.ui.screens.search.SearchScreen
import app.youplay.ui.screens.settings.SettingsScreen
import app.youplay.ui.screens.shorts.ShortsScreen
import app.youplay.ui.screens.subscriptions.SubscriptionsScreen
import app.youplay.ui.screens.watch.WatchScreen
import app.youplay.ui.screens.you.YouScreen

private data class BottomItem(
    val route: Route,
    val label: String,
    val selected: ImageVector,
    val unselected: ImageVector,
)

@Composable
fun YouPlayNavHost(openVideoId: String? = null, onOpenVideoHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    // A tapped "new video" notification asks us to open that video.
    LaunchedEffect(openVideoId) {
        if (openVideoId != null) { navController.navigate(Route.Watch.create(openVideoId)); onOpenVideoHandled() }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val bottomItems = listOf(
        BottomItem(Route.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomItem(Route.Shorts, "Shorts", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
        BottomItem(Route.Subscriptions, "Subs", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
        BottomItem(Route.Library, "You", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    )

    val showBottomBar = bottomItems.any { it.route.path == currentRoute }
    val controller = hiltViewModel<PlayerHostViewModel>().controller
    val playing by controller.current.collectAsState()
    val inShorts = currentRoute == Route.Shorts.path || currentRoute == Route.ShortsPlayer.path
    val showMini = playing != null && currentRoute != Route.Watch.path && !inShorts
    LaunchedEffect(inShorts) { if (inShorts) controller.pause() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(
                navController = navController, startDestination = Route.Home.path,
                enterTransition = { fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 1.02f, animationSpec = tween(240)) },
                popExitTransition = { fadeOut(tween(140)) + scaleOut(targetScale = 0.97f, animationSpec = tween(200)) },
            ) {
                composable(Route.Home.path) {
                    HomeScreen(
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenShort = { navController.navigate(Route.ShortsPlayer.create(it)) },
                        onSearch = { navController.navigate(Route.Search.path) },
                        onSignIn = { navController.navigate(Route.Login.path) },
                    )
                }
                composable(Route.Shorts.path) {
                    ShortsScreen()
                }
                composable(Route.Search.path) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenChannel = { navController.navigate(Route.ChannelDetail.create(it)) },
                        onOpenShort = { navController.navigate(Route.ShortsPlayer.create(it)) },
                        onOpenPlaylist = { navController.navigate(Route.Playlist.create(it)) },
                    )
                }
                composable(Route.Subscriptions.path) {
                    SubscriptionsScreen(
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenShort = { navController.navigate(Route.ShortsPlayer.create(it)) },
                        onOpenChannel = { navController.navigate(Route.ChannelDetail.create(it)) },
                        onOpenChannels = { navController.navigate(Route.Channels.path) },
                        onSignIn = { navController.navigate(Route.Login.path) },
                        onSearch = { navController.navigate(Route.Search.path) },
                    )
                }
                composable(
                    route = Route.Playlist.path,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                ) {
                    PlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                    )
                }
                composable(Route.Library.path) {
                    YouScreen(
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenChannel = { navController.navigate(Route.ChannelDetail.create(it)) },
                        onOpenPlaylist = { navController.navigate(Route.Playlist.create(it)) },
                        onOpenDownloads = { navController.navigate(Route.Downloads.path) },
                        onOpenHistory = { navController.navigate(Route.History.path) },
                        onOpenSettings = { navController.navigate(Route.Settings.path) },
                        onOpenChannels = { navController.navigate(Route.Channels.path) },
                        onSignIn = { navController.navigate(Route.Login.path) },
                        onSearch = { navController.navigate(Route.Search.path) },
                    )
                }
                composable(Route.History.path) {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                    )
                }
                composable(Route.Downloads.path) {
                    DownloadsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                    )
                }
                composable(Route.Login.path) {
                    LoginScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
                }
                composable(
                    route = Route.ShortsPlayer.path,
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                ) {
                    ShortsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Settings.path) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDownloads = { navController.navigate(Route.Downloads.path) },
                        onSignIn = { navController.navigate(Route.Login.path) },
                    )
                }
                composable(Route.Channels.path) {
                    ChannelsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenChannel = { navController.navigate(Route.ChannelDetail.create(it)) },
                    )
                }
                composable(
                    route = Route.ChannelDetail.path,
                    arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
                ) {
                    ChannelPageScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { navController.navigate(Route.Watch.create(it)) },
                        onOpenShort = { navController.navigate(Route.ShortsPlayer.create(it)) },
                        onOpenPlaylist = { navController.navigate(Route.Playlist.create(it)) },
                    )
                }
                composable(
                    route = Route.Watch.path,
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                    // WatchScreen animates itself (full <-> mini), so the route transition is only a quick cross-fade.
                    enterTransition = { fadeIn(tween(80)) },
                    popExitTransition = { ExitTransition.None },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(160)) },
                ) {
                    WatchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVideo = { id ->
                            navController.navigate(Route.Watch.create(id)) {
                                popUpTo(Route.Watch.path) { inclusive = true }
                            }
                        },
                        onOpenChannel = { navController.navigate(Route.ChannelDetail.create(it)) },
                        onOpenShort = { navController.navigate(Route.ShortsPlayer.create(it)) },
                    )
                }
            }
                MiniHost(showMini) {
                    FloatingMiniPlayer(controller, onExpand = { navController.navigate(Route.Watch.create(it)) })
                }
            }
            if (showBottomBar) {
                ThinBottomBar(
                    items = bottomItems,
                    currentRoute = currentRoute,
                    onSelect = { path ->
                        navController.navigate(path) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

/** 52dp bottom bar: icon in a sliding-colour pill, small label underneath, springy selection. */
@Composable
private fun ThinBottomBar(
    items: List<BottomItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.08f)))
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route.path
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "navScale")
                val tintC by androidx.compose.animation.animateColorAsState(
                    if (selected) Color.White else Color.White.copy(0.55f), tween(180), label = "navTint")
                val pillW by androidx.compose.animation.core.animateDpAsState(
                    if (selected) 52.dp else 28.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "navPillW")
                val pillC by androidx.compose.animation.animateColorAsState(
                    if (selected) app.youplay.ui.theme.YouPlayRed.copy(alpha = 0.28f) else Color.Transparent, tween(200), label = "navPill")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!selected) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(item.route.path)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.width(pillW).height(26.dp).background(pillC, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (selected) item.selected else item.unselected,
                            contentDescription = item.label,
                            tint = tintC,
                            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                    }
                    Text(
                        item.label, color = tintC, fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Top-level wrapper so AnimatedVisibility has no ColumnScope receiver ambiguity. */
@Composable
private fun MiniHost(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(70)),
        exit = fadeOut(tween(90)),
    ) { content() }
}

private val MINI_ORIGIN = TransformOrigin(0.92f, 0.98f)
