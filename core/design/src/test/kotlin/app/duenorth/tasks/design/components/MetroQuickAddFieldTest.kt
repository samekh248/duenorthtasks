package app.duenorth.tasks.design.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import app.duenorth.tasks.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The quick-add box hands over trimmed text, skips blank text, and is empty after every add. */
@RunWith(RobolectricTestRunner::class)
class MetroQuickAddFieldTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val added = mutableListOf<String>()

    @Test
    fun addsTrimmedTextAndSkipsBlank() {
        rule.setContent { MetroTheme(darkTheme = false) { MetroQuickAddField("add a task", onAdd = { added += it }) } }
        val box = rule.onNode(hasSetTextAction())

        box.performTextInput("  Milk  ")
        box.performImeAction()
        box.performTextInput("   ")
        box.performImeAction()

        assertEquals(listOf("Milk"), added)
        box.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun quickAddsBeforeTheNextFrameStaySeparate() {
        rule.setContent { MetroTheme(darkTheme = false) { MetroQuickAddField("add a step", onAdd = { added += it }) } }
        val box = rule.onNode(hasSetTextAction())

        rule.mainClock.autoAdvance = false
        box.performTextInput("Milk")
        box.performImeAction()
        box.performTextInput("Eggs")
        box.performImeAction()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf("Milk", "Eggs"), added)
    }
}
