package app.duenorth.tasks.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.motion.rememberAnimationsEnabled

val LocalMetroColors = staticCompositionLocalOf { MetroColors.Light }

/** The accent in effect here: the app accent, or a list's shade of it inside that list. */
val LocalAccent = staticCompositionLocalOf { Accent.Default.colors(dark = false) }

val LocalMetroTypography = staticCompositionLocalOf { MetroTypography.Default }

/**
 * Root of every screen. Follows the phone's light/dark setting unless [darkTheme] is given
 * (the manual override from settings).
 */
@Composable
fun MetroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Default,
    typography: MetroTypography = MetroTypography.Default,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) MetroColors.Dark else MetroColors.Light
    val accentColors = remember(accent, darkTheme) { accent.colors(darkTheme) }
    val selection = remember(accentColors) {
        TextSelectionColors(handleColor = accentColors.fill, backgroundColor = accentColors.fill.copy(alpha = 0.4f))
    }
    CompositionLocalProvider(
        LocalMetroColors provides colors,
        LocalAccent provides accentColors,
        LocalMetroTypography provides typography,
        LocalTextSelectionColors provides selection,
        LocalAnimationsEnabled provides rememberAnimationsEnabled(),
        content = content
    )
}

object MetroTheme {
    val colors: MetroColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMetroColors.current

    val accent: AccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current

    val typography: MetroTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMetroTypography.current
}

/** Layout constants from constitution Principle I. */
object MetroDimens {
    /** Left gutter that text and controls align to. */
    val Gutter = 12.dp

    /** Base grid. */
    val Grid = 24.dp

    /** Minimum touch target. */
    val TouchTarget = 48.dp
}
