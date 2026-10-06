package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ListPatch
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteStep
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskChangePage
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.Response

/**
 * [TaskProvider] for Google Tasks (research R6, data-model.md "Provider field mapping").
 *
 * - Subtasks are steps: a Google task with a `parent` is shown as a step of that parent, never as
 *   a task of its own. Step order is the subtasks' `position`.
 * - The change cursor is the server time at the start of the last fetch, sent back as
 *   `updatedMin`. Google returns every page for a list before the cursor moves, so a page is
 *   always the whole set of changes and [TaskChangePage.hasMore] is false.
 * - Google cannot store importance, so it is never sent and always reads as false.
 */
class GoogleTasksProvider internal constructor(
    private val api: GoogleTasksApi,
    private val auth: GoogleAuth,
    private val bearer: BearerToken,
    private val clock: Clock = Clock.systemUTC()
) : TaskProvider {
    override val kind = ProviderKind.GOOGLE
    override val capabilities = ProviderCapabilities(importance = false, manualOrder = true, dueTime = false)

    override suspend fun signIn(host: SignInHost): AccountInfo = auth.signIn(host)

    override suspend fun signOut() = auth.signOut()

    // Lists

    override suspend fun getLists(): List<RemoteList> {
        val all = mutableListOf<TaskListDto>()
        var pageToken: String? = null
        do {
            val page = call("lists") { api.lists(pageToken = pageToken) }
            all += page.items
            pageToken = page.nextPageToken
        } while (pageToken != null)
        // Google always returns the default list ("@default") first.
        return all.mapIndexed { index, dto -> dto.toRemote(isDefault = index == 0) }
    }

    override suspend fun createList(title: String): RemoteList =
        call("lists") { api.insertList(buildJsonObject { put("title", title) }) }.toRemote(isDefault = false)

    override suspend fun updateList(id: String, patch: ListPatch): RemoteList {
        val body = buildJsonObject { patch.title?.let { put("title", it) } }
        val dto = call(id) { api.patchList(id, body) }
        return dto.toRemote(isDefault = isDefaultList(id))
    }

    override suspend fun deleteList(id: String) {
        call(id) { api.deleteList(id) }
    }

    // Tasks

    override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage {
        val since = cursor?.let { parseCursor(listId, it) }
        val (fetched, serverTime) = fetchTasks(listId, updatedMin = since)
        val nextCursor = CURSOR_PREFIX + serverTime.toString()

        if (since == null) {
            val tasks = assemble(listId, fetched.filterNot { it.deleted })
            return TaskChangePage(tasks, deletedIds = emptyList(), nextCursor = nextCursor, hasMore = false)
        }
        if (fetched.isEmpty()) return TaskChangePage(emptyList(), emptyList(), nextCursor, hasMore = false)

        // A change to a subtask is a change to its parent's steps. Building any task needs all of
        // its subtasks, which Google can only list for the whole list, so read the live tasks once.
        val live = fetchTasks(listId, updatedMin = null).first.filterNot { it.deleted }
        val liveById = live.associateBy { it.id }
        val changedTopLevel = mutableSetOf<String>()
        val deleted = mutableSetOf<String>()
        for (dto in fetched) {
            val now = liveById[dto.id]
            when {
                now == null && dto.parent == null -> deleted += dto.id
                now == null -> dto.parent?.let { changedTopLevel += it }
                now.parent == null -> changedTopLevel += now.id
                else -> {
                    // Google cannot say whether this subtask was a task before (indented on the
                    // web), so it is reported as "not a task"; for an id that never was a task
                    // that is a no-op.
                    changedTopLevel += now.parent
                    deleted += now.id
                }
            }
        }
        val all = assemble(listId, live)
        val changed = all.filter { it.id in changedTopLevel }
        val gone = (deleted - all.map { it.id }.toSet()).toList()
        return TaskChangePage(changed, gone, nextCursor, hasMore = false)
    }

    override suspend fun createTask(listId: String, draft: TaskDraft): RemoteTask {
        val body = buildJsonObject {
            put("title", draft.title)
            draft.notes?.let { put("notes", it) }
            draft.dueDate?.let { put("due", formatDue(it)) }
        }
        val parent = call(listId) { api.insertTask(listId, body) }
        var previous: String? = null
        val children = draft.steps.map { step ->
            val child = call(parent.id) {
                api.insertTask(listId, stepBody(step.title, step.done), parent = parent.id, previous = previous)
            }
            previous = child.id
            child
        }
        return parent.toRemote(listId, children)
    }

    override suspend fun updateTask(listId: String, id: String, patch: TaskPatch): RemoteTask {
        val body = buildJsonObject {
            patch.title?.let { put("title", it) }
            patch.notes?.let { notes ->
                put("notes", if (notes is Patch.Set) JsonPrimitive(notes.value) else JsonNull)
            }
            patch.dueDate?.let { due ->
                put("due", if (due is Patch.Set) JsonPrimitive(formatDue(due.value)) else JsonNull)
            }
            patch.completed?.let { done ->
                put("status", if (done) GoogleStatus.COMPLETED else GoogleStatus.NEEDS_ACTION)
                if (!done) put("completed", JsonNull)
            }
        }
        val parent = if (body.isEmpty()) {
            call(id) { api.task(listId, id) }
        } else {
            call(id) { api.patchTask(listId, id, body) }
        }
        if (parent.deleted || parent.parent != null) throw ProviderError.NotFound(id)

        patch.steps?.takeIf { it.isNotEmpty() }?.let { applySteps(listId, id, it) }
        return parent.toRemote(listId, children(listId, id))
    }

    override suspend fun moveTask(listId: String, id: String, afterId: String?) {
        call(id) { api.moveTask(listId, id, previous = afterId) }
    }

    override suspend fun deleteTask(listId: String, id: String) {
        call(id) { api.deleteTask(listId, id) }
    }

    // Helpers

    private suspend fun applySteps(listId: String, parentId: String, patches: List<StepPatch>) {
        var last = if (patches.any { it is StepPatch.Add }) children(listId, parentId).lastOrNull()?.id else null
        for (patch in patches) {
            when (patch) {
                is StepPatch.Add -> {
                    last = call(parentId) {
                        api.insertTask(listId, stepBody(patch.title, patch.done), parent = parentId, previous = last)
                    }.id
                }
                is StepPatch.Update -> {
                    val body = buildJsonObject {
                        patch.title?.let { put("title", it) }
                        patch.done?.let { done ->
                            put("status", if (done) GoogleStatus.COMPLETED else GoogleStatus.NEEDS_ACTION)
                            if (!done) put("completed", JsonNull)
                        }
                    }
                    if (body.isNotEmpty()) call(patch.id) { api.patchTask(listId, patch.id, body) }
                }
                is StepPatch.Move -> {
                    call(patch.id) { api.moveTask(listId, patch.id, parent = parentId, previous = patch.afterId) }
                }
                is StepPatch.Remove -> {
                    // Already gone is as good as removed.
                    try {
                        call(patch.id) { api.deleteTask(listId, patch.id) }
                    } catch (_: ProviderError.NotFound) {
                    }
                }
            }
        }
    }

    private suspend fun children(listId: String, parentId: String): List<TaskDto> =
        fetchTasks(listId, updatedMin = null).first
            .filter { it.parent == parentId && !it.deleted }
            .sortedBy { it.position.orEmpty() }

    /** Every page of tasks in [listId]; with [updatedMin], only those changed since, deletions included. */
    private suspend fun fetchTasks(listId: String, updatedMin: Instant?): Pair<List<TaskDto>, Instant> {
        val all = mutableListOf<TaskDto>()
        var serverTime: Instant? = null
        var pageToken: String? = null
        do {
            val response = callRaw(listId) {
                api.tasks(
                    listId,
                    updatedMin = updatedMin?.let(::formatTimestamp),
                    showDeleted = updatedMin != null,
                    pageToken = pageToken
                )
            }
            // The server's clock, not the phone's, decides what "changed since" means next time.
            serverTime = serverTime ?: response.headers().getDate("Date")?.toInstant() ?: clock.instant()
            val page = response.body() ?: TasksDto()
            all += page.items
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return all to checkNotNull(serverTime)
    }

    /** Groups subtasks under their parents; the result holds only top-level tasks. */
    private fun assemble(listId: String, tasks: List<TaskDto>): List<RemoteTask> {
        val childrenByParent = tasks.filter { it.parent != null }.groupBy { it.parent }
        return tasks
            .filter { it.parent == null }
            .map { parent ->
                parent.toRemote(listId, childrenByParent[parent.id].orEmpty().sortedBy { it.position.orEmpty() })
            }
    }

    private suspend fun isDefaultList(id: String): Boolean =
        runCatching { call("lists") { api.lists(maxResults = 1) }.items.firstOrNull()?.id == id }.getOrDefault(false)

    private fun stepBody(title: String, done: Boolean): JsonObject = buildJsonObject {
        put("title", title)
        if (done) put("status", GoogleStatus.COMPLETED)
    }

    private fun parseCursor(listId: String, cursor: String): Instant {
        if (!cursor.startsWith(CURSOR_PREFIX)) throw ProviderError.CursorExpired(listId)
        return try {
            Instant.parse(cursor.removePrefix(CURSOR_PREFIX))
        } catch (e: DateTimeParseException) {
            throw ProviderError.CursorExpired(listId, e)
        }
    }

    private suspend fun <T : Any> call(id: String, request: suspend () -> Response<T>): T {
        val response = callRaw(id, request)
        @Suppress("UNCHECKED_CAST")
        return response.body() ?: (Unit as T)
    }

    /** Runs [request] with a token, retrying once with a fresh token after a 401. */
    private suspend fun <T> callRaw(id: String, request: suspend () -> Response<T>): Response<T> {
        try {
            bearer.value = auth.accessToken()
            val response = request()
            if (response.isSuccessful) return response
            if (response.code() != 401) GoogleErrors.fail(response, id)
            bearer.value = auth.accessToken(forceRefresh = true)
            val retried = request()
            if (retried.isSuccessful) return retried
            GoogleErrors.fail(retried, id)
        } catch (e: Throwable) {
            throw GoogleErrors.wrap(e)
        }
    }

    private fun TaskListDto.toRemote(isDefault: Boolean) = RemoteList(
        id = id,
        title = title,
        isDefault = isDefault,
        etag = etag,
        updatedAt = parseTimestamp(updated)
    )

    private fun TaskDto.toRemote(listId: String, children: List<TaskDto>) = RemoteTask(
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
        updatedAt = parseTimestamp(updated)
    )

    companion object {
        const val BASE_URL = "https://tasks.googleapis.com/tasks/v1/"
        private const val CURSOR_PREFIX = "u:"

        /**
         * Builds the provider for the app. [webClientId] is the OAuth web client ID from
         * local.properties or CI secrets, never from git.
         */
        fun create(context: android.content.Context, webClientId: String): GoogleTasksProvider {
            val auth = AndroidGoogleAuth(context.applicationContext, webClientId)
            val bearer = BearerToken()
            return GoogleTasksProvider(GoogleTasksClient.api(BASE_URL, bearer), auth, bearer)
        }

        /** Google stores only the date; it sends and expects midnight UTC. */
        internal fun formatDue(date: LocalDate): String = "${date}T00:00:00.000Z"

        internal fun parseDue(value: String?): LocalDate? =
            value?.takeIf { it.length >= 10 }?.let { LocalDate.parse(it.substring(0, 10)) }

        internal fun formatTimestamp(instant: Instant): String = instant.atOffset(ZoneOffset.UTC).toString()

        internal fun parseTimestamp(value: String?): Instant =
            value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: Instant.EPOCH
    }
}
