package app.duenorth.tasks

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.duenorth.tasks.badge.TodayBadge
import app.duenorth.tasks.badge.TodayBadgeSettings
import app.duenorth.tasks.badge.TodayNotifier
import app.duenorth.tasks.design.components.MetroAppBarHost
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.AppRoot
import app.duenorth.tasks.ui.settings.AppearanceViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appearance: AppearanceViewModel by viewModels()

    @Inject lateinit var badge: TodayBadge

    @Inject lateinit var badgeSettings: TodayBadgeSettings

    @Inject lateinit var notifier: TodayNotifier

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) badge.refresh()
    }

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
            MetroTheme(
                darkTheme = dark,
                accent = settings.theme.accent,
                listShades = settings.listShades,
                style = settings.theme.style
            ) {
                MetroAppBarHost { AppRoot() }
            }
        }
        askForBadgeOnce()
    }

    /**
     * Android 13+ needs the notification permission before the app icon can show today's count.
     * The app asks once, on the first launch; after that only the settings toggle asks.
     */
    private fun askForBadgeOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            if (notifier.allowed() || !badgeSettings.enabled.first()) return@launch
            if (badgeSettings.takeFirstAsk()) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
