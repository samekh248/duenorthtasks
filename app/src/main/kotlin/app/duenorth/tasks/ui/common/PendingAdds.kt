package app.duenorth.tasks.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shows a task the moment it is added, before its database write commits (constitution
 * Principle II): the ViewModel puts its row here under the id the write will use, and the row
 * stays until the database has it, so the hand-over is invisible. It also remembers which rows
 * were added here, so a touch hold that freezes the list still lets them in ([admit]).
 */
class PendingAdds {
    private val state = MutableStateFlow(Pending())

    /** Rows not in the database yet, newest first, and every id added on this screen. */
    val pending: StateFlow<Pending> = state.asStateFlow()

    data class Pending(val rows: List<TaskRowUi> = emptyList(), val added: Set<String> = emptySet())

    /** Remembers [id] as added here, and shows [row] (when the screen shows it at all) until it is stored. */
    fun add(id: String, row: TaskRowUi?) = state.update {
        Pending(rows = listOfNotNull(row) + it.rows, added = (it.added + id).toList().takeLast(MAX_ADDED).toSet())
    }

    /** Drops rows the database now has; from then on the stored row is the one shown. */
    fun settle(stored: Set<String>) = state.update { current ->
        if (current.rows.none { it.id in stored }) {
            current
        } else {
            current.copy(rows = current.rows.filterNot { it.id in stored })
        }
    }

    /** Ids of the rows shown that the database doesn't have yet. */
    fun waiting(): Set<String> = state.value.rows.mapTo(HashSet()) { it.id }

    /** The write failed or the task is gone: stop showing it. */
    fun clear(id: String) = state.update { current -> current.copy(rows = current.rows.filterNot { it.id == id }) }

    companion object {
        private const val MAX_ADDED = 50

        /**
         * [rows] with every pending row they don't have yet, each placed before the first row
         * [goesBefore] says it precedes (at the end when none), the way the stored row will sit.
         */
        fun merge(
            rows: List<TaskRowUi>,
            pending: List<TaskRowUi>,
            goesBefore: (added: TaskRowUi, row: TaskRowUi) -> Boolean
        ): List<TaskRowUi> {
            if (pending.isEmpty()) return rows
            val present = rows.mapTo(HashSet()) { it.id }
            var result = rows
            // Oldest first, so the newest ends up on top among equals, like its order key puts it.
            pending.asReversed().filterNot { it.id in present }.forEach { added ->
                val index = result.indexOfFirst { goesBefore(added, it) }.let { if (it < 0) result.size else it }
                result = result.toMutableList().apply { add(index, added) }
            }
            return result
        }

        /** [rows] showing the ticks in [overrides], so a tap on a row not stored yet shows at once. */
        fun ticked(rows: List<TaskRowUi>, overrides: Map<String, Boolean>): List<TaskRowUi> {
            if (overrides.isEmpty()) return rows
            return rows.map { row ->
                val completed = overrides[row.id] ?: return@map row
                row.copy(completed = completed, overdue = row.overdue && !completed)
            }
        }

        /**
         * A frozen [snapshot] (touch hold) with the rows of [live] whose ids are in [added] put in
         * where [live] has them: the user's own new task never waits for a finger to lift.
         */
        fun admit(snapshot: List<TaskRowUi>, live: List<TaskRowUi>, added: Set<String>): List<TaskRowUi> {
            if (added.isEmpty() || snapshot === live) return snapshot
            val present = snapshot.mapTo(HashSet()) { it.id }
            val missing = live.withIndex().filter { (_, row) -> row.id in added && row.id !in present }
            if (missing.isEmpty()) return snapshot
            val result = snapshot.toMutableList()
            missing.forEach { (index, row) -> result.add(index.coerceAtMost(result.size), row) }
            return result
        }
    }
}
