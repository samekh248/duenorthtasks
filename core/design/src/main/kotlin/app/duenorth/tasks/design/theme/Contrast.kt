package app.duenorth.tasks.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG 2 contrast helpers used to keep captions at 4.5:1 for every accent and shade. */
object Contrast {
    const val MIN_TEXT = 4.5f

    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** White or black, whichever contrasts more with [background]. */
    fun bestOn(background: Color): Color =
        if (ratio(Color.White, background) >= ratio(Color.Black, background)) Color.White else Color.Black
}
