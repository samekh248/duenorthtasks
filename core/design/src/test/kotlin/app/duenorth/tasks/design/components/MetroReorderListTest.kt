package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** specs/003-reordering R003, R004: the reorder list's gestures, accessibility actions and look. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MetroReorderListTest {
    @get:Rule val rule = createComposeRule()

    private val items =
        mutableStateListOf("Buy stamps", "Return library books", "Get stamps", "Call the vet", "Pay rent")
    private val moves = mutableListOf<List<String>>()

    /** 64dp rows: 192px each at xxhdpi. */
    private val rowPx = 192f

    private fun show(dark: Boolean = false) {
        rule.setContent {
            MetroTheme(darkTheme = dark) {
                MetroReorderList(
                    items = items,
                    key = { it },
                    onMove = { _, order ->
                        moves += order
                        items.clear()
                        items.addAll(order)
                    },
                    modifier = Modifier.fillMaxSize().background(MetroTheme.colors.background)
                ) { title ->
                    Box(
                        Modifier.height(64.dp).padding(start = MetroDimens.Gutter),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        MetroText(title, MetroTheme.typography.subheader)
                    }
                }
            }
        }
    }

    @Test
    fun draggingTheGripperMovesTheRow() {
        show()
        rule.onNodeWithTag("reorder-list").performTouchInput {
            down(Offset(right - 20f, rowPx / 2))
            repeat(20) {
                advanceEventTime(16)
                moveBy(Offset(0f, 20f))
            }
            up()
        }
        rule.waitForIdle()

        assertEquals(
            listOf("Return library books", "Get stamps", "Buy stamps", "Call the vet", "Pay rent"),
            moves.last()
        )
    }

    @Test
    fun aQuickSwipeOnTheRowScrollsInsteadOfPickingUp() {
        show()
        rule.onNodeWithTag("reorder-list").performTouchInput {
            down(Offset(centerX / 2, rowPx / 2))
            repeat(5) {
                advanceEventTime(16)
                moveBy(Offset(0f, 40f))
            }
            up()
        }
        rule.waitForIdle()

        assertEquals(emptyList<List<String>>(), moves)
    }

    @Test
    fun aStillPressOnTheRowPicksItUp() {
        show()
        rule.onNodeWithTag("reorder-list").performTouchInput {
            down(Offset(centerX / 2, rowPx * 1.5f))
            advanceEventTime(250)
            moveBy(Offset(0f, 1f))
            repeat(10) {
                advanceEventTime(16)
                moveBy(Offset(0f, -20f))
            }
            up()
        }
        rule.waitForIdle()

        assertEquals("Return library books", moves.last().first())
    }

    @Test
    fun talkBackCanMoveARowDown() {
        show()
        val row = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "position 1 of 5"))
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { actions.first { it.label == "move down" }.action() }
        rule.waitForIdle()

        assertEquals(
            listOf("Return library books", "Buy stamps", "Get stamps", "Call the vet", "Pay rent"),
            moves.last()
        )
    }

    @Test
    fun midDragLight() = midDrag(dark = false)

    @Test
    fun midDragDark() = midDrag(dark = true)

    /** The held row lifted between two others, the slot dashed, the rest faded (FR-202). */
    private fun midDrag(dark: Boolean) {
        show(dark)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("reorder-list").performTouchInput {
            down(Offset(right - 20f, rowPx * 2.5f))
            repeat(6) {
                advanceEventTime(16)
                moveBy(Offset(0f, 20f))
            }
        }
        // With the clock paused, nothing posts the drag's state writes; send them so the frames see them.
        Snapshot.sendApplyNotifications()
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("reorder-held", useUnmergedTree = true).assertExists()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/reorder_list_${if (dark) "dark" else "light"}.png")
        rule.onNodeWithTag("reorder-list").performTouchInput { up() }
    }
}
