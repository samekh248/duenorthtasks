package app.duenorth.tasks.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Ticks the box in the same frame as the tap (FR-006): the ViewModel shows the new state from here
 * until the database row catches up, then forgets it.
 */
class CompletionOverrides {
    private val state = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val overrides: StateFlow<Map<String, Boolean>> = state.asStateFlow()

    fun set(id: String, completed: Boolean) = state.update { it + (id to completed) }

    /** Drops overrides the database now agrees with, or whose rows are gone from [stored]. */
    fun settle(stored: Map<String, Boolean>) = state.update { current ->
        current.filter { (id, value) -> id in stored && stored[id] != value }
    }

    fun clear(id: String) = state.update { it - id }
}
