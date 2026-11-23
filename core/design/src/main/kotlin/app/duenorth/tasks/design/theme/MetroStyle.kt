package app.duenorth.tasks.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look the person picks under settings, theme: the same five styles as Due North Budget, so the two apps can
 * be set alike. Metro is the flat WP8.1 look and the default (constitution Principle I).
 * Tile relief keeps Metro's type, square corners, flat accent fills, round app-bar buttons and grid, and only adds
 * light and shade. Frost (inspired by Windows 11's Fluent look) puts rounded layer cards edged by a hairline on a
 * tinted page, under an acrylic app bar that floats above it. Slate (inspired by Windows 10 Mobile) writes in sentence
 * case under a flat 48dp command bar of bare glyphs, with grey filled buttons and outlined text boxes on pure white
 * or black. Neither name is a Microsoft product name, so neither the picker nor the store listing uses one.
 * Tonal is half Metro and half Material 3, as Due North Budget draws it: Metro's Selawik type and lowercase labels
 * with small-caps headings, on rounded tonal cards tinted with the accent, with pill buttons and chips and round
 * tonal containers behind the app bar's glyphs.
 */
enum class MetroStyle(
    val label: String,
    /** List tiles and panels lift off the page (tile relief). */
    val panels: Boolean = false,
    /** Add is the one accent button on the app bar (tile relief, frost). */
    val filledAdd: Boolean = false,
    /** Surfaces are rounded layer cards with a hairline edge instead of light and shade; bars float (frost). */
    val layers: Boolean = false,
    /** Sentence case, a flat command bar, filled buttons (slate). */
    val slate: Boolean = false,
    /** Tonal: frost's layer cards, but tinted with the accent, larger corners, no hairline, and pills (tonal). */
    val tonal: Boolean = false
) {
    Metro("metro"),
    TileRelief("tile relief", panels = true, filledAdd = true),
    Frost("frost", panels = true, filledAdd = true, layers = true),
    Slate("slate", slate = true),
    Tonal("tonal", panels = true, layers = true, tonal = true);

    /** Whether surfaces are drawn by [MetroSurface] (relief or layers) rather than Metro's outlines. */
    val relief: Boolean get() = this != Metro && !slate

    /** The relief of a control at rest: raised, or under frost a field (a layer card with a line under it). */
    val control: Relief get() = if (layers) Relief.Inset else Relief.Raised

    companion object {
        val Default = Metro
    }
}

/** Raised stands off the page; inset is pressed into it. */
enum class Relief { Raised, Inset }

/**
 * How a relief style lights its surfaces: a [highlight] thrown up and left and a [shade] thrown down and right,
 * [depth] apart and softened by [blur]. Both are translucent, so the same light works on the page, on the app
 * bar's chrome and on an accent fill. Small things (buttons, boxes) use half of each.
 *
 * Under frost a surface is instead a [layer] card on the [page], edged by a [stroke] hairline, and what floats
 * (the app bar) is [acrylic] with the [shade] as its drop shadow. A field is a card with an [underline].
 */
@Immutable
data class MetroSurface(
    val style: MetroStyle,
    val highlight: Color,
    val shade: Color,
    val depth: Dp,
    val blur: Dp,
    val page: Color = Color.Transparent,
    val layer: Color = Color.Transparent,
    val stroke: Color = Color.Transparent,
    val acrylic: Color = Color.Transparent,
    val underline: Color = Color.Transparent
) {
    companion object {
        val Flat = MetroSurface(MetroStyle.Metro, Color.Transparent, Color.Transparent, 0.dp, 0.dp)

        fun of(style: MetroStyle, dark: Boolean, accent: Accent = Accent.Default): MetroSurface = when {
            !style.relief -> Flat.copy(style = style)
            style.tonal -> MetroSurface(
                style,
                highlight = Color.Transparent,
                shade = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.12f),
                depth = 6.dp,
                blur = 20.dp,
                page = TonalTones.page(accent, dark),
                // Rows group on a tonal card a step deeper into the accent than the page, with no edge.
                layer = TonalTones.layer(accent, dark),
                stroke = Color.Transparent,
                acrylic = TonalTones.bar(accent, dark),
                underline = Color.Transparent
            )
            style.layers -> MetroSurface(
                style,
                highlight = Color.Transparent,
                shade = if (dark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.16f),
                depth = 8.dp,
                blur = 24.dp,
                page = StylePalettes.colors(style, dark).background,
                layer = if (dark) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.72f),
                stroke = if (dark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.07f),
                // Acrylic without a backdrop blur: opaque enough that rows passing under it do not read through.
                acrylic = if (dark) Color(0xF02C2C30) else Color(0xF2F9F9F9),
                underline = StylePalettes.colors(style, dark).secondary
            )
            else -> MetroSurface(
                style,
                highlight = if (dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.95f),
                shade = if (dark) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.17f),
                depth = 5.dp,
                blur = 14.dp
            )
        }
    }
}

/**
 * The page colors of each style. Relief needs a page that is neither white nor black, so tile relief and frost put
 * Metro's ink on a light or dark grey; slate keeps the white or black page under its own chrome.
 * Metro's own are [MetroColors.Light] and [MetroColors.Dark].
 */
object StylePalettes {
    fun colors(style: MetroStyle, dark: Boolean, accent: Accent = Accent.Default): MetroColors {
        val metro = if (dark) MetroColors.Dark else MetroColors.Light
        if (style.tonal) {
            val page = TonalTones.page(accent, dark)
            val card = TonalTones.layer(accent, dark)
            return metro.copy(
                background = page,
                chrome = TonalTones.bar(accent, dark),
                // Grey ink pulled a little toward the accent, as a tonal palette's variant ink.
                secondary = Contrast.ensure(
                    lerp(Color(if (dark) 0xFFA29A9F else 0xFF57505A), accent.fill(dark), 0.12f),
                    card
                ),
                outline = lerp(metro.outline, accent.fill(dark), 0.2f),
                overdue = Contrast.ensure(metro.overdue, card),
                scrim = page.copy(alpha = metro.scrim.alpha)
            )
        }
        val (background, chrome) = when (style) {
            MetroStyle.Metro -> return metro
            // ChromeMedium under the bar, BaseMedium for secondary ink.
            MetroStyle.Slate -> return metro.copy(
                chrome = Color(if (dark) 0xFF1F1F1F else 0xFFF2F2F2),
                secondary = Color(if (dark) 0xFF999999 else 0xFF595959),
                outline = Color(if (dark) 0xFF666666 else 0xFF999999)
            )
            MetroStyle.Tonal -> error("tonal is tinted by the accent, above")
            MetroStyle.TileRelief -> if (dark) 0xFF101010 to 0xFF1F1F1F else 0xFFEEEEEE to 0xFFE3E3E3
            MetroStyle.Frost -> if (dark) 0xFF1E1D22 to 0xFF2C2C30 else 0xFFF1EFF3 to 0xFFF9F9F9
        }
        val page = Color(background)
        val secondary = when {
            style.layers -> if (dark) 0xFFA9A6B0 else 0xFF4E4B55
            dark -> 0xFF9E9E9E
            else -> 0xFF505050
        }
        return metro.copy(
            background = page,
            chrome = Color(chrome),
            secondary = Contrast.ensure(Color(secondary), page),
            overdue = Contrast.ensure(metro.overdue, page),
            scrim = page.copy(alpha = metro.scrim.alpha)
        )
    }
}

/**
 * The tonal style's grounds, each a neutral moved toward the accent's swatch: the page, the deeper card rows group
 * on, and the app bar. On a light page the card is the deeper tone; on a dark one, the lighter.
 */
object TonalTones {
    fun page(accent: Accent, dark: Boolean): Color =
        lerp(Color(if (dark) 0xFF121013 else 0xFFFFFBFC), accent.fill(dark), if (dark) 0.05f else 0.035f)

    fun layer(accent: Accent, dark: Boolean): Color =
        lerp(Color(if (dark) 0xFF201B1E else 0xFFFFFFFF), accent.fill(dark), if (dark) 0.11f else 0.09f)

    fun bar(accent: Accent, dark: Boolean): Color =
        lerp(Color(if (dark) 0xFF262024 else 0xFFFFFFFF), accent.fill(dark), if (dark) 0.16f else 0.14f)
}

/** A slate card or tile that groups rather than acts: a faint wash of the ink, with no edge. */
val MetroColors.slateCard: Color get() = foreground.copy(alpha = 0.06f)

/** Moves [text] toward black (on a light page) or white (on a dark one) until it reads at [Contrast.MIN_TEXT]. */
fun Contrast.ensure(text: Color, background: Color): Color {
    if (ratio(text, background) >= MIN_TEXT) return text
    val toward = bestOn(background)
    var step = 0.05f
    while (step < 1f) {
        val moved = lerp(text, toward, step)
        if (ratio(moved, background) >= MIN_TEXT) return moved
        step += 0.05f
    }
    return toward
}
