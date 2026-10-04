package app.duenorth.tasks.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.nav.DueNorthNavHost
import app.duenorth.tasks.ui.account.AccountScreen
import app.duenorth.tasks.ui.account.AppState
import app.duenorth.tasks.ui.account.AppViewModel

/** The whole app: the account gate, then the panorama and its pages. */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Crossfade(targetState = state, label = "account gate") { current ->
            when (current) {
                AppState.Loading -> Box(Modifier.fillMaxSize())
                AppState.NoAccount -> AccountScreen(viewModel.demoAvailable, connecting, viewModel::connectDemo)
                AppState.Connected -> DueNorthNavHost(onSwitchAccount = viewModel::switchAccount)
            }
        }
    }
}
