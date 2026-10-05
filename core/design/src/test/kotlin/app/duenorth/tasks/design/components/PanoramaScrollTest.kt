package app.duenorth.tasks.design.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.duenorth.tasks.design.theme.MetroTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A slow finger drag scrolls every panorama section's list the same, the last one included. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PanoramaScrollTest {
    @get:Rule val rule = createComposeRule()

    private val names = listOf("today", "lists", "done")
    private lateinit var pager: PagerState
    private val lists = arrayOfNulls<LazyListState>(names.size)

    @Test
    fun slowDragScrollsEverySection() {
        rule.setContent {
            MetroTheme {
                pager = rememberPagerState { names.size }
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
        val scrolled = names.indices.map { page ->
            rule.runOnIdle { runBlocking { pager.scrollToPage(page) } }
            slowDrag(names[page])
            rule.runOnIdle { lists[page]!!.run { firstVisibleItemIndex to firstVisibleItemScrollOffset } }
        }
        assertTrue("today did not scroll: $scrolled", scrolled[0] != (0 to 0))
        assertEquals("sections scrolled differently: $scrolled", 1, scrolled.toSet().size)
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
