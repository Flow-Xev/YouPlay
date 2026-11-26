package app.youplay.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Status bar helpers. The app already draws edge to edge (MainActivity.enableEdgeToEdge) with a
 * transparent bar, so a screen only has to say what the bar's icons should look like, and whether
 * the bar is visible at all. Whatever was set before is put back when the screen leaves.
 */

/** Height of the status bar in dp (0 while it is hidden). */
@Composable
fun statusBarHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.statusBars.getTop(density).toDp() }
}

/**
 * Sets the status bar icon style for as long as this composable is in the composition.
 *
 * @param lightIcons true = white icons (use over video / dark screens), false = dark icons (light screens)
 * @param hidden true = hide the bar; a swipe from the top edge shows it again for a moment
 */
@Composable
fun StatusBarStyle(lightIcons: Boolean = true, hidden: Boolean = false) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons, hidden) {
        val window = view.context.findHostActivity()?.window
        if (window == null) return@DisposableEffect onDispose { }
        val ctl = WindowCompat.getInsetsController(window, view)
        val wasLight = ctl.isAppearanceLightStatusBars
        ctl.isAppearanceLightStatusBars = !lightIcons
        if (hidden) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.statusBars())
        } else ctl.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            ctl.isAppearanceLightStatusBars = wasLight
            ctl.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

/**
 * Soft dark fade behind the status bar so its icons stay readable over bright video or artwork.
 * Place it at the top of a full-screen Box, above the content and below the toolbar.
 */
@Composable
fun StatusBarScrim(strength: Float = 0.4f, extra: Dp = 12.dp, modifier: Modifier = Modifier) {
    val h = statusBarHeight()
    if (h > 0.dp) Box(
        modifier.fillMaxWidth().height(h + extra)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(strength), Color.Transparent))),
    )
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
