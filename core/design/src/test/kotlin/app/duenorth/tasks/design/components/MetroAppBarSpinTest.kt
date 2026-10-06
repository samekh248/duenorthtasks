package app.duenorth.tasks.design.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.duenorth.tasks.design.theme.MetroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sync button turns while a sync runs and comes to rest when it ends. */
@RunWith(RobolectricTestRunner::class)
class MetroAppBarSpinTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun theSyncButtonSaysSyncingWhileItSpinsAndComesToRestAfter() {
        var syncing by mutableStateOf(true)
        rule.setContent {
            MetroTheme(darkTheme = false) {
                MetroAppBar(buttons = listOf(AppBarButton(MetroIcon.Sync, "sync", spinning = syncing) {}))
            }
        }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithContentDescription("sync").assert(stateIs("syncing"))

        syncing = false
        // The turn finishes and stops; an endless spin would never let the test go idle.
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("sync")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    private fun stateIs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
}
