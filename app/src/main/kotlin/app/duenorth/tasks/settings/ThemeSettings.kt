package app.duenorth.tasks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.duenorth.tasks.design.theme.Accent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Light or dark background: the phone's setting (default) or a manual override (FR-004). */
enum class ThemeMode(val label: String) {
    SYSTEM("follow phone"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** The "theme" settings: background and app accent. Phone-only, backed up with the app. */
data class ThemeSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Accent.Default)

class ThemeSettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<ThemeSettings> = store.data
        .map { prefs ->
            ThemeSettings(
                mode = prefs[MODE].toEnum(ThemeMode.SYSTEM),
                accent = prefs[ACCENT].toEnum(Accent.Default)
            )
        }
        .distinctUntilChanged()

    suspend fun setMode(mode: ThemeMode) {
        store.edit { it[MODE] = mode.name }
    }

    suspend fun setAccent(accent: Accent) {
        store.edit { it[ACCENT] = accent.name }
    }

    companion object {
        // Stored by enum name; an unknown name (a removed choice) falls back to the default.
        private val MODE = stringPreferencesKey("theme_mode")
        private val ACCENT = stringPreferencesKey("accent")
        private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

        fun create(context: Context) = ThemeSettingsStore(context.applicationContext.themeDataStore)
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
