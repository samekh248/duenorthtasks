package app.duenorth.tasks.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.PageHeader

/**
 * "sync account" before anything is connected (contracts/ui-screens.md). Google Tasks and
 * Microsoft To Do sign-in land in M3 and M4; debug builds also offer the demo account.
 */
@Composable
fun AccountScreen(demoAvailable: Boolean, connecting: Boolean, onDemo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MetroTheme.colors.background).statusBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("account")) {
            PageHeader("sync account")
            Column(
                Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetroText(
                    "Due North keeps your tasks in one service at a time. Pick the one you use.",
                    MetroTheme.typography.body,
                    color = MetroTheme.colors.secondary
                )
                MetroRadio(false, {}, "Google Tasks", caption = "coming soon", enabled = false)
                MetroRadio(false, {}, "Microsoft To Do", caption = "coming soon", enabled = false)
                if (demoAvailable) {
                    MetroRadio(
                        selected = connecting,
                        onClick = onDemo,
                        label = "demo account",
                        caption = "sample tasks kept on this phone (debug builds only)",
                        enabled = !connecting
                    )
                }
            }
        }
        if (connecting) MetroProgressDots()
    }
}
