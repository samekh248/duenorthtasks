package app.duenorth.tasks.badge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Whether the app icon shows today's count, and whether the app has asked for the notification
 * permission it needs on Android 13+. Phone-only, like the theme.
 */
class TodayBadgeSettings(private val store: DataStore<Preferences>) {
    /** On unless the person turns it off; the badge still needs notifications to be allowed. */
    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    /** True the first time only, and from then on false: the app asks for the permission once. */
    suspend fun takeFirstAsk(): Boolean {
        if (store.data.first()[ASKED] == true) return false
        var first = false
        store.edit {
            first = it[ASKED] != true
            it[ASKED] = true
        }
        return first
    }

    /** Asked for the permission before, from the launch ask or the settings toggle. */
    suspend fun asked(): Boolean = store.data.first()[ASKED] == true

    suspend fun markAsked() {
        store.edit { it[ASKED] = true }
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("today_badge")
        private val ASKED = booleanPreferencesKey("notifications_asked")
        private val Context.badgeDataStore by preferencesDataStore(name = "today_badge")

        fun create(context: Context) = TodayBadgeSettings(context.applicationContext.badgeDataStore)
    }
}
