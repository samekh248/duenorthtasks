package app.duenorth.tasks

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.AppRoot
import app.duenorth.tasks.ui.settings.AppearanceViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appearance: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugTools.trackJank(this)
        setContent {
            val current by appearance.appearance.collectAsStateWithLifecycle()
            // Until the theme settings are read the window background shows, never the wrong theme.
            val settings = current ?: return@setContent
            val dark = settings.theme.mode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                // Status and navigation bar icons follow the app's theme, not only the phone's.
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MetroTheme(darkTheme = dark, accent = settings.theme.accent, listShades = settings.listShades) {
                AppRoot()
            }
        }
    }
}
