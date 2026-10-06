package app.duenorth.tasks.data.repo

import androidx.room.withTransaction
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.TemplateListEntity
import app.duenorth.tasks.data.db.TemplateListSummary
import app.duenorth.tasks.data.db.TemplateStepEntity
import app.duenorth.tasks.data.db.TemplateTaskEntity
import app.duenorth.tasks.data.db.TemplateTaskSummary
import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.provider.api.Patch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * List and task templates (specs/004-templates). Templates live in the phone's database only and
 * are never queued for sync; using one hands ordinary new lists and tasks to [tasks], which saves
 * and queues them like hand-made ones.
 */
class TemplateRepository(
    private val db: DueNorthDatabase,
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val templates = db.templateDao()

    // Reads

    fun listTemplates(): Flow<List<TemplateListSummary>> = templates.observeLists()

    fun taskTemplates(): Flow<List<TemplateTaskSummary>> = templates.observeTaskTemplates()

    fun listTemplate(id: String): Flow<TemplateListEntity?> = templates.observeList(id)

    fun tasksIn(listTemplateId: String): Flow<List<TemplateTaskSummary>> = templates.observeTasksIn(listTemplateId)

    fun templateTask(id: String): Flow<TemplateTaskEntity?> = templates.observeTask(id)

    fun steps(templateTaskId: String): Flow<List<TemplateStepEntity>> = templates.observeSteps(templateTaskId)

    // Making templates

    suspend fun createListTemplate(name: String): String {
        val clean = Validation.listTitle(name)
        val id = newId()
        db.withTransaction { templates.insertList(TemplateListEntity(id = id, name = clean, updatedAt = now())) }
        return id
    }

    /** A task template on its own, or a task added to list template [listTemplateId]. */
    suspend fun createTemplateTask(title: String, listTemplateId: String? = null): String {
        val clean = Validation.taskTitle(title)
        val id = newId()
        db.withTransaction {
            val order = listTemplateId?.let { listId ->
                val list = requireList(listId)
                check(templates.tasksIn(listId).size < Validation.MAX_TEMPLATE_TASKS) {
                    "A list template has at most ${Validation.MAX_TEMPLATE_TASKS} tasks"
                }
                touch(list)
                templates.maxTaskOrder(listId) + 1
            } ?: 0
            templates.insertTasks(
                listOf(TemplateTaskEntity(id = id, templateListId = listTemplateId, title = clean, sortOrder = order))
            )
        }
        return id
    }

    /**
     * Copies task [taskId]'s title, details, steps (unticked) and flag into a new task template,
     * with no due offset (spec 004 US3). The task itself is left as it is.
     */
    suspend fun saveTaskAsTemplate(taskId: String): String {
        val id = newId()
        db.withTransaction {
            val task = db.taskDao().get(taskId)?.takeUnless { it.deletedLocally }
                ?: throw NoSuchElementException("No task $taskId")
            templates.insertTasks(
                listOf(TemplateTaskEntity(id = id, title = task.title, notes = task.notes, important = task.important))
            )
            templates.insertSteps(
                stepCopies(
                    id,
                    db.stepDao().forTask(taskId).filterNot {
                        it.deletedLocally
                    }.map { it.title }
                )
            )
        }
        return id
    }

    /**
     * Copies list [listId] into a new list template: its name, [shadeStep], and every task, open
     * ones in "my order" then completed ones, all unticked (spec 004 US3). Due offsets count from
     * the earliest due date in the list, which becomes the start day. At most
     * [Validation.MAX_TEMPLATE_TASKS] tasks are kept; the result says how many were left out.
     */
    suspend fun saveListAsTemplate(listId: String, shadeStep: Int = 0): Saved {
        val id = newId()
        var left = 0
        db.withTransaction {
            val list = db.taskListDao().get(listId)?.takeUnless { it.deletedLocally }
                ?: throw NoSuchElementException("No list $listId")
            val taskDao = db.taskDao()
            val all = taskDao.openInList(listId).sortedWith(OrderKeys.taskComparator) + taskDao.completedInList(listId)
            val kept = all.take(Validation.MAX_TEMPLATE_TASKS)
            left = all.size - kept.size
            val start = kept.mapNotNull { it.dueDate }.minOrNull()
            templates.insertList(
                TemplateListEntity(id = id, name = list.title, shadeStep = shadeStep, updatedAt = now())
            )
            val copies = kept.mapIndexed { index, task ->
                TemplateTaskEntity(
                    id = newId(),
                    templateListId = id,
                    title = task.title,
                    notes = task.notes,
                    important = task.important,
                    dueOffsetDays = task.dueDate?.let { due ->
                        start?.let { ChronoUnit.DAYS.between(it, due).toInt() }
                    },
                    sortOrder = index
                )
            }
            templates.insertTasks(copies)
            val stepDao = db.stepDao()
            copies.zip(kept).forEach { (copy, task) ->
                templates.insertSteps(
                    stepCopies(
                        copy.id,
                        stepDao.forTask(task.localId).filterNot {
                            it.deletedLocally
                        }.map { it.title }
                    )
                )
            }
        }
        return Saved(id, left)
    }

    data class Saved(val id: String, val leftOut: Int)

    // Editing templates

    suspend fun renameListTemplate(id: String, name: String) {
        val clean = Validation.listTitle(name)
        db.withTransaction { requireList(id).let { templates.updateList(it.copy(name = clean, updatedAt = now())) } }
    }

    suspend fun setListTemplateShade(id: String, step: Int) = db.withTransaction {
        requireList(id).let { templates.updateList(it.copy(shadeStep = step, updatedAt = now())) }
    }

    suspend fun deleteListTemplate(id: String) = db.withTransaction { templates.deleteList(id) }

    /** Applies the non-null parts; [dueOffset] is checked against the template's kind (FR-301, FR-302). */
    suspend fun editTemplateTask(
        id: String,
        title: String? = null,
        notes: Patch<String>? = null,
        important: Boolean? = null,
        dueOffset: Patch<Int>? = null
    ) = db.withTransaction {
        val task = requireTask(id)
        var updated = task
        title?.let { updated = updated.copy(title = Validation.taskTitle(it)) }
        notes?.let {
            updated = updated.copy(
                notes = when (it) {
                    is Patch.Set -> Validation.notes(it.value)
                    Patch.Clear -> null
                }
            )
        }
        important?.let { updated = updated.copy(important = it) }
        dueOffset?.let {
            updated = updated.copy(
                dueOffsetDays = when (it) {
                    is Patch.Set -> checkOffset(it.value, inList = task.templateListId != null)
                    Patch.Clear -> null
                }
            )
        }
        if (updated != task) {
            templates.updateTask(updated)
            touchListOf(task)
        }
    }

    suspend fun deleteTemplateTask(id: String) = db.withTransaction {
        templates.getTask(id)?.let {
            templates.deleteTask(id)
            touchListOf(it)
        }
    }

    /** Puts list template [listTemplateId]'s tasks in [order] (ids; ones not named keep their place after them). */
    suspend fun moveTemplateTasks(listTemplateId: String, order: List<String>) = db.withTransaction {
        renumber(templates.tasksIn(listTemplateId), order, { it.id }, { it.sortOrder }) { task, index ->
            templates.updateTask(task.copy(sortOrder = index))
        }
    }

    suspend fun addTemplateStep(taskId: String, title: String): String {
        val clean = Validation.stepTitle(title)
        val id = newId()
        db.withTransaction {
            val task = requireTask(taskId)
            val current = templates.stepsFor(taskId)
            check(current.size < Validation.MAX_STEPS) { "A task has at most ${Validation.MAX_STEPS} steps" }
            templates.insertSteps(
                listOf(
                    TemplateStepEntity(
                        id,
                        taskId,
                        clean,
                        (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    )
                )
            )
            touchListOf(task)
        }
        return id
    }

    suspend fun renameTemplateStep(id: String, title: String) {
        val clean = Validation.stepTitle(title)
        db.withTransaction { templates.getStep(id)?.let { templates.updateStep(it.copy(title = clean)) } }
    }

    suspend fun removeTemplateStep(id: String) = db.withTransaction { templates.deleteStep(id) }

    suspend fun moveTemplateSteps(taskId: String, order: List<String>) = db.withTransaction {
        renumber(templates.stepsFor(taskId), order, { it.id }, { it.sortOrder }) { step, index ->
            templates.updateStep(step.copy(sortOrder = index))
        }
    }

    // Using templates

    /**
     * Makes a new list from list template [id] named [name], every due date [start] plus the
     * task's offset (spec 004 US1). Returns the new list's local id. The template is unchanged.
     */
    suspend fun useListTemplate(id: String, name: String, start: LocalDate): String {
        val newTasks = db.withTransaction {
            requireList(id)
            val rows = templates.tasksIn(id)
            val steps = stepsByTask(rows.map { it.id })
            rows.map { it.toNewTask(start, steps[it.id].orEmpty()) }
        }
        return tasks.createListWithTasks(name, newTasks)
    }

    /**
     * Adds task template [id] to [listId], due [today] plus its offset, under [taskId] so the screen
     * can show it first (spec 004 US2). Marks the template as just used.
     */
    suspend fun useTaskTemplate(id: String, listId: String, today: LocalDate, taskId: String): String {
        val newTask = db.withTransaction {
            val template = requireTask(id)
            templates.updateTask(template.copy(lastUsedAt = now()))
            template.toNewTask(today, templates.stepsFor(id).map { it.title })
        }
        return tasks.createTaskWithSteps(listId, newTask, taskId)
    }

    // Helpers

    private fun now(): Instant = clock.instant()

    private fun TemplateTaskEntity.toNewTask(from: LocalDate, steps: List<String>) = NewTask(
        title = title,
        notes = notes,
        dueDate = dueOffsetDays?.let { from.plusDays(it.toLong()) },
        important = important,
        steps = steps
    )

    private suspend fun stepsByTask(taskIds: List<String>): Map<String, List<String>> =
        taskIds.chunked(CHUNK).flatMap { templates.stepsForTasks(it) }
            .groupBy({ it.templateTaskId }, { it.title })

    private fun stepCopies(taskId: String, titles: List<String>) =
        titles.take(Validation.MAX_STEPS).mapIndexed { index, title ->
            TemplateStepEntity(newId(), taskId, title, index)
        }

    private fun checkOffset(days: Int, inList: Boolean): Int {
        val range = if (inList) -MAX_OFFSET..MAX_OFFSET else 0..MAX_OFFSET
        require(days in range) { "Due offset $days is outside $range" }
        return days
    }

    private suspend fun touch(list: TemplateListEntity) = templates.updateList(list.copy(updatedAt = now()))

    private suspend fun touchListOf(task: TemplateTaskEntity) {
        task.templateListId?.let { templates.getList(it) }?.let { touch(it) }
    }

    private suspend fun requireList(id: String): TemplateListEntity =
        templates.getList(id) ?: throw NoSuchElementException("No list template $id")

    private suspend fun requireTask(id: String): TemplateTaskEntity =
        templates.getTask(id) ?: throw NoSuchElementException("No template task $id")

    private inline fun <T> renumber(
        rows: List<T>,
        order: List<String>,
        id: (T) -> String,
        sortOrder: (T) -> Int,
        save: (T, Int) -> Unit
    ) {
        val byId = rows.associateBy(id)
        val named = order.mapNotNull { byId[it] }
        val wanted = order.toSet()
        (named + rows.filter { id(it) !in wanted }).forEachIndexed { index, row ->
            if (sortOrder(row) != index) save(row, index)
        }
    }

    companion object {
        /** Offsets go up to a year either way. */
        const val MAX_OFFSET = 365
        private const val CHUNK = 900
    }
}
