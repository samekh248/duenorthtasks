package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.Assignment
import app.duenorth.tasks.provider.api.AssignmentSource
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteStep
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.google.api.GoogleStatus
import app.duenorth.tasks.provider.google.api.TaskDto
import app.duenorth.tasks.provider.google.api.TaskListDto
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/* Google Tasks <-> Due North field mapping, the counterpart of the Microsoft module's TodoMapping. */

internal fun TaskListDto.toRemote(isDefault: Boolean) = RemoteList(
    id = id,
    title = title,
    isDefault = isDefault,
    etag = etag,
    updatedAt = parseTimestamp(updated)
)

internal fun TaskDto.toRemote(listId: String, children: List<TaskDto>) = RemoteTask(
    id = id,
    listId = listId,
    title = title.orEmpty(),
    notes = notes,
    dueDate = parseDue(due),
    completed = status == GoogleStatus.COMPLETED,
    completedAt = completed?.let(::parseTimestamp),
    important = false,
    position = position,
    rawStatus = status,
    steps = children.map { RemoteStep(it.id, it.title.orEmpty(), it.status == GoogleStatus.COMPLETED) },
    etag = etag,
    updatedAt = parseTimestamp(updated),
    assignment = assignmentInfo?.let { info ->
        Assignment(
            source = when (info.surfaceType) {
                "DOCUMENT" -> AssignmentSource.DOCUMENT
                "SPACE" -> AssignmentSource.SPACE
                else -> AssignmentSource.OTHER
            },
            link = info.linkToTask?.takeIf { it.startsWith("https://") }
        )
    }
)

/** Google stores only the date; it sends and expects midnight UTC. */
internal fun formatDue(date: LocalDate): String = "${date}T00:00:00.000Z"

internal fun parseDue(value: String?): LocalDate? =
    value?.takeIf { it.length >= 10 }?.let { LocalDate.parse(it.substring(0, 10)) }

internal fun formatTimestamp(instant: Instant): String = instant.atOffset(ZoneOffset.UTC).toString()

internal fun parseTimestamp(value: String?): Instant =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: Instant.EPOCH
