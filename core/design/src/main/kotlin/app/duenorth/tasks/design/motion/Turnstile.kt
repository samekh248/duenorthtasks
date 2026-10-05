package app.duenorth.tasks.design.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/** Length of one turnstile half (out or in). */
const val TURNSTILE_MS = 250

private const val TURNSTILE_ANGLE = 80f

/** WP8.1's ease for page transitions: quick start, soft landing. */
val MetroEasing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

/** Leaving pages accelerate away. */
private val TurnstileOutEasing = CubicBezierEasing(0.6f, 0f, 0.9f, 0.4f)

/**
 * Turnstile page transition (research R4): the page swings around its left edge like a door.
 * Entering pages swing in from behind, leaving pages swing out the other way.
 *
 * Use inside an animated destination (for example a Navigation Compose `composable {}` block):
 * `Modifier.turnstile(this)`, with the NavHost's exit transition keeping the old page on screen for
 * [TURNSTILE_MS]; the new page swings in after that. Drawn with graphicsLayer only, so it never
 * relayouts.
 */
fun Modifier.turnstile(scope: AnimatedVisibilityScope): Modifier = this.composed {
    val angle by scope.transition.animateFloat(
        // The old page swings out first, then the new one swings in, as on the phone.
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(TURNSTILE_MS, delayMillis = TURNSTILE_MS, easing = MetroEasing)
            } else {
                tween(TURNSTILE_MS, easing = TurnstileOutEasing)
            }
        },
        label = "turnstile"
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> -TURNSTILE_ANGLE
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> TURNSTILE_ANGLE
        }
    }
    Modifier.graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = 12 * density
        rotationY = angle
        alpha = if (abs(angle) >= TURNSTILE_ANGLE) 0f else 1f
    }
}
