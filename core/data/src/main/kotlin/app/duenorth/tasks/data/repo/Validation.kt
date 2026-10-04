package app.duenorth.tasks.data.repo

/** Limits both providers accept (data-model.md, "Validation"); Google's are the tighter ones. */
object Validation {
    const val MAX_LIST_TITLE = 256
    const val MAX_TASK_TITLE = 1024
    const val MAX_NOTES = 8192
    const val MAX_STEPS = 100

    fun listTitle(title: String): String = title.trim().also {
        require(it.isNotEmpty()) { "List title is empty" }
        require(it.length <= MAX_LIST_TITLE) { "List title is longer than $MAX_LIST_TITLE characters" }
    }

    fun taskTitle(title: String): String = title.trim().also {
        require(it.isNotEmpty()) { "Task title is empty" }
        require(it.length <= MAX_TASK_TITLE) { "Task title is longer than $MAX_TASK_TITLE characters" }
    }

    fun stepTitle(title: String): String = taskTitle(title)

    /** Blank details are stored as no details. */
    fun notes(notes: String?): String? = notes?.trimEnd()?.takeIf { it.isNotBlank() }?.also {
        require(it.length <= MAX_NOTES) { "Details are longer than $MAX_NOTES characters" }
    }
}
