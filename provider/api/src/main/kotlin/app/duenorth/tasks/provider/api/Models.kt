package app.duenorth.tasks.provider.api

import java.time.Instant
import java.time.LocalDate

data class RemoteList(
    val id: String,
    val title: String,
    val isDefault: Boolean,
    val etag: String?,
    val updatedAt: Instant
)

data class RemoteTask(
    val id: String,
    val listId: String,
    val title: String,
    /** Shown as "details" in the UI. */
    val notes: String?,
    val dueDate: LocalDate?,
    val completed: Boolean,
    val completedAt: Instant?,
    val important: Boolean,
    /** Provider ordering key, when the provider has one. */
    val position: String?,
    /** The provider's own status value, kept so un-completing restores it (To Do has five). */
    val rawStatus: String?,
    val steps: List<RemoteStep>,
    val etag: String?,
    val updatedAt: Instant
)

data class RemoteStep(val id: String, val title: String, val done: Boolean)

data class TaskDraft(
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val important: Boolean = false,
    val steps: List<StepDraft> = emptyList()
)

data class StepDraft(val title: String, val done: Boolean = false)

data class ListPatch(val title: String? = null)

/** Every field is optional; only non-null fields are sent (PATCH semantics, FR-024). */
data class TaskPatch(
    val title: String? = null,
    val notes: Patch<String>? = null,
    val dueDate: Patch<LocalDate>? = null,
    val completed: Boolean? = null,
    val important: Boolean? = null,
    val steps: List<StepPatch>? = null
) {
    val isEmpty: Boolean
        get() = title == null && notes == null && dueDate == null && completed == null && important == null &&
            steps.isNullOrEmpty()
}

/** A change to an optional field: set it, or clear it. */
sealed interface Patch<out T> {
    data class Set<T>(val value: T) : Patch<T>

    data object Clear : Patch<Nothing>
}

sealed interface StepPatch {
    data class Add(val title: String, val done: Boolean = false) : StepPatch

    data class Update(val id: String, val title: String? = null, val done: Boolean? = null) : StepPatch

    data class Remove(val id: String) : StepPatch
}

data class TaskChangePage(
    val changed: List<RemoteTask>,
    val deletedIds: List<String>,
    val nextCursor: String,
    val hasMore: Boolean
)
