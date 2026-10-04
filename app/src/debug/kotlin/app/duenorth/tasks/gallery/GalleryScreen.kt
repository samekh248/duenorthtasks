package app.duenorth.tasks.gallery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAccentGrid
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroPanorama
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroShadeRow
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.motion.slideInStagger
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.LocalAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import java.time.LocalDate

/**
 * Debug-only gallery of every Metro component (T015, milestone M1), laid out as a panorama so
 * the panorama itself is on show. The theme and accent can be switched live from the "colors"
 * section to check both themes on a real phone.
 */
@Composable
fun GalleryScreen() {
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var accent by rememberSaveable { mutableStateOf(Accent.Default) }
    MetroTheme(darkTheme = dark, accent = accent) {
        GalleryContent(
            dark = dark,
            onDarkChange = { dark = it },
            accent = accent,
            onAccentChange = { accent = it }
        )
    }
}

@Composable
private fun GalleryContent(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    accent: Accent,
    onAccentChange: (Accent) -> Unit
) {
    var syncing by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                MetroPanorama(
                    title = "metro gallery",
                    sections = listOf(
                        PanoramaSection("type") { TypeSection() },
                        PanoramaSection("tasks") { TasksSection() },
                        PanoramaSection("controls") { ControlsSection(onShowDialog = { showDialog = true }) },
                        PanoramaSection("lists") { ListsSection(accent) },
                        PanoramaSection("colors") { ColorsSection(dark, onDarkChange, accent, onAccentChange) }
                    )
                )
                if (syncing) MetroProgressDots()
            }
            MetroAppBar(
                buttons = listOf(
                    AppBarButton(MetroIcon.Add, "new task") {},
                    AppBarButton(MetroIcon.Sync, "sync") { syncing = !syncing },
                    AppBarButton(MetroIcon.Search, "search") {}
                ),
                menuItems = listOf(
                    AppBarMenuItem("show dialog") { showDialog = true },
                    AppBarMenuItem(if (dark) "light theme" else "dark theme") { onDarkChange(!dark) }
                )
            )
        }
        if (showDialog) {
            MetroDialog(
                title = "switch to Microsoft To Do?",
                message = "Switching signs out of Google Tasks. Your tasks stay in that account and come back " +
                    "when you switch again.",
                confirmLabel = "switch",
                onConfirm = { showDialog = false },
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Composable
private fun TypeSection() {
    val type = MetroTheme.typography
    Column(Modifier.verticalScroll(rememberScrollState())) {
        MetroText("DUE NORTH", type.pageTitle)
        MetroText("header", type.header, maxLines = 1)
        MetroText("detail title", type.detailTitle)
        MetroText("List name", type.listName)
        MetroText("Subheader and task titles", type.subheader)
        MetroText("Body text for details and settings rows.", type.body)
        MetroText("Preview lines under a task title", type.preview, color = MetroTheme.colors.secondary)
        MetroText("Caption · today", type.caption, color = MetroTheme.accent.text)
    }
}

@Composable
private fun TasksSection() {
    val tasks = remember {
        mutableStateListOf(
            GalleryTask("Return library books", "Due back Tuesday. The two in the hall.", "Errands · today"),
            GalleryTask("Pay water bill", null, "Home · 2 days overdue", overdue = true),
            GalleryTask("Call the vet", "Ask about the booster.", "Personal · tomorrow"),
            GalleryTask("Pick up dry cleaning", null, "Errands · tomorrow")
        )
    }
    var draft by rememberSaveable { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        MetroTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "add a task",
            modifier = Modifier.padding(end = MetroDimens.Gutter)
        )
        Spacer(Modifier.height(8.dp))
        tasks.forEachIndexed { index, task ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.slideInStagger(index)) {
                MetroListItem(
                    title = task.title,
                    details = task.details,
                    caption = task.caption,
                    captionColor = if (task.overdue) MetroTheme.colors.overdue else MetroTheme.accent.text,
                    strikethrough = task.done,
                    leading = {
                        MetroCheckBox(checked = task.done, onCheckedChange = { tasks[index] = task.copy(done = it) })
                    },
                    onClick = {},
                    onLongClick = { menu = true }
                )
                MetroContextMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    items = listOf(
                        ContextMenuItem("edit") {},
                        ContextMenuItem("delete") { tasks.removeAt(index) },
                        ContextMenuItem("move to") {}
                    )
                )
            }
        }
    }
}

private data class GalleryTask(
    val title: String,
    val details: String?,
    val caption: String,
    val overdue: Boolean = false,
    val done: Boolean = false
)

@Composable
private fun ControlsSection(onShowDialog: () -> Unit) {
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var provider by rememberSaveable { mutableIntStateOf(0) }
    var due by remember { mutableStateOf(LocalDate.now()) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(end = MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetroRadio(provider == 0, { provider = 0 }, "Google Tasks", caption = "signed in as demo")
        MetroRadio(provider == 1, { provider = 1 }, "Microsoft To Do", caption = "not connected")
        MetroToggle(wifiOnly, { wifiOnly = it }, label = "sync on Wi-Fi only")
        MetroButton("show dialog", onShowDialog)
        MetroText("due $due", MetroTheme.typography.body, color = MetroTheme.accent.text)
        MetroDatePicker(date = due, onDateChange = { due = it })
    }
}

@Composable
private fun ListsSection(accent: Accent) {
    val lists = listOf("Inbox" to 4, "Errands" to 2, "Home" to 7, "Work" to 12)
    var shades by remember { mutableStateOf(mapOf("Errands" to -2, "Home" to 2)) }
    var editing by remember { mutableStateOf("Errands") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lists.forEach { (name, count) ->
            val shade = AccentShades.colors(accent, shades[name] ?: 0, MetroTheme.colors)
            CompositionLocalProvider(LocalAccent provides shade) {
                Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                    MetroListTile(count = count)
                    Column {
                        MetroText(name, MetroTheme.typography.listName)
                        MetroText("next: something", MetroTheme.typography.caption, color = MetroTheme.accent.text)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
            MetroListTile(count = null)
            MetroText("new list", MetroTheme.typography.listName)
        }
        MetroText("$editing shade", MetroTheme.typography.subheader)
        MetroShadeRow(
            accent = accent,
            selectedStep = shades[editing] ?: 0,
            onSelect = { shades = shades + (editing to it) },
            modifier = Modifier.padding(end = MetroDimens.Gutter)
        )
        lists.forEach { (name, _) -> MetroRadio(editing == name, { editing = name }, name) }
    }
}

@Composable
private fun ColorsSection(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    accent: Accent,
    onAccentChange: (Accent) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetroToggle(dark, onDarkChange, label = "dark theme")
        MetroText(accent.displayName, MetroTheme.typography.subheader, color = MetroTheme.accent.text)
        MetroAccentGrid(selected = accent, onSelect = onAccentChange)
    }
}
