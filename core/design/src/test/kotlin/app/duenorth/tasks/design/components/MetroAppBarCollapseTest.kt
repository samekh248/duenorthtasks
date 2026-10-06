package app.duenorth.tasks.design.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.duenorth.tasks.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An expanded app bar closes on a touch anywhere outside it, and on back. */
@RunWith(RobolectricTestRunner::class)
class MetroAppBarCollapseTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var contentTaps = 0
    private var settingsTaps = 0

    private fun showBar() {
        rule.setContent {
            MetroTheme(darkTheme = false) {
                MetroAppBarHost(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth().testTag("content").clickable { contentTaps++ }
                        )
                        MetroAppBar(
                            buttons = listOf(AppBarButton(MetroIcon.Add, "add") {}),
                            menuItems = listOf(AppBarMenuItem("settings") { settingsTaps++ })
                        )
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("more").performClick()
        rule.onNodeWithText("settings").assertExists()
    }

    @Test
    fun aTapOutsideClosesTheBarAndGoesNoFurther() {
        showBar()
        rule.onNodeWithTag("content").performClick()
        rule.onAllNodesWithText("settings").assertCountEquals(0)
        assertEquals(0, contentTaps)

        // Once closed, the same tap reaches the content again.
        rule.onNodeWithTag("content").performClick()
        assertEquals(1, contentTaps)
    }

    @Test
    fun tapsInsideTheBarStillWork() {
        showBar()
        rule.onNodeWithText("settings").performClick()
        assertEquals(1, settingsTaps)
        rule.onAllNodesWithText("settings").assertCountEquals(0)
    }

    @Test
    fun backClosesTheBar() {
        showBar()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onAllNodesWithText("settings").assertCountEquals(0)
    }
}
