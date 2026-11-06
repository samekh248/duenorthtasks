package app.duenorth.tasks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.badge.TodayBadge
import app.duenorth.tasks.badge.TodayBadgeSettings
import app.duenorth.tasks.badge.TodayNotifier
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroStyle
import app.duenorth.tasks.settings.ThemeMode
import app.duenorth.tasks.settings.ThemeSettings
import app.duenorth.tasks.settings.ThemeSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The settings pivot's "theme" item (T060). Writes apply live through [AppearanceViewModel]. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: ThemeSettingsStore,
    private val badgeSettings: TodayBadgeSettings,
    private val badge: TodayBadge,
    private val notifier: TodayNotifier
) : ViewModel() {
    val theme: StateFlow<ThemeSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The app icon shows today's count (when notifications are allowed too). */
    val badgeEnabled: StateFlow<Boolean> = badgeSettings.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setBadgeEnabled(enabled: Boolean) {
        viewModelScope.launch { badgeSettings.setEnabled(enabled) }
    }

    /** Notifications were allowed again, outside the app's flows: post the badge now. */
    fun refreshBadge() = badge.refresh()

    fun notificationsAllowed(): Boolean = notifier.allowed()

    fun permissionGranted(): Boolean = notifier.permissionGranted()

    suspend fun badgeAsked(): Boolean = badgeSettings.asked()

    fun markBadgeAsked() {
        viewModelScope.launch { badgeSettings.markAsked() }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { store.setMode(mode) }
    }

    fun setAccent(accent: Accent) {
        viewModelScope.launch { store.setAccent(accent) }
    }

    fun setStyle(style: MetroStyle) {
        viewModelScope.launch { store.setStyle(style) }
    }
}
