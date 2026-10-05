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
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.common.PageHeader

/**
 * "sync account" before anything is connected (contracts/ui-screens.md): pick Google Tasks or
 * Microsoft To Do and sign in. A service whose client ID this build lacks is shown but disabled.
 * Debug builds also offer the demo account.
 */
@Composable
fun AccountScreen(
    demoAvailable: Boolean,
    connecting: ProviderKind?,
    error: String?,
    isConfigured: (ProviderKind) -> Boolean,
    onPick: (ProviderKind) -> Unit
) {
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
                ServiceRadios(
                    services = offeredServices(demoAvailable),
                    selected = connecting,
                    caption = { kind ->
                        when {
                            connecting == kind -> "signing in..."
                            !isConfigured(kind) -> "not set up in this build yet"
                            kind == ProviderKind.FAKE -> "sample tasks kept on this phone (debug builds only)"
                            kind == ProviderKind.GOOGLE -> "sign in with your Google account"
                            else -> "sign in with your Microsoft account"
                        }
                    },
                    enabled = { kind -> connecting == null && isConfigured(kind) },
                    onPick = onPick
                )
                if (error != null) {
                    MetroText(error, MetroTheme.typography.body, color = MetroTheme.colors.overdue)
                }
            }
        }
        if (connecting != null) MetroProgressDots()
    }
}
