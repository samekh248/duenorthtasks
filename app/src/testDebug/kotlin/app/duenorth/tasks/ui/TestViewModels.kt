package app.duenorth.tasks.ui

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.RoomDatabase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.robolectric.Shadows.shadowOf

/**
 * The view models a test built by hand. Nothing clears them, so their flows would keep querying after the test
 * closes its in-memory database, and a query caught mid-flight throws into whichever test runs next.
 */
private val built = mutableListOf<ViewModel>()

/** Keeps [viewModel] so [closeAfterViewModels] can stop it before the database closes. */
fun <T : ViewModel> track(viewModel: T): T = viewModel.also { synchronized(built) { built += it } }

/**
 * Stops every [track]ed view model and waits for its queries to finish, running the main looper so cancelled
 * collectors can complete, then closes [db].
 */
fun closeAfterViewModels(db: RoomDatabase, timeoutMs: Long = 5_000) {
    val jobs = synchronized(built) { built.map { it.viewModelScope.coroutineContext.job }.also { built.clear() } }
    jobs.forEach { it.cancel() }
    val deadline = System.currentTimeMillis() + timeoutMs
    while (jobs.any { !it.isCompleted } && System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(5)
    }
    db.close()
}
