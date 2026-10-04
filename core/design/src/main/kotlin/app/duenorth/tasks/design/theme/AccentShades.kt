package app.duenorth.tasks.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

/**
 * Per-list shades of the app accent (data-model.md, "List shades").
 *
 * Step -3..-1 mixes the accent with white, +1..+3 with black, 20% per step; 0 is the accent.
 * Shades are flat colors, never gradients. Caption text falls back toward the accent until it
 * meets [Contrast.MIN_TEXT] on the theme background.
 */
object AccentShades {
    const val LIGHTEST = -3
    const val DARKEST = 3
    val steps: IntRange = LIGHTEST..DARKEST

    private const val MIX_PER_STEP = 0.2f

    fun mix(base: Color, step: Int): Color {
        require(step in steps) { "Shade step $step is outside $steps" }
        val toward = if (step < 0) Color.White else Color.Black
        return lerp(base, toward, abs(step) * MIX_PER_STEP)
    }

    fun colors(accent: Accent, step: Int, colors: MetroColors): AccentColors {
        val fill = mix(accent.fill(colors.isDark), step)
        return AccentColors(
            text = readableText(accent.text(colors.isDark), step, colors.background),
            fill = fill,
            onFill = Contrast.bestOn(fill)
        )
    }

    /** The shade's text color, or the nearest step toward the accent that meets 4.5:1. */
    fun readableText(base: Color, step: Int, background: Color): Color {
        var s = step
        while (true) {
            val candidate = mix(base, s)
            if (s == 0 || Contrast.ratio(candidate, background) >= Contrast.MIN_TEXT) return candidate
            s -= Integer.signum(s)
        }
    }
}
