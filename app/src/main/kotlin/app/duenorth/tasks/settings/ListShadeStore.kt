package app.duenorth.tasks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.duenorth.tasks.data.db.ListKey
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.provider.api.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Per-list shade steps (data-model.md, "List shades"). Neither service stores list colors, so they
 * live only on the phone, keyed by `"<provider>:<remote list id>"`. That key outlives the Room
 * rows: sign out, switch service and come back, and the list gets its shade again. Lists not yet
 * created remotely use `"local:<local id>"` until [adopt] moves them after the first push.
 * A missing key is step 0, the app accent.
 */
class ListShadeStore(private val store: DataStore<Preferences>) {
    /** Every stored step by shade key. */
    val steps: Flow<Map<String, Int>> = store.data
        .map { prefs ->
            prefs.asMap().entries
                .filter { it.key.name.startsWith(PREFIX) }
                .associate { it.key.name.removePrefix(PREFIX) to it.value as Int }
        }
        .distinctUntilChanged()

    /** Sets the shade under [key]; step 0 removes it so the list follows the app accent. */
    suspend fun set(key: String, step: Int) {
        require(step in AccentShades.steps) { "Shade step $step is outside ${AccentShades.steps}" }
        store.edit { prefs ->
            if (step == 0) prefs.remove(prefKey(key)) else prefs[prefKey(key)] = step
        }
    }

    /** Moves local-id keys to remote-id keys for lists that now exist remotely. */
    suspend fun adopt(provider: ProviderKind, lists: List<ListKey>) {
        store.edit { prefs ->
            for (list in lists) {
                val remote = list.remoteId ?: continue
                val step = prefs[prefKey(localKey(list.localId))] ?: continue
                prefs[prefKey(remoteKey(provider, remote))] = step
                prefs.remove(prefKey(localKey(list.localId)))
            }
        }
    }

    companion object {
        private const val PREFIX = "shade:"
        private val Context.shadeDataStore by preferencesDataStore(name = "list_shades")

        fun create(context: Context) = ListShadeStore(context.applicationContext.shadeDataStore)

        fun remoteKey(provider: ProviderKind, remoteId: String) = "${provider.name.lowercase()}:$remoteId"

        fun localKey(localId: String) = "local:$localId"

        /** The key a list's shade is stored under right now. */
        fun key(provider: ProviderKind, list: ListKey): String =
            list.remoteId?.let { remoteKey(provider, it) } ?: localKey(list.localId)

        /** The list's step: its remote key, else its local key (before [adopt] runs), else 0. */
        fun stepFor(steps: Map<String, Int>, provider: ProviderKind, list: ListKey): Int =
            list.remoteId?.let { steps[remoteKey(provider, it)] } ?: steps[localKey(list.localId)] ?: 0

        /** True when some list with a remote id still has its shade under its local key. */
        fun needsAdopt(steps: Map<String, Int>, lists: List<ListKey>): Boolean =
            lists.any { it.remoteId != null && localKey(it.localId) in steps }

        private fun prefKey(key: String) = intPreferencesKey(PREFIX + key)
    }
}
