package app.duenorth.tasks.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
        }
    ) {
        repeat(TAPS) {
            val box = device.wait(Until.findObject(By.checkable(true).checked(false)), TIMEOUT_MS)
                ?: error("No open task to tick")
            box.click()
            device.waitForIdle()
        }
    }

    private companion object {
        /** Matches TICK_TRACE_SECTION in core:design's MetroCheckBox. */
        const val TICK_SECTION = "MetroCheckBox tick"
        const val TASKS = 200
        const val TAPS = 5
        const val TIMEOUT_MS = 10_000L
    }
}
