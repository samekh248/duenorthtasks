package app.duenorth.tasks.ui.settings

import app.duenorth.tasks.BuildConfig
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BisonTest {
    @Test
    fun versionStartsAt012() {
        assertEquals("0.1.2", BuildConfig.VERSION_NAME)
    }

    @Test
    fun sevenQuickTapsSendTheBison() {
        val streak = TapStreak()
        repeat(6) { assertFalse(streak.tap(it * 300L)) }
        assertTrue(streak.tap(1_800))
        // The count starts over afterwards.
        repeat(6) { assertFalse(streak.tap(2_000L + it * 300L)) }
        assertTrue(streak.tap(3_800))
    }

    @Test
    fun aPauseStartsTheCountOver() {
        val streak = TapStreak()
        repeat(5) { assertFalse(streak.tap(it * 300L)) }
        // Too slow: this tap is the first of a new streak.
        assertFalse(streak.tap(1_200 + 2_000))
        repeat(5) { assertFalse(streak.tap(3_500L + it * 300L)) }
        assertTrue(streak.tap(3_500 + 5 * 300L))
    }

    @Test
    fun walksInFromTheLeftStopsInTheMiddleAndLeavesRight() {
        val stage = 411f
        val w = BisonShapes.WIDTH
        val first = BisonTimeline.pose(0f, stage)
        assertEquals(-w, first.x, 0.01f)

        val stopped = BisonTimeline.pose(BisonTimeline.WALK_IN_MS + 1f, stage)
        assertEquals((stage - w) / 2, stopped.x, 0.01f)
        assertEquals(0f, stopped.look, 0f)

        val looking = BisonTimeline.pose(BisonTimeline.LOOK_HOLD_START + 100f, stage)
        assertEquals(1f, looking.look, 0f)
        assertEquals(stopped.x, looking.x, 0f)

        val lookedBack = BisonTimeline.pose(BisonTimeline.WALK_OFF_START - 1f, stage)
        assertEquals(0f, lookedBack.look, 0.001f)

        val gone = BisonTimeline.pose(BisonTimeline.TOTAL_MS, stage)
        assertEquals(stage, gone.x, 0.01f)
    }

    @Test
    fun legsStandStraightWhenItStops() {
        val pose = BisonTimeline.pose(BisonTimeline.WALK_IN_MS - 0.01f, 411f)
        assertEquals(0f, abs(sin(pose.stride)), 0.01f)
    }

    @Test
    fun itOnlyMovesForwardWhileWalking() {
        var last = Float.NEGATIVE_INFINITY
        var ms = 0f
        while (ms <= BisonTimeline.TOTAL_MS) {
            val x = BisonTimeline.pose(ms, 360f).x
            assertTrue("went back at $ms", x >= last - 0.001f)
            last = x
            ms += 16f
        }
    }
}
