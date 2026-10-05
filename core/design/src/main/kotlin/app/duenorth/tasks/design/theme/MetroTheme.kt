package app.duenorth.tasks.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.motion.rememberAnimationsEnabled

val LocalMetroColors = staticCompositionLocalOf { MetroColors.Light }

/** The accent in effect here: the app accent, or a list's shade of it inside that list. */
val LocalAccent = staticCompositionLocalOf { Accent.Default.colors(dark = false) }

/** The app accent the person chose in settings; list shades are computed from it. */
val LocalAppAccent = staticCompositionLocalOf { Accent.Default }

/**
 * Each list's shade step (-3..+3) by local list id; a list missing from the map uses the app
 * accent. Dynamic, so a shade change only recomposes the tiles and captions that read it.
 */
val LocalListShades = compositionLocalOf { emptyMap<String, Int>() }

val LocalMetroTypography = staticCompositionLocalOf { MetroTypography.Default }

/**
 * Root of every screen. Follows the phone's light/dark setting unless [darkTheme] is given
 * (the manual override from settings). [listShades] maps list ids to their shade steps.
 */
@Composable
fun MetroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Default,
    typography: MetroTypography = MetroTypography.Default,
    listShades: Map<String, Int> = emptyMap(),
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
        LocalAppAccent provides accent,
        LocalListShades provides listShades,
        LocalMetroTypography provides typography,
        LocalTextSelectionColors provides selection,
        LocalAnimationsEnabled provides rememberAnimationsEnabled(),
        content = content
    )
}

/** The colors of list [listId]: its shade of the app accent, or the accent itself by default. */
@Composable
fun listAccent(listId: String): AccentColors = shadeAccent(LocalListShades.current[listId] ?: 0)

/** The app accent at shade [step] for the current theme, with readable caption text. */
@Composable
fun shadeAccent(step: Int): AccentColors {
    val accent = LocalAppAccent.current
    val colors = LocalMetroColors.current
    return remember(accent, step, colors) { AccentShades.colors(accent, step, colors) }
}

/** Everything inside [content] (tiles, check boxes, captions) uses list [listId]'s shade. */
@Composable
fun ListAccent(listId: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides listAccent(listId), content = content)
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
