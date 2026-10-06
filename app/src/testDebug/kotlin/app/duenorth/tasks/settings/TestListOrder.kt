package app.duenorth.tasks.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A list order store in its own temp file, for tests that build ViewModels by hand. */
fun testListOrderStore(): ListOrderStore {
    val dir = Files.createTempDirectory("list-order").toFile().apply { deleteOnExit() }
    return ListOrderStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(dir, "order.preferences_pb")
        }
    )
}

fun testListOrder(tasks: TaskRepository, accounts: AccountRepository, store: ListOrderStore = testListOrderStore()) =
    ListOrder(store, tasks, accounts)

/** List shades in their own temp file, for tests that build ViewModels by hand. */
fun testListShades(tasks: TaskRepository, accounts: AccountRepository): ListShades {
    val dir = Files.createTempDirectory("list-shades").toFile().apply { deleteOnExit() }
    val store = ListShadeStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(dir, "shades.preferences_pb")
        }
    )
    return ListShades(store, tasks, accounts)
}
