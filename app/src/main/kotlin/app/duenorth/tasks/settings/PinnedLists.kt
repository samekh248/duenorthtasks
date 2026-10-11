package app.duenorth.tasks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.duenorth.tasks.data.db.ListKey
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Lists pinned to the home panorama, each its own section between "today" and "lists", in the
 * order they were pinned. Neither service has such a thing, so like list order it lives on the
 * phone, one entry per service: remote list ids, or `"local:<local id>"` for a list not created
 * remotely yet, until [adopt] swaps in its remote id.
 */
class PinnedListStore(private val store: DataStore<Preferences>) {
    /** Stored entries by service, in pin order. */
    val entries: Flow<Map<ProviderKind, List<String>>> = store.data
        .map { prefs ->
            ProviderKind.entries.mapNotNull { provider ->
                prefs[pinsKey(provider)]?.let { provider to it.split(SEPARATOR).filter(String::isNotEmpty) }
            }.toMap()
        }
        .distinctUntilChanged()

    /** Adds [entry] after the lists already pinned; pinning it again changes nothing. */
    suspend fun pin(provider: ProviderKind, entry: String) {
        store.edit { prefs ->
            val current = prefs[pinsKey(provider)]?.split(SEPARATOR)?.filter(String::isNotEmpty).orEmpty()
            if (entry !in current) prefs[pinsKey(provider)] = (current + entry).joinToString(SEPARATOR)
        }
    }

    suspend fun unpin(provider: ProviderKind, entry: String) {
        store.edit { prefs ->
            val current = prefs[pinsKey(provider)]?.split(SEPARATOR) ?: return@edit
            prefs[pinsKey(provider)] = current.filter { it.isNotEmpty() && it != entry }.joinToString(SEPARATOR)
        }
    }

    /** Replaces local-id entries with remote ids for lists that now exist remotely. */
    suspend fun adopt(provider: ProviderKind, lists: List<ListKey>) {
        val remoteByLocal =
            lists.mapNotNull { list -> list.remoteId?.let { ListOrderStore.localKey(list.localId) to it } }.toMap()
        store.edit { prefs ->
            val current = prefs[pinsKey(provider)]?.split(SEPARATOR) ?: return@edit
            prefs[pinsKey(provider)] = current.map { remoteByLocal[it] ?: it }.distinct().joinToString(SEPARATOR)
        }
    }

    companion object {
        private const val SEPARATOR = "\n"
        private val Context.pinsDataStore by preferencesDataStore(name = "pinned_lists")

        fun create(context: Context) = PinnedListStore(context.applicationContext.pinsDataStore)

        /** Local ids of the pinned [lists] in pin order; entries for lists not on the phone are left out. */
        fun resolve(entries: List<String>, lists: List<ListKey>): List<String> {
            val byEntry = lists.associate { ListOrderStore.entryFor(it) to it.localId }
            return entries.mapNotNull(byEntry::get).distinct()
        }

        private fun pinsKey(provider: ProviderKind) = stringPreferencesKey("pins:${provider.name.lowercase()}")
    }
}

/** Resolves the stored pins to the lists on the phone now, for the home panorama and the list page. */
class PinnedLists(
    private val store: PinnedListStore,
    private val tasks: TaskRepository,
    private val accounts: AccountRepository
) {
    /** Local ids of the pinned lists, in the order they were pinned. A deleted list drops out. */
    val pinned: Flow<List<String>> =
        combine(store.entries, tasks.listKeys(), accounts.account.map { it?.provider }) { entries, lists, provider ->
            Triple(entries, lists, provider)
        }
            .onEach { (entries, lists, provider) ->
                val mine = provider?.let(entries::get) ?: return@onEach
                if (ListOrderStore.needsAdopt(mine, lists)) store.adopt(provider, lists)
            }
            .map { (entries, lists, provider) ->
                provider?.let(entries::get)?.let { PinnedListStore.resolve(it, lists) }.orEmpty()
            }
            .distinctUntilChanged()

    suspend fun pin(localId: String) = edit(localId, store::pin)

    suspend fun unpin(localId: String) = edit(localId, store::unpin)

    private suspend fun edit(localId: String, change: suspend (ProviderKind, String) -> Unit) {
        val provider = accounts.current()?.provider ?: return
        val list = tasks.list(localId).first() ?: return
        change(provider, ListOrderStore.entryFor(ListKey(list.localId, list.remoteId)))
    }
}
