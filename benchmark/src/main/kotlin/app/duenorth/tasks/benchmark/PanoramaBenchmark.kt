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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first panorama swipes right after a cold start: round the ring both ways, as soon as the home
 * screen shows. They must be as smooth as any later swipe (constitution Principle II).
 * scripts/check_benchmark_budgets.py turns the frame timings into pass or fail.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PanoramaBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartSwipe() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        // The Baseline Profile applied, as after an install from the Play Store.
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            seed(TASKS)
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForHome()
        swipePanorama()
    }

    private companion object {
        const val TASKS = 50
    }
}

private const val TIMEOUT_MS = 10_000L
private const val SWIPE_STEPS = 6

/** today, lists, done and stats (spec 005 FR-432). */
private const val SECTIONS = 4

/** Waits until the home panorama shows its first section. */
internal fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.text("today")), TIMEOUT_MS)) { "The home panorama never showed" }
}

/**
 * Swipes from "today" round the ring back to "today", then the other way round, one section at a
 * time, so the seam between "done" and "today" is crossed both ways.
 */
internal fun MacrobenchmarkScope.swipePanorama() {
    val y = device.displayHeight / 2
    val right = device.displayWidth * 4 / 5
    val left = device.displayWidth / 5
    repeat(SECTIONS) {
        device.swipe(right, y, left, y, SWIPE_STEPS)
        device.waitForIdle()
    }
    repeat(SECTIONS) {
        device.swipe(left, y, right, y, SWIPE_STEPS)
        device.waitForIdle()
    }
}
