package app.duenorth.tasks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.BuildConfig
import app.duenorth.tasks.design.components.MetroAccentGrid
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.settings.ThemeMode
import app.duenorth.tasks.settings.ThemeSettings

/** The settings pivot (contracts/ui-screens.md "Settings"). */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    SettingsContent(theme ?: ThemeSettings(), onMode = viewModel::setMode, onAccent = viewModel::setAccent)
}

@Composable
fun SettingsContent(theme: ThemeSettings, onMode: (ThemeMode) -> Unit, onAccent: (Accent) -> Unit) {
    MetroPivot(
        headers = listOf("theme", "about"),
        pageTitle = "settings",
        modifier = Modifier.statusBarsPadding()
    ) { index ->
        when (index) {
            0 -> ThemePage(theme, onMode, onAccent)
            else -> AboutPage()
        }
    }
}

/** WP8.1's "start+theme": background, then the accent grid. Every pick applies at once. */
@Composable
private fun ThemePage(theme: ThemeSettings, onMode: (ThemeMode) -> Unit, onAccent: (Accent) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .testTag("theme")
    ) {
        Label("background")
        ThemeMode.entries.forEach { mode ->
            MetroRadio(
                selected = theme.mode == mode,
                onClick = { onMode(mode) },
                label = mode.label,
                caption = if (mode ==
                    ThemeMode.SYSTEM
                ) {
                    "the phone is ${if (systemDark) "dark" else "light"} now"
                } else {
                    null
                }
            )
        }
        Label("accent color")
        MetroText(
            theme.accent.displayName,
            MetroTheme.typography.subheader,
            Modifier.padding(bottom = MetroDimens.Gutter),
            color = MetroTheme.accent.text
        )
        MetroAccentGrid(selected = theme.accent, onSelect = onAccent)
        MetroText(
            "Lists use the accent, or a lighter or darker shade of it you pick for each list. " +
                "Shades follow when the accent changes.",
            MetroTheme.typography.caption,
            Modifier.padding(top = MetroDimens.Gutter, bottom = MetroDimens.Grid),
            color = MetroTheme.colors.secondary
        )
    }
}

@Composable
private fun AboutPage() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
    ) {
        Label("due north tasks")
        MetroText("version ${BuildConfig.VERSION_NAME}", MetroTheme.typography.body)
        MetroText(
            "A to-do list in the Windows Phone 8.1 style that syncs with Google Tasks or Microsoft To Do.",
            MetroTheme.typography.body,
            Modifier.padding(top = MetroDimens.Gutter),
            color = MetroTheme.colors.secondary
        )
    }
}

@Composable
private fun Label(text: String) {
    MetroText(
        text,
        MetroTheme.typography.subheader,
        Modifier.padding(top = MetroDimens.Grid, bottom = 4.dp).semantics { heading() },
        color = MetroTheme.colors.secondary
    )
}
