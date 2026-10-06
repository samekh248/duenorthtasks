package app.duenorth.tasks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * The order of lists (specs/003-reordering, FR-225 and data-model.md "List order"). Neither service
 * stores it, so it lives on the phone, one entry per service: remote list ids in order, or
 * `"local:<local id>"` for a list not created remotely yet, until [adopt] swaps in its remote id.
 * Like list shades, it outlives sign-out and switching services. Also holds the one-time "order
 * stays on this phone" flag (FR-231).
 */
class ListOrderStore(private val store: DataStore<Preferences>) {
    /** Stored entries by service. */
    val entries: Flow<Map<ProviderKind, List<String>>> = store.data
        .map { prefs ->
            ProviderKind.entries.mapNotNull { provider ->
                prefs[orderKey(provider)]?.let { provider to it.split(SEPARATOR).filter(String::isNotEmpty) }
            }.toMap()
        }
        .distinctUntilChanged()

    val noteShown: Flow<Boolean> = store.data.map { it[NOTE_SHOWN] == true }.distinctUntilChanged()

    suspend fun set(provider: ProviderKind, entries: List<String>) {
        store.edit { it[orderKey(provider)] = entries.joinToString(SEPARATOR) }
    }

    suspend fun markNoteShown() {
        store.edit { it[NOTE_SHOWN] = true }
    }

    /** Replaces local-id entries with remote ids for lists that now exist remotely. */
    suspend fun adopt(provider: ProviderKind, lists: List<ListKey>) {
        val remoteByLocal = lists.mapNotNull { list -> list.remoteId?.let { localKey(list.localId) to it } }.toMap()
        store.edit { prefs ->
            val current = prefs[orderKey(provider)]?.split(SEPARATOR) ?: return@edit
            prefs[orderKey(provider)] = current.map { remoteByLocal[it] ?: it }.distinct().joinToString(SEPARATOR)
        }
    }

    companion object {
        private const val SEPARATOR = "\n"
        private val NOTE_SHOWN = booleanPreferencesKey("order_note_shown")
        private val Context.orderDataStore by preferencesDataStore(name = "list_order")

        fun create(context: Context) = ListOrderStore(context.applicationContext.orderDataStore)

        fun localKey(localId: String) = "local:$localId"

        /** The entry a list is stored under right now. */
        fun entryFor(list: ListKey): String = list.remoteId ?: localKey(list.localId)

        /** Rank by local list id; lists with no entry are left out. */
        fun rank(entries: List<String>, lists: List<ListKey>): Map<String, Int> {
            val index = entries.withIndex().associate { (i, entry) -> entry to i }
            return lists.mapNotNull { list ->
                (list.remoteId?.let(index::get) ?: index[localKey(list.localId)])?.let { list.localId to it }
            }.toMap()
        }

        fun needsAdopt(entries: List<String>, lists: List<ListKey>): Boolean =
            lists.any { it.remoteId != null && localKey(it.localId) in entries }

        private fun orderKey(provider: ProviderKind) = stringPreferencesKey("order:${provider.name.lowercase()}")
    }
}

/** Resolves the stored list order to the lists on the phone now, for every place lists are shown. */
class ListOrder(
    private val store: ListOrderStore,
    private val tasks: TaskRepository,
    private val accounts: AccountRepository
) {
    /** Place by local list id. Lists missing from it (new ones) go after the others. */
    val rank: Flow<Map<String, Int>> =
        combine(store.entries, tasks.listKeys(), accounts.account.map { it?.provider }) { entries, lists, provider ->
            Triple(entries, lists, provider)
        }
            .onEach { (entries, lists, provider) ->
                val mine = provider?.let(entries::get) ?: return@onEach
                if (ListOrderStore.needsAdopt(mine, lists)) store.adopt(provider, lists)
            }
            .map { (entries, lists, provider) ->
                provider?.let(entries::get)?.let { ListOrderStore.rank(it, lists) }.orEmpty()
            }
            .distinctUntilChanged()

    val noteShown: Flow<Boolean> = store.noteShown

    suspend fun markNoteShown() = store.markNoteShown()

    /** Saves [localIds] as the order; ids that are not lists on the phone are skipped. */
    suspend fun set(localIds: List<String>) {
        val provider = accounts.current()?.provider ?: return
        val lists = tasks.listKeys().first().associateBy { it.localId }
        store.set(provider, localIds.mapNotNull { lists[it]?.let(ListOrderStore::entryFor) })
    }

    companion object {
        /** [items] in the stored order; unranked ones keep their current order after the ranked ones. */
        fun <T> sort(items: List<T>, rank: Map<String, Int>, id: (T) -> String): List<T> =
            if (rank.isEmpty()) items else items.sortedBy { rank[id(it)] ?: Int.MAX_VALUE }
    }
}
