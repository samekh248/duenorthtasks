package app.duenorth.tasks.design.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STAGGER_MS = 30L
private const val MAX_STAGGERED = 10
private const val SLIDE_MS = 350

/**
 * Slide-in stagger: list items enter from the right one after another, ~30ms apart (research R4).
 * Pass [play] = false for items that should simply appear, for example rows scrolled into view
 * after the page has entered.
 */
fun Modifier.slideInStagger(index: Int, play: Boolean = true): Modifier = this.composed {
    val animate = play && LocalAnimationsEnabled.current
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(Unit) {
            delay(index.coerceAtMost(MAX_STAGGERED) * STAGGER_MS)
            progress.animateTo(1f, tween(SLIDE_MS, easing = MetroEasing))
        }
    }
    Modifier.graphicsLayer {
        val p = progress.value
        translationX = (1f - p) * 64.dp.toPx()
        alpha = p
    }
}
