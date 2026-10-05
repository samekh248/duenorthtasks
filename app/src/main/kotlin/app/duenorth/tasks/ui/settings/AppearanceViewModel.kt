package app.duenorth.tasks.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.settings.ListShades
import app.duenorth.tasks.settings.ThemeSettings
import app.duenorth.tasks.settings.ThemeSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@Immutable
data class Appearance(val theme: ThemeSettings, val listShades: Map<String, Int>)

/**
 * What the whole app is drawn with, held by the activity so a change in settings applies to every
 * screen at once, with no restart (US5). Null until the theme settings are read, so the first
 * frame never flashes the wrong background.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(theme: ThemeSettingsStore, shades: ListShades) : ViewModel() {
    val appearance: StateFlow<Appearance?> = combine(
        theme.settings,
        // Shades wait on Room; the theme must not.
        shades.byList.onStart { emit(emptyMap()) }
    ) { settings, byList -> Appearance(settings, byList) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
