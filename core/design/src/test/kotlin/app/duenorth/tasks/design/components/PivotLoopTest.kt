package app.duenorth.tasks.design.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import app.duenorth.tasks.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pivot pages form a ring, like the panorama's sections. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PivotLoopTest {
    @get:Rule val rule = createComposeRule()

    private val headers = listOf("theme", "sync account", "about")
    private lateinit var pivot: PivotState

    /** Past "about" comes "theme" again, and back from "theme" comes "about". */
    @Test
    fun swipesWrapAround() {
        showPivot()
        val seen = mutableListOf<Int>()
        repeat(4) {
            rule.onNodeWithTag("pivot").performTouchInput { swipeLeft() }
            rule.waitForIdle()
            seen += pivot.currentPage
        }
        repeat(4) {
            rule.onNodeWithTag("pivot").performTouchInput { swipeRight() }
            rule.waitForIdle()
            seen += pivot.currentPage
        }
        assertEquals(listOf(1, 2, 0, 1, 0, 2, 1, 0), seen)
    }

    /** Tapping a header always moves forwards, so "theme" from "about" is one page on, not two back. */
    @Test
    fun tappingAHeaderGoesForwardsRoundTheRing() {
        showPivot(initialPage = 2)
        val start = rule.runOnIdle { pivot.pager.currentPage }
        rule.onNodeWithText("theme").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, pivot.currentPage)
            assertEquals(start + 1, pivot.pager.currentPage)
        }
    }

    private fun showPivot(initialPage: Int = 0) {
        rule.setContent {
            MetroTheme {
                pivot = rememberPivotState(headers.size, initialPage)
                MetroPivot(headers = headers, state = pivot, modifier = Modifier.testTag("pivot")) { index ->
                    MetroText("page $index", MetroTheme.typography.subheader)
                }
            }
        }
    }
}
