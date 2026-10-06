package app.duenorth.tasks.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The sync account page's "sync every" and "Wi-Fi only" choices (FR-027). Phone-only settings. */
data class SyncSettings(val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES, val wifiOnly: Boolean = false) {
    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15

        val INTERVAL_CHOICES = listOf(5, 10, 15, 30, 60, 120, 240)

        /**
         * WorkManager cannot run periodic work more often than every 15 minutes, so shorter choices
         * are a one-time job that schedules the next one ([SyncScheduler]). They cost battery, and
         * Android still stretches them while the phone sits idle (Doze).
         */
        const val SHORTEST_PERIODIC_MINUTES = 15

        fun isShort(minutes: Int) = minutes < SHORTEST_PERIODIC_MINUTES
    }
}

class SyncSettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<SyncSettings> = store.data
        .map { prefs ->
            SyncSettings(
                intervalMinutes = prefs[INTERVAL] ?: SyncSettings.DEFAULT_INTERVAL_MINUTES,
                wifiOnly = prefs[WIFI_ONLY] ?: false
            )
        }
        .distinctUntilChanged()

    suspend fun setInterval(minutes: Int) {
        require(minutes in SyncSettings.INTERVAL_CHOICES) { "Unsupported interval $minutes" }
        store.edit { it[INTERVAL] = minutes }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        store.edit { it[WIFI_ONLY] = wifiOnly }
    }

    companion object {
        private val INTERVAL = intPreferencesKey("sync_interval_minutes")
        private val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        private val Context.syncDataStore by preferencesDataStore(name = "sync_settings")

        fun create(context: Context) = SyncSettingsStore(context.applicationContext.syncDataStore)
    }
}
