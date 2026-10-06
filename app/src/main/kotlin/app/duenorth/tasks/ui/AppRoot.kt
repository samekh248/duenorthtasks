package app.duenorth.tasks.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.di.ActivitySignInHost
import app.duenorth.tasks.nav.AppActions
import app.duenorth.tasks.nav.DueNorthNavHost
import app.duenorth.tasks.ui.account.AccountScreen
import app.duenorth.tasks.ui.account.AppState
import app.duenorth.tasks.ui.account.AppViewModel

/** The whole app: the account gate, then the panorama and its pages. */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val turning by viewModel.syncButtonTurning.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as ComponentActivity
    // Built per sign-in from the current Activity; holds nothing afterwards.
    val host = { ActivitySignInHost(activity) }
    val actions = remember(viewModel) {
        AppActions(
            syncNow = viewModel::syncNow,
            switchTo = { kind -> viewModel.switchTo(kind, host()) },
            signOut = viewModel::signOut
        )
    }
    Box(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        // A cold start goes straight from the blank window to the first screen: fading it in would
        // draw the whole screen through an offscreen layer while the first swipes come in.
        if (state != AppState.Loading) {
            Crossfade(targetState = state, label = "account gate") { current ->
                when (current) {
                    AppState.Loading -> Box(Modifier.fillMaxSize())
                    AppState.NoAccount -> AccountScreen(
                        demoAvailable = viewModel.demoAvailable,
                        connecting = connecting,
                        error = error,
                        isConfigured = viewModel::isConfigured,
                        onPick = { kind -> viewModel.connect(kind, host()) }
                    )
                    AppState.Connected -> DueNorthNavHost(actions, syncing, turning)
                }
            }
        }
    }
}
