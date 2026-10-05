package app.duenorth.tasks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.design.theme.Accent
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
class SettingsViewModel @Inject constructor(private val store: ThemeSettingsStore) : ViewModel() {
    val theme: StateFlow<ThemeSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { store.setMode(mode) }
    }

    fun setAccent(accent: Accent) {
        viewModelScope.launch { store.setAccent(accent) }
    }
}
