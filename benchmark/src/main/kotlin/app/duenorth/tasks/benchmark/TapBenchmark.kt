package app.duenorth.tasks.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tap-to-tick (T037, SC-008): from a tap on a task's check box on "today" to the first frame that
 * draws it ticked, at most 100 ms. The app writes that span as the "MetroCheckBox tick" trace
 * section; scripts/check_benchmark_budgets.py checks the slowest tap of each run.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class TapBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun tapToTick() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(TICK_SECTION, TraceSectionMetric.Mode.Max),
            TraceSectionMetric(TICK_SECTION, TraceSectionMetric.Mode.Count)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            // Enough open tasks for every tap of every iteration; ticked ones leave "today".
            seed(TASKS)
            pressHome()
            startActivityAndWait()
            waitForHome()
            // Let the panorama finish building its other sections, which refreshes the tree.
            device.waitForIdle()
        }
    ) {
        repeat(TAPS) {
            tickOpenTask()
            device.waitForIdle()
        }
    }

    /**
     * Taps the first unticked box at the centre of where it is on screen right now. Neither a
     * cached UiObject2 (stale on every CI run) nor UiObject.click() (which waits for an
     * accessibility event and reported failure every time) held up, so this reads the bounds and
     * taps the screen directly. Whether the tap landed is what the trace section measures: no
     * ticks means no "MetroCheckBox tick" sections, which the budget check reports as missing.
     */
    private fun MacrobenchmarkScope.tickOpenTask() {
        val box = device.findObject(UiSelector().checkable(true).checked(false))
        check(box.waitForExists(TIMEOUT_MS)) { "No open task to tick" }
        val bounds = box.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
    }

    private companion object {
        /** Matches TICK_TRACE_SECTION in core:design's MetroCheckBox. */
        const val TICK_SECTION = "MetroCheckBox tick"
        const val TASKS = 200
        const val TAPS = 5
        const val TIMEOUT_MS = 10_000L
    }
}
