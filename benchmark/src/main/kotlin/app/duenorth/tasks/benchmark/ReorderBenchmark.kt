package app.duenorth.tasks.benchmark

import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dragging a task through a 200-task list in reorder mode (specs/003-reordering FR-242, SC-204):
 * pick a row up by its gripper, hold it at the bottom edge so the list scrolls under it, then at
 * the top edge, and drop it. Held to the same jank budget as scrolling (SC-005), which
 * scripts/check_benchmark_budgets.py turns into pass or fail.
 *
 * The benchmark build's seeder fills one list, "Benchmark", with the tasks.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ReorderBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun reorderDrag() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            seed(TASKS)
            pressHome()
            startActivityAndWait()
            waitForHome()
            openReorderMode()
        }
    ) {
        dragThroughTheList()
    }

    /** Lists section, the seeded list, then its app bar's reorder button. */
    private fun MacrobenchmarkScope.openReorderMode() {
        val y = device.displayHeight / 2
        device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, SWIPE_STEPS)
        val list = device.wait(Until.findObject(By.text(LIST_TITLE)), TIMEOUT_MS)
        checkNotNull(list) { "The seeded list never showed on the lists section" }.click()
        val reorder = device.wait(Until.findObject(By.desc("reorder").enabled(true)), TIMEOUT_MS)
        checkNotNull(reorder) { "The list page's reorder button never showed" }.click()
        check(device.wait(Until.hasObject(By.text(HINT)), TIMEOUT_MS)) { "Reorder mode never opened" }
        device.waitForIdle()
    }

    /**
     * One unbroken touch: down on the first row's gripper, to the bottom edge and held there while
     * the list scrolls, up to the top edge and held there, then dropped mid-list. Edges come from
     * what's on screen: the hint line sits at the top of the list, the done button just under it.
     */
    private fun MacrobenchmarkScope.dragThroughTheList() {
        val dp = device.displayWidth.toFloat() / device.displaySizeDp.x
        val row = checkNotNull(device.wait(Until.findObject(By.text(TASK_ROW)), TIMEOUT_MS)) { "No task rows" }
        val hint = checkNotNull(device.findObject(By.text(HINT))) { "No hint line" }
        val done = checkNotNull(device.findObject(By.desc("done"))) { "No done button" }
        val gripperX = device.displayWidth - (GRIPPER_CENTER_DP * dp).toInt()
        val start = Point(gripperX, row.visibleBounds.centerY())
        // Inside the 64dp auto-scroll zones at each end of the list.
        val bottom = Point(gripperX, done.visibleBounds.top - (EDGE_INSET_DP * dp).toInt())
        val top = Point(gripperX, hint.visibleBounds.top + (EDGE_INSET_DP * dp / 2).toInt())
        val middle = Point(gripperX, (top.y + bottom.y) / 2)
        val path = buildList {
            add(start)
            add(bottom)
            repeat(HOLD_SEGMENTS) { add(bottom) }
            add(top)
            repeat(HOLD_SEGMENTS) { add(top) }
            add(middle)
        }
        device.swipe(path.toTypedArray(), SEGMENT_STEPS)
        device.waitForIdle()
    }

    private companion object {
        const val TASKS = 200
        const val LIST_TITLE = "Benchmark"
        const val HINT = "drag a task to move it"
        val TASK_ROW: Pattern = Pattern.compile("Task \\d+")

        /** The gripper's touch zone is the row's right 56dp. */
        const val GRIPPER_CENTER_DP = 28
        const val EDGE_INSET_DP = 40

        /** Each swipe step is about 5 ms: 30 steps per segment, held ten segments (about 1.5 s). */
        const val SEGMENT_STEPS = 30
        const val HOLD_SEGMENTS = 10
        const val SWIPE_STEPS = 6
        const val TIMEOUT_MS = 10_000L
    }
}
