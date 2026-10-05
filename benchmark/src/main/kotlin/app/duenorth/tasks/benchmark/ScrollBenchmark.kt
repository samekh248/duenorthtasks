package app.duenorth.tasks.benchmark

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
 * Scrolling 1,000 tasks on "today", alone (T037) and while a sync applies 500 changes from the
 * service in ten rounds with 150 ms per call (T044). SC-005: under 1% janky frames either way.
 * scripts/check_benchmark_budgets.py turns the frame timings into pass or fail.
 *
 * Runs against the benchmark build's fake service, filled over adb by BenchmarkSeedReceiver.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scroll() = measure(syncing = false)

    @Test
    fun scrollWhileSyncing() = measure(syncing = true)

    private fun measure(syncing: Boolean) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        // gfxinfo rather than FrameTimingMetric: the CI emulator renders in software and its traces
        // have no frame timeline slices, which FrameTimingMetric needs.
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            seed(TASKS)
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.text(SEEDED_ROW)), TIMEOUT_MS)) { "Seeded tasks never showed" }
        }
    ) {
        if (syncing) churn(changes = 500, rounds = 10, latencyMs = 150)
        val x = device.displayWidth / 3
        val bottom = device.displayHeight * 3 / 4
        val top = device.displayHeight / 4
        // Fling down through the list and back, the way people skim a long list.
        repeat(FLINGS) {
            device.swipe(x, bottom, x, top, SWIPE_STEPS)
            device.waitForIdle()
        }
        repeat(FLINGS) {
            device.swipe(x, top, x, bottom, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TASKS = 1_000
        const val FLINGS = 4
        const val SWIPE_STEPS = 8
        const val TIMEOUT_MS = 10_000L

        /** A seeded title, or one a previous iteration's churn renamed. */
        val SEEDED_ROW: Pattern = Pattern.compile("Task \\d+|Edited on the web, change \\d+")
    }
}

private const val RECEIVER = "$TARGET_PACKAGE/$TARGET_PACKAGE.bench.BenchmarkSeedReceiver"

/** Include stopped packages: the benchmark force-stops the app between compilations. */
private const val FLAG_INCLUDE_STOPPED_PACKAGES = 32

/** Makes sure the fake service holds [tasks] tasks and the phone has synced them all. */
internal fun MacrobenchmarkScope.seed(tasks: Int) {
    val out = device.executeShellCommand(
        "am broadcast -f $FLAG_INCLUDE_STOPPED_PACKAGES -n $RECEIVER " +
            "-a $TARGET_PACKAGE.bench.SEED --ei tasks $tasks"
    )
    check("seeded" in out) { "Seeding failed: $out" }
}

/** Starts [changes] edits on the service, synced in [rounds], with [latencyMs] per call; returns at once. */
internal fun MacrobenchmarkScope.churn(changes: Int, rounds: Int, latencyMs: Int) {
    device.executeShellCommand(
        "am broadcast -f $FLAG_INCLUDE_STOPPED_PACKAGES -n $RECEIVER -a $TARGET_PACKAGE.bench.CHURN " +
            "--ei changes $changes --ei rounds $rounds --ei latencyMs $latencyMs"
    )
}
