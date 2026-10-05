package app.duenorth.tasks.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroPanorama
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T064: what TalkBack reads, 48dp targets and headings. Contrast is in [ContrastTest]. Debug only:
 * the compose test activity is in the debug manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appBarButtonsAreLabelledAndAtLeast48dp() {
        compose.setContent {
            MetroTheme(darkTheme = false) {
                MetroAppBar(
                    buttons = listOf(
                        AppBarButton(MetroIcon.Add, "add") {},
                        AppBarButton(MetroIcon.Search, "search") {},
                        AppBarButton(MetroIcon.Sync, "sync", enabled = false) {}
                    ),
                    menuItems = listOf(AppBarMenuItem("settings") {})
                )
            }
        }
        for (label in listOf("add", "search", "sync", "more")) {
            val node = compose.onNode(hasContentDescription(label)).fetchSemanticsNode()
            assertTouchTarget(node, label)
        }
    }

    @Test
    fun checkBoxNamesWhatItChecksOff() {
        compose.setContent {
            MetroTheme(darkTheme = false) {
                MetroCheckBox(checked = false, onCheckedChange = {}, label = "Call the vet")
            }
        }
        val node = compose.onNode(hasContentDescription("Call the vet")).fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsProperties.ToggleableState))
        assertTouchTarget(node, "check box")
    }

    @Test
    fun panoramaSectionsAreHeadings() {
        compose.setContent {
            MetroTheme(darkTheme = false) {
                MetroPanorama(
                    title = "due north",
                    sections = listOf("today", "lists", "done").map { header ->
                        PanoramaSection(header) { MetroText("$header content", MetroTheme.typography.body) }
                    }
                )
            }
        }
        compose.onNode(hasText("today") and isHeading()).assertExists()
    }

    @Test
    fun pivotHeadersAreTabsAndTheCurrentOneIsSelected() {
        compose.setContent {
            MetroTheme(darkTheme = false) {
                Box(Modifier.height(320.dp)) {
                    MetroPivot(headers = listOf("theme", "sync account", "about")) { Column {} }
                }
            }
        }
        compose.onNode(hasText("theme") and isTab()).assertIsSelected()
        compose.onNode(hasText("sync account") and isTab()).assertIsNotSelected()
    }

    private fun assertTouchTarget(node: SemanticsNode, what: String) {
        val density = node.layoutInfo.density
        val width = with(density) { node.size.width.toDp() }
        val height = with(density) { node.size.height.toDp() }
        assertTrue("$what is $width x $height", width >= MetroDimens.TouchTarget && height >= MetroDimens.TouchTarget)
        assertEquals("$what has a click action", true, node.config.getOrNull(SemanticsActions.OnClick) != null)
    }

    private fun isTab() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
}
