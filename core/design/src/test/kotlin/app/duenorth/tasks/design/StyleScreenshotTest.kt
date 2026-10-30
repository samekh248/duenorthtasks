package app.duenorth.tasks.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenuContent
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroStyle
import app.duenorth.tasks.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The components each style changes, on one sheet per style and theme (spec 006). Writes
 * build/outputs/roborazzi/style_<style>_<theme>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StyleScreenshotTest {
    @Test
    fun metro() = sheet(MetroStyle.Metro)

    @Test
    fun tileRelief() = sheet(MetroStyle.TileRelief)

    @Test
    fun mica() = sheet(MetroStyle.Mica)

    @Test
    fun mobile10() = sheet(MetroStyle.Mobile10)

    private fun sheet(style: MetroStyle) {
        for (dark in listOf(false, true)) {
            val file = "build/outputs/roborazzi/style_${style.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            captureRoboImage(file) {
                MetroTheme(darkTheme = dark, style = style) {
                    Column(Modifier.fillMaxWidth().background(MetroTheme.colors.background)) {
                        MetroPivot(
                            headers = listOf("theme", "sync account", "about"),
                            pageTitle = "settings",
                            modifier = Modifier.height(150.dp)
                        ) {
                            MetroText("follow phone", MetroTheme.typography.subheader)
                        }
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                MetroListTile(count = 4)
                                MetroListTile(count = 7, shared = true)
                                MetroListTile(count = null)
                            }
                            MetroListItem(
                                title = "return library books",
                                caption = "Errands · today",
                                leading = { MetroCheckBox(checked = false, onCheckedChange = {}) }
                            )
                            MetroListItem(
                                title = "Pay water bill",
                                caption = "Home · done",
                                leading = { MetroCheckBox(checked = true, onCheckedChange = {}) }
                            )
                            MetroTextField(value = "", onValueChange = {}, placeholder = "add a task")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetroButton("switch", onClick = {})
                                MetroButton("cancel", onClick = {}, enabled = false)
                            }
                        }
                        MetroContextMenuContent(
                            listOf(ContextMenuItem("rename") {}, ContextMenuItem("delete") {}),
                            onItemClick = {}
                        )
                        MetroAppBar(
                            buttons = listOf(
                                AppBarButton(MetroIcon.Add, "new task") {},
                                AppBarButton(MetroIcon.Sync, "sync") {},
                                AppBarButton(MetroIcon.Search, "search") {}
                            ),
                            menuItems = listOf(AppBarMenuItem("settings") {})
                        )
                    }
                }
            }
        }
    }
}
