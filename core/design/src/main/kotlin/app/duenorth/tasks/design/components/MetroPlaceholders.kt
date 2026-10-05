package app.duenorth.tasks.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * Task-shaped placeholders shown while rows load, sized like real rows so nothing jumps when
 * they arrive (FR-009). They pulse gently unless animations are off.
 */
@Composable
fun MetroTaskPlaceholders(modifier: Modifier = Modifier, count: Int = 4) {
    val color = MetroTheme.colors.chrome
    val pulse = if (LocalAnimationsEnabled.current) {
        rememberInfiniteTransition(label = "placeholder").animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulse"
        )
    } else {
        null
    }
    Column(
        modifier.graphicsLayer { alpha = pulse?.value ?: 1f },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(start = 12.dp, top = 12.dp)) {
                    Box(Modifier.size(24.dp).background(color))
                }
                Column(Modifier.padding(start = 16.dp, top = 8.dp, end = MetroDimens.Gutter)) {
                    Box(
                        Modifier.fillMaxWidth(if (i % 2 == 0) 0.8f else 0.6f).height(20.dp).background(color)
                    )
                    Box(
                        Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(12.dp).background(color)
                    )
                }
            }
        }
    }
}
