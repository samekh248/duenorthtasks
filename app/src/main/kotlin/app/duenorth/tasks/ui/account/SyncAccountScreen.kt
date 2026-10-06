package app.duenorth.tasks.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.sync.SyncSettings
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.PageHeader

/** What the sync account page asks of the app: both end this page, so they run app-wide. */
class SyncAccountActions(val switchTo: (ProviderKind) -> Unit, val signOut: () -> Unit)

@Composable
fun SyncAccountScreen(actions: SyncAccountActions, viewModel: SyncAccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncAccountContent(
        state = state,
        demoAvailable = viewModel.demoAvailable,
        isConfigured = viewModel::isConfigured,
        onInterval = viewModel::setInterval,
        onWifiOnly = viewModel::setWifiOnly,
        onSyncNow = viewModel::syncNow,
        actions = actions
    )
}

/**
 * "sync account" once connected (contracts/ui-screens.md): the service radio group with "signed in
 * as", "sync every", "sync on Wi-Fi only", and sync now / sign out. Picking the other service
 * asks first, and warns when changes on the phone haven't synced yet (T057).
 */
@Composable
fun SyncAccountContent(
    state: SyncAccountUiState,
    demoAvailable: Boolean,
    isConfigured: (ProviderKind) -> Boolean,
    onInterval: (Int) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    actions: SyncAccountActions
) {
    var switching by rememberSaveable { mutableStateOf<ProviderKind?>(null) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    var pickingInterval by rememberSaveable { mutableStateOf(false) }
    val current = state.provider
    val currentName = current?.let(::serviceLabel).orEmpty()
    val type = MetroTheme.typography
    val colors = MetroTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("sync account")) {
                PageHeader("sync account")
                Column(
                    Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetroText(
                        "Due North syncs your tasks with one service at a time.",
                        type.body,
                        color = colors.secondary
                    )
                    ServiceRadios(
                        services = offeredServices(demoAvailable || current == ProviderKind.FAKE),
                        selected = current,
                        caption = { kind ->
                            when {
                                kind == current -> "signed in as ${state.signedInAs}"
                                !isConfigured(kind) -> "not set up in this build yet"
                                else -> "not connected"
                            }
                        },
                        enabled = { kind -> kind == current || isConfigured(kind) },
                        onPick = { kind -> if (kind != current) switching = kind }
                    )
                    MetroText(
                        "Switching signs out of $currentName. Your tasks stay in that account and come back " +
                            "when you switch again.",
                        type.caption,
                        color = colors.secondary
                    )
                    if (current != null) {
                        MetroText(
                            if (state.storesOrder) {
                                "Task and step order sync; list order stays on this phone."
                            } else {
                                "Task, step and list order stay on this phone."
                            },
                            type.caption,
                            Modifier.testTag("order note"),
                            color = colors.secondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MetroText("sync every", type.caption, color = colors.secondary)
                    Chip(intervalLabel(state.settings.intervalMinutes)) { pickingInterval = true }
                    MetroToggle(state.settings.wifiOnly, onWifiOnly, label = "sync on Wi-Fi only")
                    MetroText(
                        when {
                            state.syncing -> "syncing..."
                            state.pendingChanges == 0 -> "everything on this phone is synced"
                            else -> "${changes(state.pendingChanges)} waiting to sync"
                        },
                        type.caption,
                        color = MetroTheme.accent.text
                    )
                }
            }
            if (state.syncing) MetroProgressDots()
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Sync, "sync now", spinning = state.syncing, onClick = onSyncNow),
                AppBarButton(MetroIcon.Close, "sign out") { signingOut = true }
            )
        )
    }

    switching?.let { kind ->
        MetroDialog(
            title = "switch to ${serviceLabel(kind)}?",
            message = "Switching signs out of $currentName. Your tasks stay in that account and come back when " +
                "you switch again. Your templates are kept only on this phone, so they are removed." +
                unsyncedWarning(state.pendingChanges),
            confirmLabel = "switch",
            onConfirm = {
                switching = null
                actions.switchTo(kind)
            },
            onDismiss = { switching = null }
        )
    }
    if (signingOut) {
        MetroDialog(
            title = "sign out of $currentName?",
            message = "This clears its tasks and your templates from this phone. Tasks already synced stay in " +
                "that account." + unsyncedWarning(state.pendingChanges),
            confirmLabel = "sign out",
            onConfirm = {
                signingOut = false
                actions.signOut()
            },
            onDismiss = { signingOut = false }
        )
    }
    if (pickingInterval) {
        MetroPickerDialog(
            title = "sync every",
            options = SyncSettings.INTERVAL_CHOICES,
            selected = state.settings.intervalMinutes,
            label = ::intervalLabel,
            onPick = {
                onInterval(it)
                pickingInterval = false
            },
            onDismiss = { pickingInterval = false }
        )
    }
}

internal fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    else -> "${minutes / 60} hours"
}

private fun changes(count: Int) = if (count == 1) "1 change" else "$count changes"

private fun unsyncedWarning(pending: Int): String = when (pending) {
    0 -> ""
    1 -> " 1 change on this phone hasn't synced yet and will be lost."
    else -> " $pending changes on this phone haven't synced yet and will be lost."
}
