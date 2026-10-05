package app.duenorth.tasks.settings

import app.duenorth.tasks.data.db.ListKey
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Resolves stored shade keys to the lists on the phone now (T062). */
class ListShades(
    private val store: ListShadeStore,
    private val tasks: TaskRepository,
    private val accounts: AccountRepository
) {
    private val current =
        combine(store.steps, tasks.listKeys(), accounts.account.map { it?.provider }) { steps, lists, provider ->
            Triple(steps, lists, provider)
        }

    /** Shade step by local list id, for lists that have one (missing means the app accent). */
    val byList: Flow<Map<String, Int>> = current
        .onEach { (steps, lists, provider) ->
            if (provider != null && ListShadeStore.needsAdopt(steps, lists)) store.adopt(provider, lists)
        }
        .map { (steps, lists, provider) ->
            if (provider == null) return@map emptyMap()
            lists.mapNotNull { list ->
                ListShadeStore.stepFor(steps, provider, list).takeIf { it != 0 }?.let { list.localId to it }
            }.toMap()
        }
        .distinctUntilChanged()

    suspend fun set(localId: String, step: Int) {
        val provider = accounts.current()?.provider ?: return
        val list = tasks.list(localId).first() ?: return
        store.set(ListShadeStore.key(provider, ListKey(list.localId, list.remoteId)), step)
    }
}
