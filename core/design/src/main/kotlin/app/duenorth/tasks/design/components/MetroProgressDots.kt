package app.duenorth.tasks.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.theme.MetroTheme
import kotlin.math.pow

private const val DOTS = 5
private const val CYCLE_MS = 2600
private const val DOT_SPACING = 0.035f

/**
 * WP8.1 indeterminate progress: five accent dots that rush in from the left, bunch up and slow
 * down in the middle, then rush off to the right. This is the only sync indicator the app shows
 * (constitution Principle II). The animation runs in the draw phase only.
 */
@Composable
fun MetroProgressDots(modifier: Modifier = Modifier, color: Color = MetroTheme.accent.fill) {
    val label = Modifier.semantics { contentDescription = "syncing" }
    if (!LocalAnimationsEnabled.current) {
        // Reduced motion: hold the dots still in the middle.
        Canvas(modifier.fillMaxWidth().height(4.dp).then(label)) {
            for (i in 0 until DOTS) drawDot(color, 0.5f + (2 - i) * DOT_SPACING)
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "progress dots")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )
    Canvas(modifier.fillMaxWidth().height(4.dp).then(label)) {
        for (i in 0 until DOTS) {
            val t = time.value * 1.25f - i * 0.06f
            if (t in 0f..1f) drawDot(color, dotPosition(t))
        }
    }
}

/** Fast-slow-fast: a cubic that flattens in the middle, blended with linear so dots never stop. */
internal fun dotPosition(t: Float): Float {
    val cubic = 4f * (t - 0.5f).pow(3) + 0.5f
    return 0.7f * cubic + 0.3f * t
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDot(color: Color, fraction: Float) {
    val r = size.height / 2
    drawCircle(color, radius = r, center = Offset(fraction * size.width, r))
}
