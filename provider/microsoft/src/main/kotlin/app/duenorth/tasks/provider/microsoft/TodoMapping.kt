package app.duenorth.tasks.provider.microsoft

import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteStep
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.microsoft.graph.ChecklistItemDto
import app.duenorth.tasks.provider.microsoft.graph.DateTimeTimeZoneDto
import app.duenorth.tasks.provider.microsoft.graph.TodoTaskDto
import app.duenorth.tasks.provider.microsoft.graph.TodoTaskListDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * Due North <-> Microsoft To Do field mapping (task T052, research R7 and R8).
 *
 *   Due North           To Do (Graph todoTask)
 *   title            <-> title
 *   notes            <-> body.content (text; HTML bodies are read as plain text)
 *   dueDate          <-> dueDateTime: local midnight, sent as UTC like the To Do apps do
 *   completed        <-> status == "completed"; any other status is "open"
 *   rawStatus        <-  status, so un-completing can restore inProgress, waitingOnOthers, ...
 *   important        <-> importance == "high" (we toggle normal <-> high)
 *   steps            <-> checklistItems (displayName, isChecked)
 */

internal const val STATUS_COMPLETED = "completed"
internal const val STATUS_NOT_STARTED = "notStarted"

private val graphDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")

internal fun TodoTaskListDto.toRemote(now: Instant) = RemoteList(
    id = id,
    title = displayName,
    isDefault = wellknownListName == "defaultList",
    etag = etag,
    // Graph does not expose a modified time for lists.
    updatedAt = now
)

internal fun TodoTaskDto.toRemote(listId: String, zone: ZoneId, now: Instant, steps: List<ChecklistItemDto>?) =
    RemoteTask(
        id = id,
        listId = listId,
        title = title.orEmpty(),
        notes = body?.let {
            if (it.contentType.equals("html", ignoreCase = true)) htmlToText(it.content) else it.content
        }
            ?.trim()
            ?.takeIf { it.isNotEmpty() },
        dueDate = dueDateTime?.toLocalDate(zone),
        completed = status == STATUS_COMPLETED,
        completedAt = completedDateTime?.toInstant(),
        important = importance == "high",
        position = null,
        rawStatus = status,
        steps = (steps ?: checklistItems).orEmpty().map { RemoteStep(it.id, it.displayName, it.isChecked) },
        etag = etag,
        updatedAt = lastModifiedDateTime?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now
    )

/**
 * A To Do due date is a date-time, but the apps store "midnight on the due day" in the user's zone,
 * usually expressed in UTC (midnight in Chicago arrives as 05:00 UTC). Midnight in the stated zone
 * is taken as the date itself, so other clients writing a bare UTC midnight read correctly too;
 * anything else is converted to [zone] first.
 */
internal fun DateTimeTimeZoneDto.toLocalDate(zone: ZoneId): LocalDate? {
    val local = runCatching { LocalDateTime.parse(dateTime) }.getOrNull() ?: return null
    if (local.toLocalTime() == LocalTime.MIDNIGHT) return local.toLocalDate()
    val sourceZone = graphZone(timeZone) ?: return local.toLocalDate()
    return local.atZone(sourceZone).withZoneSameInstant(zone).toLocalDate()
}

internal fun DateTimeTimeZoneDto.toInstant(): Instant? {
    val local = runCatching { LocalDateTime.parse(dateTime) }.getOrNull() ?: return null
    return local.atZone(graphZone(timeZone) ?: ZoneOffset.UTC).toInstant()
}

/** Graph answers in UTC unless asked otherwise; Windows zone names are not mapped. */
private fun graphZone(name: String): ZoneId? =
    if (name.equals("UTC", ignoreCase = true)) ZoneOffset.UTC else runCatching { ZoneId.of(name) }.getOrNull()

/** Local midnight of [date] in [zone], written in UTC (what the To Do apps themselves send). */
internal fun dueDateTimeJson(date: LocalDate, zone: ZoneId): JsonObject = buildJsonObject {
    val utc = date.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    put("dateTime", graphDateTime.format(utc))
    put("timeZone", "UTC")
}

internal fun TaskDraft.toCreateBody(zone: ZoneId): JsonObject = buildJsonObject {
    put("title", title)
    notes?.takeIf { it.isNotEmpty() }?.let { putBody(it) }
    dueDate?.let { put("dueDateTime", dueDateTimeJson(it, zone)) }
    if (important) put("importance", "high")
}

/** Only the fields set in [patch] (FR-024); cleared fields are sent as empty or null. */
internal fun TaskPatch.toPatchBody(zone: ZoneId): JsonObject = buildJsonObject {
    title?.let { put("title", it) }
    when (val n = notes) {
        is Patch.Set -> putBody(n.value)
        Patch.Clear -> putBody("")
        null -> Unit
    }
    when (val d = dueDate) {
        is Patch.Set -> put("dueDateTime", dueDateTimeJson(d.value, zone))
        Patch.Clear -> put("dueDateTime", JsonNull)
        null -> Unit
    }
    completed?.let { done ->
        val status = if (done) {
            STATUS_COMPLETED
        } else {
            reopenStatus?.takeIf { it != STATUS_COMPLETED } ?: STATUS_NOT_STARTED
        }
        put("status", status)
    }
    important?.let { put("importance", if (it) "high" else "normal") }
}

private fun JsonObjectBuilder.putBody(text: String) = putJsonObject("body") {
    put("content", text)
    put("contentType", "text")
}

private val tags = Regex("<[^>]*>")
private val breaks = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>")

/** Rough HTML to text for bodies written by Outlook or the To Do web app. */
internal fun htmlToText(html: String): String = html
    .replace(breaks, "\n")
    .replace(tags, "")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .lines()
    .joinToString("\n") { it.trimEnd() }
    .replace(Regex("\n{3,}"), "\n\n")
