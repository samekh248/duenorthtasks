package app.duenorth.tasks.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.MetroTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Counts taps on the about page's version number. [needed] taps in a row, each within [gapMs] of
 * the last, return true once and start the count again; a longer pause starts it over.
 */
internal class TapStreak(private val needed: Int = 7, private val gapMs: Long = 1_500) {
    private var count = 0
    private var last = Long.MIN_VALUE

    fun tap(nowMs: Long): Boolean {
        count = if (last != Long.MIN_VALUE && nowMs - last <= gapMs) count + 1 else 1
        last = nowMs
        if (count < needed) return false
        count = 0
        last = Long.MIN_VALUE
        return true
    }
}

/**
 * The bison easter egg: it walks in from the left, stops in the middle, turns its head to look at
 * you, looks ahead again and walks off to the right. It plays each time [runs] goes up. It is drawn
 * on a Canvas that only redraws (the clock is read in the draw phase, so nothing recomposes per
 * frame) and takes no touches, so the page under it stays usable. With animations off it stands
 * still in the middle, looking at you, for a moment instead.
 */
@Composable
internal fun BisonWalk(runs: Int, modifier: Modifier = Modifier, bleed: Dp = 0.dp) {
    if (runs == 0) return
    val animate = LocalAnimationsEnabled.current
    val clock = remember { Animatable(0f) }
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(runs) {
        showing = true
        if (animate) {
            clock.snapTo(0f)
            clock.animateTo(BisonTimeline.TOTAL_MS, tween(BisonTimeline.TOTAL_MS.toInt(), easing = LinearEasing))
        } else {
            clock.snapTo(BisonTimeline.LOOK_HOLD_START)
            delay(STILL_MS)
        }
        showing = false
    }
    if (showing) BisonCanvas({ clock.value }, modifier, bleed)
}

/** One bison frame at [timeMs] into the walk; [BisonWalk] and the screenshot tests both draw this. */
@Composable
internal fun BisonCanvas(timeMs: () -> Float, modifier: Modifier = Modifier, bleed: Dp = 0.dp) {
    val shapes = remember { BisonShapes() }
    val fill = MetroTheme.accent.fill
    val horn = MetroTheme.colors.foreground
    val colors = remember(fill, horn) {
        BisonColors(
            front = fill,
            rear = AccentShades.mix(fill, -2),
            far = AccentShades.mix(fill, 1),
            horn = horn
        )
    }
    Canvas(modifier.testTag("bison").semantics { contentDescription = "a bison wanders by" }) {
        val scale = size.height / BisonShapes.HEIGHT
        val bleedUnits = bleed.toPx() / scale
        val stage = size.width / scale + 2 * bleedUnits
        val pose = BisonTimeline.pose(timeMs(), stage)
        scale(scale, pivot = Offset.Zero) { drawBison(pose, shapes, colors, -bleedUnits) }
    }
}

/** Where the bison is and what it is doing at one moment. Units are the drawing's (see [BisonShapes]). */
internal data class BisonPose(
    /** Left edge of the bison, from the left edge of the stage. */
    val x: Float,
    /** Walk cycle angle: legs swing with sin(stride), and it only moves while the bison walks. */
    val stride: Float,
    /** 0 = head in profile, looking ahead; 1 = facing you. */
    val look: Float,
    /** Tail swish angle in degrees, while it stands. */
    val tail: Float
)

/** The walk's timeline. All times in milliseconds. */
internal object BisonTimeline {
    const val WALK_IN_MS = 2_600f
    const val SETTLE_MS = 250f
    const val TURN_MS = 320f
    const val LOOK_MS = 1_300f
    const val PAUSE_MS = 350f
    const val WALK_OFF_MS = 2_600f

    const val LOOK_START = WALK_IN_MS + SETTLE_MS
    const val LOOK_HOLD_START = LOOK_START + TURN_MS
    const val LOOK_BACK_START = LOOK_HOLD_START + LOOK_MS
    const val WALK_OFF_START = LOOK_BACK_START + TURN_MS + PAUSE_MS
    const val TOTAL_MS = WALK_OFF_START + WALK_OFF_MS

    /** Roughly how far one full walk cycle carries it, in drawing units. */
    private const val STRIDE = 70f
    private const val TAIL_SWISH = 9f

    /**
     * The pose at [ms] on a stage [stage] units wide. It starts just off the left edge, stops with
     * its body centered, and ends just off the right edge. Legs follow the distance walked, so they
     * slow with the body and stand straight when it stops.
     */
    fun pose(ms: Float, stage: Float): BisonPose {
        val w = BisonShapes.WIDTH
        val start = -w
        val middle = (stage - w) / 2
        val leg = middle - start
        // A whole number of half cycles per leg of the walk, so the legs are straight at the stop.
        val halfCycles = max(1, (leg / (STRIDE / 2)).roundToInt())
        val perUnit = (halfCycles * PI.toFloat()) / leg
        return when {
            ms < WALK_IN_MS -> {
                val walked = leg * easeOutSine(ms / WALK_IN_MS)
                BisonPose(start + walked, walked * perUnit, 0f, 0f)
            }
            ms < WALK_OFF_START -> {
                val look = when {
                    ms < LOOK_START -> 0f
                    ms < LOOK_HOLD_START -> easeInOut((ms - LOOK_START) / TURN_MS)
                    ms < LOOK_BACK_START -> 1f
                    else -> 1f - easeInOut((ms - LOOK_BACK_START) / TURN_MS)
                }
                val still = ms - WALK_IN_MS
                val tail = TAIL_SWISH * sin(still / 260f) * (1f - cos(min1(still / 400f) * PI.toFloat())) / 2
                BisonPose(middle, 0f, look, tail)
            }
            else -> {
                val walked = leg * easeInSine(min1((ms - WALK_OFF_START) / WALK_OFF_MS))
                BisonPose(middle + walked, walked * perUnit, 0f, 0f)
            }
        }
    }

    private fun min1(x: Float) = x.coerceIn(0f, 1f)

    private fun easeOutSine(x: Float) = sin(min1(x) * PI.toFloat() / 2)

    private fun easeInSine(x: Float) = 1f - cos(min1(x) * PI.toFloat() / 2)

    private fun easeInOut(x: Float) = (1f - cos(min1(x) * PI.toFloat())) / 2
}

internal class BisonColors(val front: Color, val rear: Color, val far: Color, val horn: Color)

/**
 * The bison's flat shapes, facing right, in a 200 x 120 box with the ground at y = 118. Built once
 * per composition from SVG path data (the same paths as the design mockup).
 */
internal class BisonShapes {
    val rear = path("M40 48C28 50 22 62 24 74C26 86 36 92 50 92L100 94L102 44C82 38 60 42 40 48Z")
    val mane = path(
        "M76 52C78 28 90 10 108 10C124 10 138 20 148 34L156 60C154 78 148 90 138 96L133 91L128 98" +
            "L122 92L116 98L110 92L104 97L98 91L90 94C80 84 74 66 76 52Z"
    )
    val headSide = path(
        "M146 32C160 32 170 42 176 56L186 78C189 88 183 94 175 94L167 95L163 108L157 99L152 106L148 95" +
            "C138 84 136 52 146 32Z"
    )
    val hornSide = path("M155 42C155 32 161 26 169 28C165 31 162 36 162 44Z")
    val headFront = path("M141 44C142 34 174 34 175 44L177 68C175 84 169 95 158 97C147 95 141 84 139 68Z")
    val beardFront = path("M147 92L151 110L155 102L158 114L161 102L165 110L169 92Z")
    val hornLeft = path("M143 48C133 47 128 39 130 29C133 35 137 39 145 41Z")
    val hornRight = path("M173 48C183 47 188 39 186 29C183 35 179 39 171 41Z")
    val tail = path("M27 54C21 60 19 70 20 80L23.5 80C23 71 25 63 30 58Z")
    val tuft = path("M21.5 76C26 80 28 86 26 92L17 92C15 86 17 80 21.5 76Z")
    val legs = LEGS.associate { it.hipY to leg(GROUND - it.hipY) }

    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

    /** A leg hanging from its hip at (0, 0), tapering to a hoof [length] below. */
    private fun leg(length: Float) = Path().apply {
        moveTo(-6f, 0f)
        lineTo(6f, 0f)
        lineTo(4.5f, length - 6f)
        lineTo(5.5f, length)
        lineTo(-5.5f, length)
        lineTo(-4.5f, length - 6f)
        close()
    }

    /** A leg's hip and where it is in the gait: diagonal pairs swing together. */
    internal class Leg(val hipX: Float, val hipY: Float, val near: Boolean, val offset: Float)

    companion object {
        const val WIDTH = 200f
        const val HEIGHT = 120f
        const val GROUND = 118f
        val LEGS = listOf(
            Leg(42f, 78f, near = false, offset = PI.toFloat()),
            Leg(118f, 82f, near = false, offset = 0f),
            Leg(52f, 78f, near = true, offset = 0f),
            Leg(128f, 82f, near = true, offset = PI.toFloat())
        )
        const val SWING = 18f
        const val HEAD_PIVOT_X = 158f
        const val HEAD_PINCH = 0.3f
        val Eye = Color(0xFF1F1F1F)
    }
}

/** Draws [pose] with the stage's left edge at [left] units. */
private fun DrawScope.drawBison(pose: BisonPose, s: BisonShapes, c: BisonColors, left: Float) {
    val bob = -1.4f * abs(sin(pose.stride))
    translate(left + pose.x, bob) {
        fun legs(near: Boolean) = BisonShapes.LEGS.forEach { leg ->
            if (leg.near != near) return@forEach
            val color = when {
                !near -> c.far
                leg.hipX < 100f -> c.rear
                else -> c.front
            }
            translate(leg.hipX, leg.hipY) {
                rotate(BisonShapes.SWING * sin(pose.stride + leg.offset), pivot = Offset.Zero) {
                    drawPath(s.legs.getValue(leg.hipY), color)
                }
            }
        }
        legs(near = false)
        rotate(pose.tail, pivot = Offset(28f, 56f)) {
            drawPath(s.tail, c.rear)
            drawPath(s.tuft, c.front)
        }
        drawPath(s.rear, c.rear)
        legs(near = true)
        drawPath(s.mane, c.front)
        drawHead(pose, s, c)
    }
}

/**
 * The head turns by pinching the profile thin, then widening the front view from thin: a flat,
 * cut-out style turn that needs no in-between drawings.
 */
private fun DrawScope.drawHead(pose: BisonPose, s: BisonShapes, c: BisonColors) {
    val pivot = Offset(BisonShapes.HEAD_PIVOT_X, 60f)
    val pinch = BisonShapes.HEAD_PINCH
    val nod = 2.5f * sin(pose.stride * 2)
    if (pose.look < 0.5f) {
        val sx = 1f - (1f - pinch) * (pose.look * 2)
        rotate(nod, pivot = Offset(148f, 36f)) {
            scale(sx, 1f, pivot) {
                drawPath(s.hornSide, c.horn)
                drawPath(s.headSide, c.far)
                drawCircle(BisonShapes.Eye, 2.6f, Offset(161f, 54f))
            }
        }
    } else {
        val sx = pinch + (1f - pinch) * ((pose.look - 0.5f) * 2)
        scale(sx, 1f, pivot) {
            drawPath(s.hornLeft, c.horn)
            drawPath(s.hornRight, c.horn)
            drawPath(s.beardFront, c.far)
            drawPath(s.headFront, c.far)
            drawOval(c.rear, Offset(148f, 78f), Size(20f, 16f))
            drawCircle(BisonShapes.Eye, 1.8f, Offset(154f, 87f))
            drawCircle(BisonShapes.Eye, 1.8f, Offset(162f, 87f))
            drawFrontEye(149f)
            drawFrontEye(167f)
        }
    }
}

private fun DrawScope.drawFrontEye(x: Float) {
    drawCircle(Color.White, 3.4f, Offset(x, 63f))
    drawCircle(BisonShapes.Eye, 2f, Offset(x, 64f))
}

/** How long the still bison stays when animations are off. */
private const val STILL_MS = 2_500L
