package app.duenorth.tasks.provider.microsoft.graph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Microsoft Graph v1.0 To Do resources, reduced to the fields Due North reads (research R7).
 * Unknown fields are ignored when reading, and writes are field-level PATCH bodies built in
 * TodoMapping.kt, so fields we do not model are never sent back (FR-024).
 */

/** One page of a Graph collection; delta pages end with [deltaLink] instead of [nextLink]. */
@Serializable
internal data class GraphPage<T>(
    val value: List<T> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null,
    @SerialName("@odata.deltaLink") val deltaLink: String? = null
)

@Serializable
internal data class TodoTaskListDto(
    val id: String,
    val displayName: String = "",
    /** "defaultList" for the built-in Tasks list, "flaggedEmails" for flagged mail, else "none". */
    val wellknownListName: String? = null,
    /** True when the list is shared with other people. Graph has no members list (spec 002, R2). */
    val isShared: Boolean = false,
    /** True when the signed-in user owns the list. */
    val isOwner: Boolean = true,
    @SerialName("@odata.etag") val etag: String? = null
)

@Serializable
internal data class TodoTaskDto(
    val id: String,
    val title: String? = null,
    val body: ItemBodyDto? = null,
    /** low, normal or high. */
    val importance: String? = null,
    /** notStarted, inProgress, completed, waitingOnOthers or deferred. */
    val status: String? = null,
    val lastModifiedDateTime: String? = null,
    val completedDateTime: DateTimeTimeZoneDto? = null,
    val dueDateTime: DateTimeTimeZoneDto? = null,
    /** Null when the response did not expand them (as opposed to an empty list). */
    val checklistItems: List<ChecklistItemDto>? = null,
    @SerialName("@odata.etag") val etag: String? = null,
    /** Present only in delta responses, on items deleted since the last delta link. */
    @SerialName("@removed") val removed: RemovedDto? = null
)

@Serializable
internal data class ItemBodyDto(val content: String = "", val contentType: String = "text")

/** A wall-clock time plus the zone it is in; Graph usually answers in "UTC". */
@Serializable
internal data class DateTimeTimeZoneDto(val dateTime: String, val timeZone: String = "UTC")

@Serializable
internal data class ChecklistItemDto(val id: String, val displayName: String = "", val isChecked: Boolean = false)

@Serializable
internal data class RemovedDto(val reason: String? = null)

@Serializable
internal data class GraphErrorBody(val error: GraphErrorDto? = null)

@Serializable
internal data class GraphErrorDto(val code: String? = null, val message: String? = null)

/** JSON batching: up to [MAX_BATCH] requests in one round trip. */
@Serializable
internal data class BatchRequestDto(val requests: List<BatchRequestItem>)

@Serializable
internal data class BatchRequestItem(
    val id: String,
    val method: String,
    /** Relative to the version root, for example "/me/todo/lists/{id}/tasks". */
    val url: String,
    val body: JsonElement? = null,
    val headers: Map<String, String>? = null,
    /** Graph runs batch requests in any order unless they depend on each other. */
    val dependsOn: List<String>? = null
)

@Serializable
internal data class BatchResponseDto(val responses: List<BatchResponseItem> = emptyList())

@Serializable
internal data class BatchResponseItem(
    val id: String,
    val status: Int,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null
)

/** Graph's limit on requests per `$batch` call. */
internal const val MAX_BATCH = 20
