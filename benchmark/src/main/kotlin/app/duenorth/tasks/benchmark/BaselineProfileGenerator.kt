package app.duenorth.tasks.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile that keeps startup, the first panorama swipes and the first
 * scroll fast (research R12). Until it runs on a device, app/src/main/baseline-prof.txt covers
 * the app's own code by hand.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        seed(TASKS)
        pressHome()
        startActivityAndWait()
        waitForHome()
        // The first thing people do after opening the app: slide through the panorama and back.
        swipePanorama()
        // Then skim "today".
        val x = device.displayWidth / 3
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, SCROLL_STEPS)
        device.waitForIdle()
    }

    private companion object {
        const val TASKS = 200
        const val SCROLL_STEPS = 8
    }
}
