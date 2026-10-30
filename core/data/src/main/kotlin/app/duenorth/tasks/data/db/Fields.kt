package app.duenorth.tasks.data.db

import app.duenorth.tasks.provider.api.RemoteTask

/**
 * Field names used in [PendingOperationEntity.changedFields], and the one place that says which
 * synced fields differ between two versions of a task or step. A new synced field is added here.
 */
object Fields {
    const val TITLE = "title"
    const val NOTES = "notes"
    const val DUE_DATE = "dueDate"
    const val COMPLETED = "completed"
    const val IMPORTANT = "important"
    const val LIST = "list"
    const val DONE = "done"

    /** Never sent as a field; marks a task whose queued `MOVE` keeps its place over a pull's. */
    const val POSITION = "position"

    /** The task fields a full "send everything" update covers. */
    val TASK = setOf(TITLE, NOTES, DUE_DATE, COMPLETED, IMPORTANT)

    /** Fields that differ between two local versions of a task, its list included. */
    fun changed(a: TaskEntity, b: TaskEntity): Set<String> = buildSet {
        if (a.title != b.title) add(TITLE)
        if (a.notes != b.notes) add(NOTES)
        if (a.dueDate != b.dueDate) add(DUE_DATE)
        if (a.completed != b.completed) add(COMPLETED)
        if (a.important != b.important) add(IMPORTANT)
        if (a.listId != b.listId) add(LIST)
    }

    /** Fields that differ between two local versions of a step. */
    fun changed(a: StepEntity, b: StepEntity): Set<String> = buildSet {
        if (a.title != b.title) add(TITLE)
        if (a.done != b.done) add(DONE)
    }

    /**
     * Which of [fields] the phone's [local] task holds differently from the service's [remote]
     * copy. Importance counts only when the service can store it ([importance]).
     */
    fun differing(local: TaskEntity, remote: RemoteTask, fields: Set<String>, importance: Boolean): Set<String> =
        fields.filter { field ->
            when (field) {
                TITLE -> local.title != remote.title
                NOTES -> local.notes.orEmpty() != remote.notes.orEmpty()
                DUE_DATE -> local.dueDate != remote.dueDate
                COMPLETED -> local.completed != remote.completed
                IMPORTANT -> importance && local.important != remote.important
                else -> false
            }
        }.toSet()
}
