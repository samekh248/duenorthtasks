package app.duenorth.tasks.provider.microsoft

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl

/**
 * An in-memory Microsoft Graph To Do endpoint for tests, answering in Graph's own JSON shapes
 * (the same shapes as the recorded responses in src/test/resources/graph).
 *
 * It models what the provider relies on: server-assigned ids, field-level PATCH that leaves
 * fields it was not sent alone (including ones Due North never reads, like `categories`), delta
 * links with paging and `@removed` entries, checklist items, `$batch`, and bearer-token checks.
 */
class FakeGraphServer(
    /** Tasks per delta page, to exercise `@odata.nextLink`. */
    private val pageSize: Int = 2,
    /** Whether delta honors `$expand=checklistItems`; when false the provider must fetch them. */
    private val expandOnDelta: Boolean = true,
    withDefaultList: Boolean = false,
    /** Delta keeps answering with the same next link and never finishes, as Graph sometimes does. */
    private val loopDelta: Boolean = false,
    /** Whether the list endpoint (`/tasks`) honors `$expand=checklistItems`. */
    private val expandOnList: Boolean = true,
    /** Whether the list endpoint accepts `$filter`; when false it answers 400 as some services do. */
    private val filterOnList: Boolean = true
) : Dispatcher() {
    val server = MockWebServer()
    val baseUrl: HttpUrl get() = server.url("/v1.0/")

    /** Every request as "METHOD /path", in order, including requests inside a `$batch`. */
    val calls = mutableListOf<String>()

    /** The Prefer header of every top-level request, in order. */
    val prefers = mutableListOf<String?>()

    private val lock = Any()
    private val ids = AtomicLong(1)
    private var version = 0L
    private val lists = linkedMapOf<String, ListRec>()
    private val tasks = linkedMapOf<String, TaskRec>()
    private val tombstones = mutableListOf<Tombstone>()
    private val failures = ArrayDeque<MockResponse>()

    /** Requests inside `$batch` calls still to be turned away with 429, as a busy mailbox does. */
    var throttleBatchItems = 0

    private class ListRec(
        val id: String,
        var name: String,
        val wellknown: String,
        var isShared: Boolean = false,
        var isOwner: Boolean = true
    )

    /** Shares a list as To Do itself would; Graph only ever reports these two flags (spec 002). */
    fun shareList(id: String, isShared: Boolean, isOwner: Boolean) = synchronized(lock) {
        val list = checkNotNull(lists[id])
        list.isShared = isShared
        list.isOwner = isOwner
    }

    private class TaskRec(
        val id: String,
        val listId: String,
        var fields: JsonObject,
        val items: MutableList<JsonObject>,
        var version: Long
    )

    private class Tombstone(val listId: String, val id: String, val version: Long)

    init {
        server.dispatcher = this
        server.start()
        if (withDefaultList) lists["list-default"] = ListRec("list-default", "Tasks", "defaultList")
    }

    /** The next request fails with [status] and an optional Graph error [code]. */
    fun failNext(status: Int, code: String = "error", headers: Map<String, String> = emptyMap()) {
        failures += MockResponse(
            status,
            headersOf(
                *(headers + ("Content-Type" to "application/json")).flatMap {
                    listOf(it.key, it.value)
                }.toTypedArray()
            ),
            """{"error":{"code":"$code","message":"Injected failure"}}"""
        )
    }

    /** Applies a Graph PATCH body to a task, as an edit in the To Do web app would. */
    fun editRemotely(taskId: String, patch: JsonObject) = synchronized(lock) {
        val task = tasks.getValue(taskId)
        task.fields = applyPatch(task.fields, patch)
        task.version = ++version
    }

    fun rawTask(taskId: String): JsonObject = synchronized(lock) { tasks.getValue(taskId).fields }

    fun shutdown() = server.close()

    override fun dispatch(request: RecordedRequest): MockResponse {
        synchronized(lock) { prefers += request.headers["Prefer"] }
        failures.removeFirstOrNull()?.let { return it }
        if (request.headers["Authorization"] != "Bearer $TOKEN") {
            return error(401, "InvalidAuthenticationToken")
        }
        val path = request.url.encodedPath.removePrefix("/v1.0")
        val body = request.body?.utf8()?.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it).jsonObject }
        val query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it) }
        return synchronized(lock) { route(request.method ?: "GET", path, query, body) }.toMockResponse()
    }

    private data class Reply(val status: Int, val body: JsonElement? = null)

    private fun Reply.toMockResponse() = MockResponse(
        status,
        headersOf("Content-Type", "application/json"),
        body?.toString() ?: ""
    )

    private fun error(status: Int, code: String) = Reply(status, errorJson(code)).toMockResponse()

    private fun errorJson(code: String) = buildJsonObject {
        putJsonObject("error") {
            put("code", code)
            put("message", code)
        }
    }

    private fun route(method: String, path: String, query: Map<String, String?>, body: JsonObject?): Reply {
        calls += "$method $path"
        val seg = path.trim('/').split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
        if (seg == listOf("\$batch") && method == "POST") return batch(body!!)
        if (seg.take(3) != listOf("me", "todo", "lists")) return Reply(400, errorJson("BadRequest"))
        val listId = seg.getOrNull(3)
        val taskId = seg.getOrNull(5)
        val itemId = seg.getOrNull(7)
        return when {
            seg.size == 3 && method == "GET" -> Reply(200, page(lists.values.map { it.json() }))
            seg.size == 3 && method == "POST" -> {
                val list = ListRec("list-${ids.getAndIncrement()}", body!!.str("displayName")!!, "none")
                lists[list.id] = list
                Reply(201, list.json())
            }
            lists[listId] == null -> Reply(404, errorJson("ErrorItemNotFound"))
            seg.size == 4 && method == "PATCH" -> {
                val list = lists.getValue(listId!!)
                body!!.str("displayName")?.let { list.name = it }
                Reply(200, list.json())
            }
            seg.size == 4 && method == "DELETE" -> {
                lists.remove(listId)
                tasks.values.removeAll { it.listId == listId }
                Reply(204)
            }
            seg.size == 6 && seg[5] == "delta" && method == "GET" -> delta(listId!!, query)
            seg.size == 5 && method == "POST" -> createTask(listId!!, body!!)
            seg.size == 5 && method == "GET" -> listTasks(listId!!, query)
            tasks[taskId]?.takeIf { it.listId == listId } == null -> Reply(404, errorJson("ErrorItemNotFound"))
            seg.size == 6 && method == "GET" -> Reply(
                200,
                tasks.getValue(taskId!!).json(query["\$expand"] == "checklistItems")
            )
            seg.size == 6 && method == "PATCH" -> {
                val task = tasks.getValue(taskId!!)
                task.fields = applyPatch(task.fields, body!!)
                task.version = ++version
                Reply(200, task.json(expand = false))
            }
            seg.size == 6 && method == "DELETE" -> {
                tasks.remove(taskId)
                tombstones += Tombstone(listId!!, taskId!!, ++version)
                Reply(204)
            }
            seg.size == 7 -> checklist(tasks.getValue(taskId!!), method, body)
            seg.size == 8 -> checklistItem(tasks.getValue(taskId!!), itemId!!, method, body)
            else -> Reply(405, errorJson("MethodNotAllowed"))
        }
    }

    private fun createTask(listId: String, body: JsonObject): Reply {
        val now = Instant.ofEpochSecond(1_790_000_000L + version).toString()
        val defaults = buildJsonObject {
            put("importance", "normal")
            put("isReminderOn", false)
            put("status", "notStarted")
            put("title", "")
            put("createdDateTime", now)
            putJsonArray("categories") { }
            putJsonObject("body") {
                put("content", "")
                put("contentType", "text")
            }
        }
        val task =
            TaskRec("task-${ids.getAndIncrement()}", listId, applyPatch(defaults, body), mutableListOf(), ++version)
        tasks[task.id] = task
        return Reply(201, task.json(expand = false))
    }

    private fun checklist(task: TaskRec, method: String, body: JsonObject?): Reply = when (method) {
        "GET" -> Reply(200, page(task.items))
        "POST" -> {
            val item = buildJsonObject {
                put("id", "item-${ids.getAndIncrement()}")
                put("displayName", body!!.str("displayName").orEmpty())
                put("isChecked", body["isChecked"]?.jsonPrimitive?.booleanOrNull ?: false)
                put("createdDateTime", "2026-10-04T12:00:00Z")
            }
            task.items += item
            task.version = ++version
            Reply(201, item)
        }
        else -> Reply(405, errorJson("MethodNotAllowed"))
    }

    private fun checklistItem(task: TaskRec, itemId: String, method: String, body: JsonObject?): Reply {
        val index = task.items.indexOfFirst { it.str("id") == itemId }
        if (index < 0) return Reply(404, errorJson("ErrorItemNotFound"))
        task.version = ++version
        return when (method) {
            "PATCH" -> {
                task.items[index] = applyPatch(task.items[index], body!!)
                Reply(200, task.items[index])
            }
            "DELETE" -> {
                task.items.removeAt(index)
                Reply(204)
            }
            else -> Reply(405, errorJson("MethodNotAllowed"))
        }
    }

    /** Delta tokens are the server version the client has seen; skip tokens add a page offset. */
    private fun delta(listId: String, query: Map<String, String?>): Reply {
        if (loopDelta) {
            val base = baseUrl.newBuilder().addPathSegments(
                "me/todo/lists"
            ).addPathSegment(listId).addPathSegments("tasks/delta")
            return Reply(
                200,
                buildJsonObject {
                    put("@odata.nextLink", base.addQueryParameter("\$skiptoken", "same").build().toString())
                    put(
                        "value",
                        JsonArray(
                            tasks.values.filter {
                                it.listId == listId
                            }.take(pageSize).map { it.json(false) }
                        )
                    )
                }
            )
        }
        val (since, offset) = when {
            query["\$skiptoken"] != null -> query.getValue("\$skiptoken")!!.split(':').let {
                it[0].toLong() to
                    it[1].toInt()
            }
            query["\$deltatoken"] != null -> query.getValue("\$deltatoken")!!.toLong() to 0
            else -> -1L to 0
        }
        if (since > version) return Reply(410, errorJson("syncStateNotFound"))
        val expand =
            expandOnDelta &&
                (query["\$expand"] == "checklistItems" || query["\$skiptoken"] != null || query["\$deltatoken"] != null)
        val changed = tasks.values.filter { it.listId == listId && it.version > since }.map {
            it.version to
                it.json(expand)
        }
        val removed = if (since < 0) {
            emptyList()
        } else {
            tombstones.filter { it.listId == listId && it.version > since }.map {
                it.version to buildJsonObject {
                    put("id", it.id)
                    putJsonObject("@removed") { put("reason", "deleted") }
                }
            }
        }
        val all = (changed + removed).sortedBy { it.first }.map { it.second }
        val pageItems = all.drop(offset).take(pageSize)
        val base = baseUrl.newBuilder().addPathSegments(
            "me/todo/lists"
        ).addPathSegment(listId).addPathSegments("tasks/delta")
        return Reply(
            200,
            buildJsonObject {
                put("@odata.context", "https://graph.microsoft.com/v1.0/\$metadata#Collection(todoTask)")
                if (offset + pageSize < all.size) {
                    put(
                        "@odata.nextLink",
                        base.addQueryParameter("\$skiptoken", "$since:${offset + pageSize}").build().toString()
                    )
                } else {
                    put("@odata.deltaLink", base.addQueryParameter("\$deltatoken", "$version").build().toString())
                }
                put("value", JsonArray(pageItems))
            }
        )
    }

    /** Supports the two filters the provider sends; `$skip` paging stands in for Graph's skip tokens. */
    private fun listTasks(listId: String, query: Map<String, String?>): Reply {
        val filter = query["\$filter"]
        if (filter != null && !filterOnList) return Reply(400, errorJson("BadRequest"))
        val all = tasks.values.filter { it.listId == listId }.filter {
            val done = it.fields.str("status") == "completed"
            when (filter) {
                null -> true
                "status ne 'completed'" -> !done
                "status eq 'completed'" -> done
                else -> return Reply(400, errorJson("BadRequest"))
            }
        }
        val top = query["\$top"]?.toInt() ?: all.size
        val skip = query["\$skip"]?.toInt() ?: 0
        val expand = expandOnList && query["\$expand"] == "checklistItems"
        return Reply(
            200,
            buildJsonObject {
                if (skip + top < all.size) {
                    val next = baseUrl.newBuilder().addPathSegments("me/todo/lists").addPathSegment(listId)
                        .addPathSegment("tasks")
                    query.forEach { (k, v) -> if (k != "\$skip") next.addQueryParameter(k, v) }
                    put("@odata.nextLink", next.addQueryParameter("\$skip", "${skip + top}").build().toString())
                }
                put("value", JsonArray(all.drop(skip).take(top).map { it.json(expand) }))
            }
        )
    }

    private fun batch(body: JsonObject): Reply {
        val responses = body.getValue("requests").jsonArray.map { element ->
            val req = element.jsonObject
            val url = req.str("url")!!
            if (throttleBatchItems > 0) {
                throttleBatchItems--
                return@map buildJsonObject {
                    put("id", req.str("id"))
                    put("status", 429)
                    putJsonObject("headers") { put("Retry-After", "0") }
                    put("body", errorJson("ApplicationThrottled"))
                }
            }
            val reply = route(req.str("method")!!, url.substringBefore('?'), emptyMap(), req["body"] as? JsonObject)
            buildJsonObject {
                put("id", req.str("id"))
                put("status", reply.status)
                putJsonObject("headers") { put("Content-Type", "application/json") }
                reply.body?.let { put("body", it) }
            }
        }
        return Reply(200, buildJsonObject { put("responses", JsonArray(responses)) })
    }

    private fun page(items: List<JsonObject>) = buildJsonObject { put("value", JsonArray(items)) }

    private fun ListRec.json() = buildJsonObject {
        put("@odata.etag", "W/\"$id-$name\"")
        put("displayName", name)
        put("isOwner", isOwner)
        put("isShared", isShared)
        put("wellknownListName", wellknown)
        put("id", id)
    }

    private fun TaskRec.json(expand: Boolean) = buildJsonObject {
        put("@odata.etag", "W/\"v$version\"")
        fields.forEach { (k, v) -> put(k, v) }
        put("lastModifiedDateTime", Instant.ofEpochSecond(1_790_000_000L + version).toString())
        put("id", id)
        if (expand) put("checklistItems", JsonArray(items))
    }

    private fun applyPatch(current: JsonObject, patch: JsonObject): JsonObject {
        val merged = current.toMutableMap()
        patch.forEach { (k, v) ->
            if (v is JsonNull) merged.remove(k) else merged[k] = v
        }
        val status = patch.str("status")
        if (status == "completed" && current.str("status") != "completed") {
            merged["completedDateTime"] = buildJsonObject {
                put("dateTime", "2026-10-04T12:00:00.0000000")
                put("timeZone", "UTC")
            }
        } else if (status != null && status != "completed") {
            merged.remove("completedDateTime")
        }
        return JsonObject(merged)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val TOKEN = "token-1"
    }
}
