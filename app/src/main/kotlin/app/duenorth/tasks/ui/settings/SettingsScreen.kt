package app.duenorth.tasks.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.BuildConfig
import app.duenorth.tasks.design.components.MetroAccentGrid
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.settings.ThemeMode
import app.duenorth.tasks.settings.ThemeSettings
import app.duenorth.tasks.ui.home.LogoMotion
import app.duenorth.tasks.ui.home.drawLogo

/** The settings pivot (contracts/ui-screens.md "Settings"). */
@Composable
fun SettingsScreen(
    onOpenSyncAccount: () -> Unit,
    onOpenSyncLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    SettingsContent(
        theme ?: ThemeSettings(),
        onMode = viewModel::setMode,
        onAccent = viewModel::setAccent,
        onOpenSyncAccount = onOpenSyncAccount,
        onOpenSyncLog = onOpenSyncLog
    )
}

@Composable
fun SettingsContent(
    theme: ThemeSettings,
    onMode: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
    onOpenSyncAccount: () -> Unit = {},
    onOpenSyncLog: () -> Unit = {},
    initialPage: Int = 0
) {
    val headers = listOf("theme", "sync account", "about")
    MetroPivot(
        headers = headers,
        pageTitle = "settings",
        modifier = Modifier.statusBarsPadding(),
        state = rememberPagerState(initialPage) { headers.size }
    ) { index ->
        when (index) {
            0 -> ThemePage(theme, onMode, onAccent)
            1 -> SyncLinks(onOpenSyncAccount, onOpenSyncLog)
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

/** The sync account and sync log pages have their own routes; this pivot item leads to them. */
@Composable
private fun SyncLinks(onOpenAccount: () -> Unit, onOpenLog: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .padding(top = MetroDimens.Grid)
    ) {
        LinkRow(
            "sync account",
            "choose Google Tasks or Microsoft To Do, sign in or out, and how often to sync",
            onOpenAccount
        )
        LinkRow(
            "sync log",
            "changes sync settled for you, and syncs that ran into trouble",
            onOpenLog,
            Modifier.padding(top = MetroDimens.Gutter)
        )
    }
}

@Composable
private fun LinkRow(title: String, caption: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpen)
            .heightIn(min = MetroDimens.TouchTarget)
    ) {
        MetroText(title, MetroTheme.typography.listName, color = MetroTheme.accent.text)
        MetroText(caption, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
    }
}

/** Where the app's code lives, shown under "developer". */
internal const val REPO_URL = "https://github.com/samekh248/duenorthtasks"

/**
 * The about page: the tile, name and version, who makes it, and credits. Seven quick taps on the
 * version send a bison across the page ([BisonWalk]) once; the walk and the tap count are plain
 * remembered state, so leaving the page forgets both and the taps are needed again.
 * [bisonPreviewMs] pins a bison frame for screenshot tests.
 */
@Composable
internal fun AboutPage(bisonPreviewMs: Float? = null) {
    val uriHandler = LocalUriHandler.current
    val streak = remember { TapStreak() }
    var bisonRuns by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(MetroTheme.colors.background).testTag("about")) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = MetroDimens.Grid, bottom = BISON_HEIGHT)
        ) {
            AppTile()
            MetroText(
                "due north tasks",
                MetroTheme.typography.listName,
                Modifier.padding(top = MetroDimens.Gutter).semantics { heading() }
            )
            MetroText(
                "version ${BuildConfig.VERSION_NAME}",
                MetroTheme.typography.body,
                Modifier
                    .metroTilt()
                    .clickable(interactionSource = null, indication = null) {
                        if (streak.tap(SystemClock.uptimeMillis())) bisonRuns++
                    }
                    .heightIn(min = MetroDimens.TouchTarget)
                    .wrapContentHeight()
                    .testTag("version"),
                color = MetroTheme.colors.secondary
            )
            MetroText(
                "A to-do list in the Windows Phone 8.1 style that syncs with Google Tasks or Microsoft To Do.",
                MetroTheme.typography.body,
                color = MetroTheme.colors.secondary
            )
            Label("developer")
            MetroText("Dustin", MetroTheme.typography.body)
            MetroLink("github.com/samekh248/duenorthtasks", onClick = { uriHandler.openUri(REPO_URL) })
            Label("credits")
            MetroText(
                "Set in Selawik, © 2015 Microsoft Corporation, used under the SIL Open Font License 1.1.",
                MetroTheme.typography.caption,
                color = MetroTheme.colors.secondary
            )
        }
        val bisonModifier = Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(BISON_HEIGHT)
        if (bisonPreviewMs != null) {
            BisonCanvas({ bisonPreviewMs }, bisonModifier, bleed = MetroDimens.Gutter)
        } else {
            BisonWalk(bisonRuns, onDone = { bisonRuns = 0 }, bisonModifier, bleed = MetroDimens.Gutter)
        }
    }
}

/** The launcher icon as a flat Metro tile: the check-north mark in white on the accent. */
@Composable
private fun AppTile() {
    val fill = MetroTheme.accent.fill
    val mark = Color.White
    Canvas(Modifier.size(TILE_SIZE).semantics { contentDescription = "due north tasks logo" }) {
        drawRect(fill)
        val h = size.height * 0.62f
        val w = h * LogoMotion.ASPECT
        inset((size.width - w) / 2, (size.height - h) / 2) { drawLogo(1f, mark) }
    }
}

private val TILE_SIZE = 96.dp
private val BISON_HEIGHT = 112.dp

@Composable
private fun Label(text: String) {
    MetroText(
        text,
        MetroTheme.typography.subheader,
        Modifier.padding(top = MetroDimens.Grid, bottom = 4.dp).semantics { heading() },
        color = MetroTheme.colors.secondary
    )
}
