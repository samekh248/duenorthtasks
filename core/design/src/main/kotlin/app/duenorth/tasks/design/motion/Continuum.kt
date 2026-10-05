package app.duenorth.tasks.design.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val CONTINUUM_OUT_MS = 150

/**
 * Continuum (research R4): the tapped item's title lifts off toward the top of the screen, then the
 * next page's header lands where it went. Call [flyOut] with the tapped item's key, then navigate;
 * call [reset] when coming back.
 */
@Stable
class ContinuumState {
    internal val progress = Animatable(0f)

    var activeKey: Any? by mutableStateOf(null)
        private set

    suspend fun flyOut(key: Any, animate: Boolean = true) {
        activeKey = key
        progress.snapTo(0f)
        if (animate) progress.animateTo(1f, tween(CONTINUUM_OUT_MS, easing = MetroEasing)) else progress.snapTo(1f)
    }

    suspend fun reset() {
        progress.snapTo(0f)
        activeKey = null
    }
}

@Composable
fun rememberContinuumState(): ContinuumState = remember { ContinuumState() }

/** Marks the title of an item that can fly out; only the item whose key is active moves. */
fun Modifier.continuumSource(state: ContinuumState, key: Any): Modifier = this.graphicsLayer {
    if (state.activeKey == key) {
        val p = state.progress.value
        translationX = p * 32.dp.toPx()
        translationY = -p * 120.dp.toPx()
        rotationZ = -p * 4f
        alpha = 1f - p
    }
}

/** For the header of the page being opened: it drops in from where the item flew to. */
fun Modifier.continuumTarget(scope: AnimatedVisibilityScope): Modifier = this.composed {
    val p by scope.transition.animateFloat(
        transitionSpec = { tween(TURNSTILE_MS, delayMillis = TURNSTILE_MS, easing = MetroEasing) },
        label = "continuum"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    Modifier.graphicsLayer {
        translationX = (1f - p) * 32.dp.toPx()
        translationY = (1f - p) * 48.dp.toPx()
        rotationZ = (1f - p) * -4f
        alpha = p
    }
}
