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
     * Taps the first unticked box. Looked up fresh at tap time (UiObject, not UiObject2): a
     * UiObject2 found a moment earlier went stale on every CI run before it could be tapped.
     */
    private fun MacrobenchmarkScope.tickOpenTask() {
        repeat(ATTEMPTS) {
            val box = device.findObject(UiSelector().checkable(true).checked(false))
            check(box.waitForExists(TIMEOUT_MS)) { "No open task to tick" }
            if (box.click()) return
            device.waitForIdle()
        }
        error("Couldn't tap an open task's check box")
    }

    private companion object {
        /** Matches TICK_TRACE_SECTION in core:design's MetroCheckBox. */
        const val TICK_SECTION = "MetroCheckBox tick"
        const val TASKS = 200
        const val TAPS = 5
        const val TIMEOUT_MS = 10_000L
        const val ATTEMPTS = 3
    }
}
