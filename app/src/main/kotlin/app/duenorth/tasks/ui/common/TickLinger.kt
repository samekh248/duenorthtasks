package app.duenorth.tasks.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps a row the user just ticked or unticked in the section they tapped it in, showing its new
 * state, for [lingerMs] after the database moves it elsewhere (FR-006, SC-008). Without it a
 * ticked task on "today" can leave before the frame that draws its tick, so the tap shows nothing
 * but a row fading away.
 */
class TickLinger(private val scope: CoroutineScope, private val lingerMs: Long = LINGER_MS) {
    private val state = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Rows still lingering, by id, with the state they were ticked to. */
    val ticked: StateFlow<Map<String, Boolean>> = state.asStateFlow()

    fun ticked(id: String, completed: Boolean) {
        state.update { it + (id to completed) }
        scope.launch {
            delay(lingerMs)
            state.update { current -> if (current[id] == completed) current - id else current }
        }
    }

    companion object {
        /** Long enough to see the tick fill (100 ms) and register it, short enough not to feel stuck. */
        const val LINGER_MS = 600L

        /**
         * [rows] with every lingering row that [shown] had and [rows] lost put back where it was,
         * in its ticked state.
         */
        fun keep(shown: List<TaskRowUi>, rows: List<TaskRowUi>, ticked: Map<String, Boolean>): List<TaskRowUi> {
            if (ticked.isEmpty()) return rows
            val present = rows.mapTo(HashSet()) { it.id }
            val result = rows.toMutableList()
            shown.forEachIndexed { index, row ->
                val completed = ticked[row.id] ?: return@forEachIndexed
                if (row.id !in present) result.add(index.coerceAtMost(result.size), row.copy(completed = completed))
            }
            return result
        }
    }
}
