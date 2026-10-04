package app.duenorth.tasks.provider.google

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers

/**
 * A small, stateful stand-in for the Google Tasks v1 REST API, behind MockWebServer.
 *
 * It answers with the same JSON shapes the real service sends (see the recorded responses in
 * `src/test/resources/google/`): server-assigned ids, RFC 3339 `updated` stamps, zero-padded
 * `position` strings, subtasks as tasks with a `parent`, deletions kept as `deleted: true` and
 * shown only with `showDeleted`, `updatedMin` filtering and paging. Its clock moves one second per
 * request so "changed since" is exact, and every response carries a `Date` header.
 */
class FakeGoogleTasksServer(
    private val pageSize: Int = 100,
    /** Real accounts always have one; the shared contract starts from an empty account. */
    withDefaultList: Boolean = true
) : AutoCloseable {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }

    private var now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private var nextId = 1
    private val lists = linkedMapOf<String, ListRow>()
    private val tasks = linkedMapOf<String, TaskRow>()
    private val failures = ArrayDeque<MockResponse>()

    /** Every request received, in order. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** Bearer tokens seen, so tests can check that a refreshed token was used. */
    val tokens = CopyOnWriteArrayList<String?>()

    private data class ListRow(val id: String, var title: String, var updated: Instant)

    private data class TaskRow(
        val id: String,
        var listId: String,
        var title: String,
        var notes: String?,
        var status: String,
        var due: String?,
        var completed: Instant?,
        var updated: Instant,
        var parent: String?,
        var order: Long,
        var deleted: Boolean = false
    )

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = synchronized(this@FakeGoogleTasksServer) {
                requests += request
                tokens += request.headers["Authorization"]?.removePrefix("Bearer ")
                now = now.plusSeconds(1)
                failures.removeFirstOrNull()?.let { return it }
                handle(request)
            }
        }
        server.start()
        // The account always has a default list, as on Google.
        if (withDefaultList) lists["default"] = ListRow("default", "My Tasks", now)
    }

    val baseUrl: String get() = server.url("/tasks/v1/").toString()

    /** The next request gets [response] instead of being served. */
    fun failNext(response: MockResponse) {
        synchronized(this) { failures.addLast(response) }
    }

    fun failNext(code: Int, body: String = errorBody(code, "error"), headers: Map<String, String> = emptyMap()) =
        failNext(MockResponse(code, Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build(), body))

    /** Edits a task as if on the web, bypassing the app. Keys follow the Tasks API. */
    fun editOnWeb(taskId: String, title: String? = null, status: String? = null) = synchronized(this) {
        now = now.plusSeconds(1)
        val task = checkNotNull(tasks[taskId])
        title?.let { task.title = it }
        status?.let { task.status = it }
        task.updated = now
    }

    /** Makes [taskId] a subtask of [parentId], as dragging it under another task on the web does. */
    fun indentOnWeb(taskId: String, parentId: String) = synchronized(this) {
        now = now.plusSeconds(1)
        val task = checkNotNull(tasks[taskId])
        task.parent = parentId
        task.updated = now
    }

    fun deleteOnWeb(taskId: String) = synchronized(this) {
        now = now.plusSeconds(1)
        val task = checkNotNull(tasks[taskId])
        task.deleted = true
        task.updated = now
    }

    fun liveTasks(listId: String): List<String> = synchronized(this) {
        tasks.values.filter { it.listId == listId && !it.deleted }.map { it.title }
    }

    override fun close() = server.close()

    // Routing

    private fun handle(request: RecordedRequest): MockResponse {
        val url = request.url
        val path = url.encodedPathSegments.drop(2) // after tasks/v1
        val method = request.method
        val body = request.body?.utf8()?.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it).jsonObject }
        return when {
            path == listOf("users", "@me", "lists") && method == "GET" -> getLists(url.queryParameter("pageToken"))
            path == listOf("users", "@me", "lists") && method == "POST" -> insertList(checkNotNull(body))
            path.size == 4 && path[0] == "users" && method == "PATCH" -> patchList(path[3], checkNotNull(body))
            path.size == 4 && path[0] == "users" && method == "DELETE" -> deleteList(path[3])
            path.size == 3 && path[0] == "lists" && path[2] == "tasks" && method == "GET" -> listTasks(path[1], request)
            path.size == 3 && path[0] == "lists" && path[2] == "tasks" && method == "POST" ->
                insertTask(path[1], checkNotNull(body), url.queryParameter("parent"), url.queryParameter("previous"))
            path.size == 4 && method == "GET" -> getTask(path[1], path[3])
            path.size == 4 && method == "PATCH" -> patchTask(path[1], path[3], checkNotNull(body))
            path.size == 4 && method == "DELETE" -> deleteTask(path[1], path[3])
            path.size == 5 && path[4] == "move" && method == "POST" ->
                moveTask(path[1], path[3], url.queryParameter("parent"), url.queryParameter("previous"))
            else -> error(404, "Not Found")
        }
    }

    // Lists

    private fun getLists(pageToken: String?): MockResponse {
        val offset = pageToken?.toInt() ?: 0
        val all = lists.values.toList()
        val page = all.drop(offset).take(pageSize)
        return ok(
            buildJsonObject {
                put("kind", "tasks#taskLists")
                put("etag", "\"lists-$now\"")
                if (offset + page.size < all.size) put("nextPageToken", (offset + page.size).toString())
                put("items", JsonArray(page.map { it.toJson() }))
            }
        )
    }

    private fun insertList(body: JsonObject): MockResponse {
        val row = ListRow(newId(), body.string("title").orEmpty(), now)
        lists[row.id] = row
        return ok(row.toJson())
    }

    private fun patchList(id: String, body: JsonObject): MockResponse {
        val row = lists[id] ?: return error(404, "Task list not found.")
        body.string("title")?.let { row.title = it }
        row.updated = now
        return ok(row.toJson())
    }

    private fun deleteList(id: String): MockResponse {
        lists.remove(id) ?: return error(404, "Task list not found.")
        tasks.values.removeAll { it.listId == id }
        return MockResponse(204, Headers.headersOf("Date", httpDate()), "")
    }

    // Tasks

    private fun listTasks(listId: String, request: RecordedRequest): MockResponse {
        if (listId !in lists) return error(404, "Task list not found.")
        val url = request.url
        val updatedMin = url.queryParameter("updatedMin")?.let { OffsetDateTime.parse(it).toInstant() }
        val showDeleted = url.queryParameter("showDeleted") == "true"
        val showCompleted = url.queryParameter("showCompleted") != "false"
        val offset = url.queryParameter("pageToken")?.toInt() ?: 0
        val max = minOf(url.queryParameter("maxResults")?.toInt() ?: 20, pageSize)
        val matching = tasks.values
            .filter { it.listId == listId }
            .filter { showDeleted || !it.deleted }
            .filter { showCompleted || it.status != GoogleStatus.COMPLETED }
            .filter { updatedMin == null || !it.updated.isBefore(updatedMin) }
            .sortedWith(compareBy({ it.parent ?: it.id }, { it.parent != null }, { it.order }))
        val page = matching.drop(offset).take(max)
        return ok(
            buildJsonObject {
                put("kind", "tasks#tasks")
                put("etag", "\"tasks-$now\"")
                if (offset + page.size < matching.size) put("nextPageToken", (offset + page.size).toString())
                put("items", JsonArray(page.map { it.toJson() }))
            }
        )
    }

    private fun getTask(listId: String, id: String): MockResponse {
        val row = tasks[id]?.takeIf { it.listId == listId && !it.deleted } ?: return error(404, "Task not found.")
        return ok(row.toJson())
    }

    private fun insertTask(listId: String, body: JsonObject, parent: String?, previous: String?): MockResponse {
        if (listId !in lists) return error(404, "Task list not found.")
        if (parent != null && tasks[parent]?.takeIf { !it.deleted } == null) return error(404, "Parent not found.")
        val status = body.string("status") ?: GoogleStatus.NEEDS_ACTION
        val row = TaskRow(
            id = newId(),
            listId = listId,
            title = body.string("title").orEmpty(),
            notes = body.string("notes"),
            status = status,
            due = body.string("due")?.let(::normalizeDue),
            completed = if (status == GoogleStatus.COMPLETED) now else null,
            updated = now,
            parent = parent,
            order = 0
        )
        tasks[row.id] = row
        place(row, previous)
        return ok(row.toJson())
    }

    private fun patchTask(listId: String, id: String, body: JsonObject): MockResponse {
        val row = tasks[id]?.takeIf { it.listId == listId && !it.deleted } ?: return error(404, "Task not found.")
        for ((key, value) in body) {
            val text = if (value is JsonNull) null else value.jsonPrimitive.content
            when (key) {
                "title" -> row.title = text.orEmpty()
                "notes" -> row.notes = text
                "due" -> row.due = text?.let(::normalizeDue)
                "status" -> {
                    row.status = text ?: GoogleStatus.NEEDS_ACTION
                    row.completed = if (row.status == GoogleStatus.COMPLETED) row.completed ?: now else null
                }
                "completed" -> if (text == null) row.completed = null
            }
        }
        row.updated = now
        return ok(row.toJson())
    }

    private fun moveTask(listId: String, id: String, parent: String?, previous: String?): MockResponse {
        val row = tasks[id]?.takeIf { it.listId == listId && !it.deleted } ?: return error(404, "Task not found.")
        row.parent = parent
        row.updated = now
        place(row, previous)
        return ok(row.toJson())
    }

    private fun deleteTask(listId: String, id: String): MockResponse {
        val row = tasks[id]?.takeIf { it.listId == listId && !it.deleted } ?: return error(404, "Task not found.")
        row.deleted = true
        row.updated = now
        tasks.values.filter { it.parent == id }.forEach {
            it.deleted = true
            it.updated = now
        }
        return MockResponse(204, Headers.headersOf("Date", httpDate()), "")
    }

    /** Puts [row] just after [previous] among its siblings, or first when null, like Google. */
    private fun place(row: TaskRow, previous: String?) {
        val siblings = tasks.values
            .filter { it.listId == row.listId && it.parent == row.parent && !it.deleted && it.id != row.id }
            .sortedBy { it.order }
            .toMutableList()
        val index = previous?.let { prev -> siblings.indexOfFirst { it.id == prev } + 1 } ?: 0
        siblings.add(index, row)
        siblings.forEachIndexed { i, task -> task.order = i.toLong() }
    }

    // JSON

    private fun ListRow.toJson() = buildJsonObject {
        put("kind", "tasks#taskList")
        put("id", id)
        put("etag", "\"list-$id-$updated\"")
        put("title", title)
        put("updated", stamp(updated))
        put("selfLink", "https://www.googleapis.com/tasks/v1/users/@me/lists/$id")
    }

    private fun TaskRow.toJson() = buildJsonObject {
        put("kind", "tasks#task")
        put("id", id)
        put("etag", "\"task-$id-$updated\"")
        put("title", title)
        put("updated", stamp(updated))
        put("selfLink", "https://www.googleapis.com/tasks/v1/lists/$listId/tasks/$id")
        parent?.let { put("parent", it) }
        put("position", "%020d".format(order))
        notes?.let { put("notes", it) }
        put("status", status)
        due?.let { put("due", it) }
        completed?.let { put("completed", stamp(it)) }
        if (deleted) put("deleted", true)
        put("links", JsonArray(emptyList()))
        put("webViewLink", "https://tasks.google.com/task/$id")
    }

    private fun ok(body: JsonElement) =
        MockResponse(200, Headers.headersOf("Content-Type", "application/json; charset=UTF-8", "Date", httpDate()), body.toString())

    private fun error(code: Int, message: String) =
        MockResponse(code, Headers.headersOf("Content-Type", "application/json; charset=UTF-8"), errorBody(code, message))

    private fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    /** Google drops the time from `due` and returns midnight UTC. */
    private fun normalizeDue(value: String) = value.substring(0, 10) + "T00:00:00.000Z"

    private fun newId() = "gid${nextId++}"

    private fun stamp(instant: Instant) = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .format(instant.atOffset(ZoneOffset.UTC))

    private fun httpDate() = DateTimeFormatter.RFC_1123_DATE_TIME.format(now.atOffset(ZoneOffset.UTC))

    companion object {
        fun errorBody(code: Int, message: String, reason: String = "notFound") =
            """{"error":{"code":$code,"message":"$message","errors":[{"message":"$message","domain":"global","reason":"$reason"}],"status":"ERROR"}}"""
    }
}
