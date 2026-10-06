package app.duenorth.tasks.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAccentGrid
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenuContent
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroDialogContent
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroPanorama
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroShadeRow
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.components.PanoramaState
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One screenshot per Metro component in the light and dark themes (constitution Principle V).
 * `./gradlew :core:design:recordRoborazziDebug` writes them to build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MetroComponentScreenshotTest {
    @Test
    fun typeRamp() = snap("type_ramp") {
        val type = MetroTheme.typography
        Column {
            MetroText("DUE NORTH", type.pageTitle)
            MetroText("today", type.sectionHeader)
            MetroText("sync account", type.header)
            MetroText("return library books", type.detailTitle)
            MetroText("Errands", type.listName)
            MetroText("Pick up dry cleaning", type.subheader)
            MetroText("Due back Tuesday. The two in the hall.", type.body)
            MetroText("Errands · today", type.caption, color = MetroTheme.accent.text)
        }
    }

    @Test
    fun checkBoxes() = snap("check_box") {
        Row {
            MetroCheckBox(checked = false, onCheckedChange = {})
            MetroCheckBox(checked = true, onCheckedChange = {})
            MetroCheckBox(checked = false, onCheckedChange = {}, enabled = false)
        }
    }

    @Test
    fun toggles() = snap("toggle") {
        Column {
            MetroToggle(checked = true, onCheckedChange = {}, label = "sync on Wi-Fi only")
            MetroToggle(checked = false, onCheckedChange = {}, label = "sync on Wi-Fi only")
        }
    }

    @Test
    fun radios() = snap("radio") {
        Column {
            MetroRadio(selected = true, onClick = {}, label = "Google Tasks", caption = "signed in as dustin")
            MetroRadio(selected = false, onClick = {}, label = "Microsoft To Do", caption = "not connected")
        }
    }

    @Test
    fun textFields() = snap("text_field") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroTextField(value = "", onValueChange = {}, placeholder = "add a task")
            MetroTextField(value = "Return library books", onValueChange = {})
        }
    }

    @Test
    fun buttons() = snap("button") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton("switch", onClick = {})
            MetroButton("cancel", onClick = {}, enabled = false)
        }
    }

    @Test
    fun listItems() = snap("list_item") {
        Column {
            MetroListItem(
                title = "Return library books",
                details = "Due back Tuesday. The two in the hall, plus the one on the nightstand.",
                caption = "Errands · today",
                leading = { MetroCheckBox(checked = false, onCheckedChange = {}) },
                onClick = {}
            )
            MetroListItem(
                title = "Pay water bill",
                caption = "Home · 2 days overdue",
                captionColor = MetroTheme.colors.overdue,
                leading = { MetroCheckBox(checked = false, onCheckedChange = {}) },
                onClick = {}
            )
            MetroListItem(
                title = "Buy milk",
                caption = "Groceries · done",
                strikethrough = true,
                leading = { MetroCheckBox(checked = true, onCheckedChange = {}) },
                onClick = {}
            )
        }
    }

    @Test
    fun listTiles() = snap("list_tile") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroListTile(count = 4)
            MetroListTile(count = 12)
            MetroListTile(count = null)
        }
    }

    @Test
    fun icons() = snap("icons") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroIcon.entries.forEach { MetroIconGlyph(it) }
        }
    }

    @Test
    fun progressDots() = snap("progress_dots") {
        MetroProgressDots(Modifier.width(300.dp))
    }

    @Test
    fun dialog() = snap("dialog", padded = false) {
        MetroDialogContent(
            title = "switch to Microsoft To Do?",
            message = "Switching signs out of Google Tasks. Your tasks stay in that account and come back when " +
                "you switch again.",
            confirmLabel = "switch",
            onConfirm = {},
            dismissLabel = "cancel",
            onDismiss = {}
        )
    }

    @Test
    fun contextMenu() = snap("context_menu", padded = false) {
        MetroContextMenuContent(
            items = listOf(ContextMenuItem("edit") {}, ContextMenuItem("delete") {}, ContextMenuItem("move to") {}),
            onItemClick = {}
        )
    }

    @Test
    fun datePicker() = snap("date_picker") {
        MetroDatePicker(date = LocalDate.of(2026, 10, 4), onDateChange = {})
    }

    @Test
    fun accentGrid() = snap("accent_grid") {
        MetroAccentGrid(selected = Accent.Magenta, onSelect = {})
    }

    @Test
    fun shadeRow() = snap("shade_row") {
        MetroShadeRow(accent = Accent.Magenta, selectedStep = 0, onSelect = {})
    }

    @Test
    fun appBar() = snap("app_bar", padded = false) {
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Add, "new task") {},
                AppBarButton(MetroIcon.Sync, "sync") {},
                AppBarButton(MetroIcon.Search, "search") {}
            ),
            menuItems = listOf(AppBarMenuItem("settings") {}, AppBarMenuItem("sync log") {})
        )
    }

    @Test
    fun appBarExpanded() = snap("app_bar_expanded", padded = false) {
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Add, "new task") {},
                AppBarButton(MetroIcon.Sync, "sync") {},
                AppBarButton(MetroIcon.Search, "search") {}
            ),
            menuItems = listOf(AppBarMenuItem("settings") {}, AppBarMenuItem("sync log") {}),
            initiallyExpanded = true
        )
    }

    @Test
    fun panorama() = snap("panorama", padded = false) {
        MetroPanorama(
            title = "due north",
            sections = listOf("today", "lists", "done").map { header ->
                PanoramaSection(header) {
                    Column {
                        MetroListItem(
                            title = "Return library books",
                            caption = "Errands · today",
                            leading = { MetroCheckBox(checked = false, onCheckedChange = {}) }
                        )
                    }
                }
            },
            modifier = Modifier.height(480.dp)
        )
    }

    /** Resting on the last section, the first one peeks in after it. */
    @Test
    fun panoramaLast() = snap("panorama_last", padded = false) { PanoramaAt(2f) }

    /** Halfway across the seam from the last section to the first, the title's copy sliding in. */
    @Test
    fun panoramaSeam() = snap("panorama_seam", padded = false) { PanoramaAt(2.5f) }

    @Composable
    private fun PanoramaAt(position: Float) {
        val state = remember { PanoramaState(3).also { it.position = position } }
        MetroPanorama(
            title = "tasks",
            subtitle = "due north",
            state = state,
            sections = listOf("today", "lists", "done").map { header ->
                PanoramaSection(header) {
                    Column {
                        MetroListItem(title = "$header task", caption = "Errands", leading = {
                            MetroCheckBox(checked = false, onCheckedChange = {})
                        })
                    }
                }
            },
            modifier = Modifier.height(480.dp)
        )
    }

    @Test
    fun pivot() = snap("pivot", padded = false) {
        MetroPivot(
            headers = listOf("theme", "sync account", "about"),
            pageTitle = "due north",
            modifier = Modifier.height(320.dp)
        ) {
            MetroText("follow phone", MetroTheme.typography.subheader)
        }
    }

    /** Captures [content] once per theme. */
    private fun snap(name: String, padded: Boolean = true, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) {
            captureRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png") {
                MetroTheme(darkTheme = dark) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MetroTheme.colors.background)
                            .padding(if (padded) 12.dp else 0.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
