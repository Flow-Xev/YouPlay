package app.youplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The four colour themes. All are dark; AMOLED turns the backgrounds pure black on top of any of them. */
enum class AccentTheme(
    val id: String,
    val label: String,
    val accent: Color,
    val onAccent: Color,
    val bg: Color,
    val surface: Color,
    val elev: Color,
    val outline: Color,
) {
    Red("red", "Red", Color(0xFFE50914), Color.White, Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF1C1C1C), Color(0xFF2A2A2A)),
    Mono("mono", "Monochrome", Color(0xFFF2F2F2), Color.Black, Color(0xFF0D0D0D), Color(0xFF181818), Color(0xFF242424), Color(0xFF333333)),
    Ocean("ocean", "Ocean", Color(0xFF3B82F6), Color.White, Color(0xFF070B14), Color(0xFF0E1522), Color(0xFF16202F), Color(0xFF223049)),
    Violet("violet", "Violet", Color(0xFFA78BFA), Color.Black, Color(0xFF0B0914), Color(0xFF14111F), Color(0xFF1D1930), Color(0xFF2E2848));

    companion object {
        fun of(id: String): AccentTheme = entries.firstOrNull { it.id == id } ?: Red
    }
}

private val accentState = mutableStateOf(AccentTheme.Red.accent)

/** The current accent colour. Reading it inside a composable recomposes when the theme changes. */
val YouPlayRed: Color get() = accentState.value

/** Call before setContent so the first frame already uses the saved accent. */
fun setAccent(theme: AccentTheme) { accentState.value = theme.accent }

private val OnDark = Color(0xFFF5F5F5)
private val Muted = Color(0xFFA3A3A3)

@Composable
fun YouPlayTheme(
    accent: String = "red",
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val t = AccentTheme.of(accent)
    SideEffect { accentState.value = t.accent }

    val bg = if (amoled) Color.Black else t.bg
    val surface = if (amoled) Color.Black else t.surface
    val elev = if (amoled) lerp(Color.Black, t.elev, 0.55f) else t.elev
    val outline = if (amoled) lerp(Color.Black, t.outline, 0.75f) else t.outline
    val tint = lerp(bg, t.accent, 0.28f)

    val colors = darkColorScheme(
        primary = t.accent, onPrimary = t.onAccent,
        primaryContainer = tint, onPrimaryContainer = OnDark,
        secondary = Muted, onSecondary = bg,
        secondaryContainer = tint, onSecondaryContainer = OnDark,
        background = bg, onBackground = OnDark,
        surface = surface, onSurface = OnDark,
        surfaceVariant = elev, onSurfaceVariant = Muted,
        surfaceTint = t.accent,
        surfaceContainerLowest = bg, surfaceContainerLow = bg,
        surfaceContainer = surface, surfaceContainerHigh = elev, surfaceContainerHighest = elev,
        outline = outline, outlineVariant = outline,
        error = Color(0xFFE5484D),
    )
    MaterialTheme(colorScheme = colors, typography = YouPlayTypography, content = content)
}
