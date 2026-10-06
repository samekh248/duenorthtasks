package app.duenorth.tasks.ui.templates

import androidx.compose.runtime.Immutable
import app.duenorth.tasks.data.repo.TemplateRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A task template offered under the "add a task" box (spec 004 FR-324). */
@Immutable
data class TaskTemplatePick(
    val id: String,
    val title: String,
    val caption: String?,
    val details: String?,
    val dueOffset: Int?
)

/** Task templates for the picker, most recently used first, then by name. */
fun taskTemplatePicks(templates: TemplateRepository, importance: Flow<Boolean>): Flow<List<TaskTemplatePick>> =
    combine(templates.taskTemplates(), importance) { all, important ->
        all.sortedByDescending { it.task.lastUsedAt ?: Instant.EPOCH }.map {
            val row = it.toRow(inList = false, importance = important)
            TaskTemplatePick(it.task.id, it.task.title, row.caption, it.task.notes, it.task.dueOffsetDays)
        }
    }
