package app.duenorth.tasks.design.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import app.duenorth.tasks.design.theme.MetroTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Panorama gestures: slow drags scroll every section's list alike, and swipes go round the ring. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PanoramaScrollTest {
    @get:Rule val rule = createComposeRule()

    private val names = listOf("today", "lists", "done")
    private lateinit var pager: PanoramaState
    private val lists = arrayOfNulls<LazyListState>(names.size)

    @Test
    fun slowDragScrollsEverySection() {
        showPanorama()
        val scrolled = names.indices.map { page ->
            rule.runOnIdle { runBlocking { pager.scrollToSection(page) } }
            slowDrag(names[page])
            rule.runOnIdle { lists[page]!!.run { firstVisibleItemIndex to firstVisibleItemScrollOffset } }
        }
        assertTrue("today did not scroll: $scrolled", scrolled[0] != (0 to 0))
        assertEquals("sections scrolled differently: $scrolled", 1, scrolled.toSet().size)
    }

    /** Past "done" comes "today" again, and back from "today" comes "done". */
    @Test
    fun swipesWrapAround() {
        showPanorama()
        val seen = mutableListOf<Int>()
        repeat(4) {
            rule.onNodeWithTag(names[pager.currentSection]).performTouchInput { swipeLeft() }
            rule.waitForIdle()
            seen += pager.currentSection
        }
        repeat(4) {
            rule.onNodeWithTag(names[pager.currentSection]).performTouchInput { swipeRight() }
            rule.waitForIdle()
            seen += pager.currentSection
        }
        assertEquals(listOf(1, 2, 0, 1, 0, 2, 1, 0), seen)
        rule.runOnIdle { assertEquals("not resting on a section", 0f, pager.position) }
    }

    /** A drag short of halfway that lifts slowly settles back where it started. */
    @Test
    fun shortSlowDragSettlesBack() {
        showPanorama()
        rule.onNodeWithTag("today").performTouchInput {
            down(center)
            repeat(20) {
                advanceEventTime(16)
                moveBy(Offset(5f, 0f))
            }
            advanceEventTime(200)
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, pager.currentSection)
            assertEquals(0f, pager.position)
        }
    }

    private fun showPanorama() {
        rule.setContent {
            MetroTheme {
                pager = rememberPanoramaState(names.size)
                MetroPanorama(
                    title = "tasks",
                    state = pager,
                    sections = names.mapIndexed { i, name ->
                        PanoramaSection(name) {
                            val list = rememberLazyListState().also { lists[i] = it }
                            LazyColumn(Modifier.testTag(name), state = list) {
                                items(60) { n -> MetroListItem("task $n", caption = "inbox", onClick = {}) }
                            }
                        }
                    }
                )
            }
        }
    }

    /** 300px upward in small steps at 60fps, then a pause so it ends without a fling. */
    private fun slowDrag(tag: String) {
        rule.onNodeWithTag(tag).performTouchInput {
            down(Offset(centerX, centerY + 300f))
            repeat(60) {
                advanceEventTime(16)
                moveBy(Offset(0f, -5f))
            }
            advanceEventTime(200)
            up()
        }
        rule.waitForIdle()
    }
}
