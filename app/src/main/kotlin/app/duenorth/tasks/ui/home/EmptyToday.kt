package app.duenorth.tasks.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.theme.MetroTheme
import kotlinx.coroutines.launch

/**
 * What "today" shows when nothing is due (spec 005 US4): the check-north logo drawing itself,
 * large and centered, with "all clear." under it. It plays the launch splash's motion
 * (`splash_icon_animated.xml`) each time [arrivals] changes and on tap, and rests when
 * animations are off. Two strokes on a Canvas: nothing to load, so it costs cold start nothing.
 */
@Composable
internal fun EmptyToday(arrivals: () -> Int, modifier: Modifier = Modifier) {
    val animate = LocalAnimationsEnabled.current
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val play: () -> Unit = {
        scope.launch {
            if (!animate) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(LogoMotion.DURATION_MS, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(animate) { snapshotFlow(arrivals).collect { play() } }

    val color = MetroTheme.accent.fill
    BoxWithConstraints(modifier.testTag("empty today"), contentAlignment = Alignment.Center) {
        val logoHeight = minOf(LOGO_HEIGHT, maxHeight - TEXT_ROOM)
        Column(
            Modifier.clickable(interactionSource = null, indication = null, onClick = play)
                .semantics(mergeDescendants = true) {
                    role = Role.Image
                    onClick("replay") {
                        play()
                        true
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (logoHeight >= MIN_LOGO) {
                Canvas(Modifier.height(logoHeight).width(logoHeight * LogoMotion.ASPECT)) {
                    drawLogo(progress.value, color)
                }
            }
            MetroText(
                "all clear.",
                MetroTheme.typography.listName,
                Modifier.padding(top = 8.dp),
                maxLines = 1
            )
            MetroText(
                "nothing due today",
                MetroTheme.typography.caption,
                color = MetroTheme.colors.secondary,
                maxLines = 1
            )
        }
    }
}

/** The splash animation's timeline, in the launcher icon's 108-unit space. */
internal object LogoMotion {
    const val DURATION_MS = 700

    /** The crop around the mark: x 34..78, y 24..80. */
    const val LEFT = 34f
    const val TOP = 24f
    const val WIDTH = 44f
    const val HEIGHT = 56f
    const val ASPECT = WIDTH / HEIGHT
    const val STROKE = 5.5f

    /** How far below its resting place the arrowhead starts. */
    const val RISE = 30f
    const val RISE_MS = 600f

    /** Each trail frame: its offset below the tip, when it flashes, and how long it fades. */
    val trails = listOf(24f to 50f, 18f to 100f, 12f to 160f, 6f to 220f)
    const val TRAIL_FADE_MS = 300f
    const val TRAIL_ALPHA = 0.5f

    /** The check's short stroke ticks in once the head has passed it. */
    const val TICK_START_MS = 320f
    const val TICK_MS = 300f
}

private fun easeOut(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/** Draws "check north" at [progress] (0 = start of the splash, 1 = the launcher icon). */
internal fun DrawScope.drawLogo(progress: Float, color: Color) {
    val m = LogoMotion
    val scale = size.height / m.HEIGHT
    fun p(x: Float, y: Float) = Offset((x - m.LEFT) * scale, (y - m.TOP) * scale)
    val width = m.STROKE * scale
    val butt = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    val square = Stroke(width, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    fun chevron(dy: Float) = Path().apply {
        moveTo(p(46f, 44f + dy).x, p(46f, 44f + dy).y)
        lineTo(p(58f, 32f + dy).x, p(58f, 32f + dy).y)
        lineTo(p(70f, 44f + dy).x, p(70f, 44f + dy).y)
    }

    if (progress >= 1f) {
        // At rest: exactly the launcher icon, one path so the corner is a clean miter.
        val check = Path().apply {
            moveTo(p(38.06f, 56.06f).x, p(38.06f, 56.06f).y)
            lineTo(p(58f, 76f).x, p(58f, 76f).y)
            lineTo(p(58f, 32f).x, p(58f, 32f).y)
        }
        drawPath(check, color, style = butt)
        drawPath(chevron(0f), color, style = square)
        return
    }
    val ms = progress * m.DURATION_MS
    m.trails.forEach { (dy, at) ->
        val t = (ms - at) / m.TRAIL_FADE_MS
        if (t in 0f..1f) drawPath(chevron(dy), color, alpha = m.TRAIL_ALPHA * (1f - t), style = square)
    }
    val rise = m.RISE * (1f - easeOut(ms / m.RISE_MS))
    val tick = ((ms - m.TICK_START_MS) / m.TICK_MS).coerceIn(0f, 1f)
    if (tick > 0f) {
        val from = p(58f, 76f)
        val to = p(58f - 19.94f * easeOut(tick), 76f - 19.94f * easeOut(tick))
        drawLine(color, from, to, width, StrokeCap.Butt)
    }
    // The stem carries the check's pointed bottom from the first frame (the resting miter's
    // outline, cut along the short stroke's outer edge), so the point rides up with the arrow
    // instead of snapping in at rest. The short stroke later lands flush on that cut.
    val half = m.STROKE / 2f
    val cut = half * 1.4142135f
    val stem = Path().apply {
        moveTo(p(58f - half, 32f + rise).x, p(58f - half, 32f + rise).y)
        lineTo(p(58f + half, 32f + rise).x, p(58f + half, 32f + rise).y)
        lineTo(p(58f + half, 76f + half + cut).x, p(58f + half, 76f + half + cut).y)
        lineTo(p(58f - half, 76f - half + cut).x, p(58f - half, 76f - half + cut).y)
        close()
    }
    drawPath(stem, color)
    drawPath(chevron(rise), color, style = square)
}

private val LOGO_HEIGHT = 170.dp
private val MIN_LOGO = 64.dp

/** Room left under the logo for "all clear." and its caption. */
private val TEXT_ROOM = 72.dp
