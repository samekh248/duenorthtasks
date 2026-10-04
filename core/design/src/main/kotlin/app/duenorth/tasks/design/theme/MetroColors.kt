package app.duenorth.tasks.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Theme colors from research R5. Flat fills only: no gradients, no elevation tints. */
@Immutable
data class MetroColors(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    /** Secondary text: details previews, captions, inactive pivot headers. */
    val secondary: Color,
    /** Application Bar, dialog band and context menu fill. */
    val chrome: Color,
    /** Overdue captions. */
    val overdue: Color,
    /** Outlines of text boxes and unchecked boxes when not focused. */
    val outline: Color,
    /** Dims the page behind dialogs. */
    val scrim: Color
) {
    companion object {
        val Light = MetroColors(
            isDark = false,
            background = Color(0xFFFFFFFF),
            foreground = Color(0xFF111111),
            secondary = Color(0xFF5C5C5C),
            chrome = Color(0xFFE5E5E5),
            overdue = Color(0xFFC40000),
            outline = Color(0xFF8C8C8C),
            scrim = Color(0x99FFFFFF)
        )

        val Dark = MetroColors(
            isDark = true,
            background = Color(0xFF000000),
            foreground = Color(0xFFFFFFFF),
            secondary = Color(0xFFA6A6A6),
            chrome = Color(0xFF1F1F1F),
            overdue = Color(0xFFFF6B6B),
            outline = Color(0xFF8C8C8C),
            scrim = Color(0x99000000)
        )
    }
}

/**
 * One accent as drawn in one theme. [text] meets 4.5:1 on the background for 13sp captions;
 * [fill] colors tiles, checked boxes and swatches; [onFill] is white or black, whichever reads
 * better on [fill].
 */
@Immutable
data class AccentColors(val text: Color, val fill: Color, val onFill: Color)

/**
 * The 22 accent choices: the 20 Windows Phone 8.1 accents plus light orange and coral
 * (research R5, "Accent choices"). Light-theme values are darkened and dark-theme values
 * brightened so captions stay readable. Light orange and coral keep their bright color as a fill
 * on white so they still look light.
 */
enum class Accent(
    val displayName: String,
    private val lightText: Long,
    private val darkValue: Long,
    private val lightFill: Long = lightText
) {
    Magenta("magenta", 0xFFB0005E, 0xFFF0389A),
    LightOrange("light orange", 0xFFA85400, 0xFFFFA552, lightFill = 0xFFFFA552),
    Coral("coral", 0xFFB8402A, 0xFFFF8A6E, lightFill = 0xFFFF8A6E),
    Lime("lime", 0xFF5A6E00, 0xFFA4C400),
    Green("green", 0xFF3C7A0E, 0xFF60A917),
    Emerald("emerald", 0xFF007A00, 0xFF2DB52D),
    Teal("teal", 0xFF00787A, 0xFF00ABA9),
    Cyan("cyan", 0xFF0B6FA4, 0xFF1BA1E2),
    Cobalt("cobalt", 0xFF0050EF, 0xFF4D8BFF),
    Indigo("indigo", 0xFF6A00FF, 0xFF9A5CFF),
    Violet("violet", 0xFF8A00D4, 0xFFC25CFF),
    Pink("pink", 0xFFB0308F, 0xFFF472D0),
    Crimson("crimson", 0xFFA20025, 0xFFFF5C7A),
    Red("red", 0xFFC41100, 0xFFFF5C4D),
    Orange("orange", 0xFFB34A00, 0xFFFA6800),
    Amber("amber", 0xFF8F5F00, 0xFFF0A30A),
    Yellow("yellow", 0xFF7A6A00, 0xFFE3C800),
    Brown("brown", 0xFF825A2C, 0xFFC8955A),
    Olive("olive", 0xFF566B4F, 0xFF93AD89),
    Steel("steel", 0xFF576778, 0xFF8EA2B8),
    Mauve("mauve", 0xFF76608A, 0xFFA891BE),
    Taupe("taupe", 0xFF6E6240, 0xFFB5A577);

    fun text(dark: Boolean): Color = Color(if (dark) darkValue else lightText)

    fun fill(dark: Boolean): Color = Color(if (dark) darkValue else lightFill)

    fun colors(dark: Boolean): AccentColors {
        val fill = fill(dark)
        return AccentColors(text = text(dark), fill = fill, onFill = Contrast.bestOn(fill))
    }

    companion object {
        val Default = Magenta
    }
}
