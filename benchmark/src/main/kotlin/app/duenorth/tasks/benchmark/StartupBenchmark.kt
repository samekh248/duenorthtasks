package app.duenorth.tasks.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup budgets from plan.md: cold start <= 1s, warm start <= 300ms (SC-007).
 * scripts/check_benchmark_budgets.py reads the JSON output and fails CI when a budget is exceeded.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = startup(StartupMode.COLD)

    @Test
    fun warmStart() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = mode,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }
}

internal const val TARGET_PACKAGE = "app.duenorth.tasks"
