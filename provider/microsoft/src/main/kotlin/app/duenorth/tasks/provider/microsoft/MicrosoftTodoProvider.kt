package app.duenorth.tasks.provider.microsoft

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ListPatch
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskChangePage
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.microsoft.auth.MicrosoftAccountAuth
import app.duenorth.tasks.provider.microsoft.graph.BatchRequestDto
import app.duenorth.tasks.provider.microsoft.graph.BatchRequestItem
import app.duenorth.tasks.provider.microsoft.graph.ChecklistItemDto
import app.duenorth.tasks.provider.microsoft.graph.GRAPH_BASE_URL
import app.duenorth.tasks.provider.microsoft.graph.GraphTodoApi
import app.duenorth.tasks.provider.microsoft.graph.MAX_BATCH
import app.duenorth.tasks.provider.microsoft.graph.TodoTaskDto
import app.duenorth.tasks.provider.microsoft.graph.allows
import app.duenorth.tasks.provider.microsoft.graph.errorCode
import app.duenorth.tasks.provider.microsoft.graph.graphApi
import app.duenorth.tasks.provider.microsoft.graph.graphCall
import app.duenorth.tasks.provider.microsoft.graph.httpError
import java.time.Clock
import java.time.ZoneId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * [TaskProvider] for Microsoft To Do over Microsoft Graph v1.0 (user story 3, research R7).
 *
 * - Lists are `todoTaskList`s; the built-in "Tasks" list is the default list.
 * - Change cursors are Graph delta links (`/tasks/delta`), stored as-is by the sync engine.
 * - Steps are `checklistItem`s; step edits go out together in one `$batch` call.
 * - To Do has importance but no manual order, so [moveTask] does nothing.
 *
 * Every failure surfaces as a [ProviderError]; no Graph or MSAL type leaves this module.
 */
class MicrosoftTodoProvider internal constructor(
    private val api: GraphTodoApi,
    private val auth: MicrosoftAccountAuth,
    private val baseUrl: HttpUrl,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: Clock = Clock.systemUTC()
) : TaskProvider {
    override val kind = ProviderKind.MICROSOFT
    override val capabilities = ProviderCapabilities(importance = true, manualOrder = false, dueTime = false)

    override suspend fun signIn(host: SignInHost): AccountInfo = auth.signIn(host)

    override suspend fun signOut() = auth.signOut()

    override suspend fun getLists(): List<RemoteList> = graphCall("lists") {
        var page = api.lists()
        val lists = page.value.toMutableList()
        while (page.nextLink != null) {
            page = api.listsPage(checkedLink(page.nextLink!!, listId = null))
            lists += page.value
        }
        lists.map { it.toRemote(clock.instant()) }
    }

    override suspend fun createList(title: String): RemoteList = graphCall("lists") {
        api.createList(buildJsonObject { put("displayName", title) }).toRemote(clock.instant())
    }

    override suspend fun updateList(id: String, patch: ListPatch): RemoteList = graphCall(id) {
        val body = buildJsonObject { patch.title?.let { put("displayName", it) } }
        api.updateList(id, body).toRemote(clock.instant())
    }

    override suspend fun deleteList(id: String) = graphCall(id) { api.deleteList(id) }

    override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage {
        val page = graphCall(listId, listId = listId) {
            if (cursor == null) api.taskDelta(listId) else api.taskDeltaPage(checkedLink(cursor, listId))
        }
        val (removed, live) = page.value.partition { it.removed != null }
        return TaskChangePage(
            changed = live.map { it.toRemote(listId, stepsOf(listId, it)) },
            deletedIds = removed.map { it.id },
            nextCursor = page.nextLink ?: page.deltaLink
                ?: throw ProviderError.Transient("Delta response for list $listId had no next or delta link"),
            hasMore = page.nextLink != null
        )
    }

    override suspend fun createTask(listId: String, draft: TaskDraft): RemoteTask {
        val created = graphCall(listId) { api.createTask(listId, draft.toCreateBody(zone())) }
        if (draft.steps.isEmpty()) return created.toRemote(listId, steps = emptyList())
        applySteps(listId, created.id, draft.steps.map { StepPatch.Add(it.title, it.done) })
        return fetchTask(listId, created.id)
    }

    override suspend fun updateTask(listId: String, id: String, patch: TaskPatch): RemoteTask {
        val body = patch.toPatchBody(zone())
        val patched = if (body.isNotEmpty()) graphCall(id) { api.updateTask(listId, id, body) } else null
        val steps = patch.steps.orEmpty()
        if (steps.isNotEmpty()) applySteps(listId, id, steps)
        // A PATCH response does not include checklist items, so read the task back whole.
        return if (patched != null && steps.isEmpty()) {
            patched.toRemote(listId, stepsOf(listId, patched))
        } else {
            fetchTask(listId, id)
        }
    }

    /** To Do has no manual order (capabilities.manualOrder is false). */
    override suspend fun moveTask(listId: String, id: String, afterId: String?) = Unit

    override suspend fun deleteTask(listId: String, id: String) = graphCall(id) { api.deleteTask(listId, id) }

    private suspend fun fetchTask(listId: String, id: String): RemoteTask {
        val task = graphCall(id) { api.task(listId, id) }
        return task.toRemote(listId, stepsOf(listId, task))
    }

    /** Checklist items of [task], fetched separately when the response did not expand them. */
    private suspend fun stepsOf(listId: String, task: TodoTaskDto): List<ChecklistItemDto> =
        task.checklistItems ?: graphCall(task.id) { api.checklistItems(listId, task.id).value }

    /** Sends step changes as `$batch` calls of up to [MAX_BATCH] requests each, in order. */
    private suspend fun applySteps(listId: String, taskId: String, steps: List<StepPatch>) {
        val base = "/me/todo/lists/${listId.urlSegment()}/tasks/${taskId.urlSegment()}/checklistItems"
        steps.chunked(MAX_BATCH).forEach { chunk ->
            // Adds are chained with dependsOn so new steps keep their order; edits and removals run freely.
            var previousAdd: String? = null
            val requests = chunk.mapIndexed { i, step ->
                val id = "${i + 1}"
                val item = step.toBatchItem(id, base)
                if (step !is StepPatch.Add) return@mapIndexed item
                item.copy(dependsOn = previousAdd?.let(::listOf)).also { previousAdd = id }
            }
            val response = graphCall(taskId) { api.batch(BatchRequestDto(requests)) }
            for (item in response.responses.sortedBy { it.id.toIntOrNull() ?: 0 }) {
                if (item.status in 200..299) continue
                val step = chunk.getOrNull((item.id.toIntOrNull() ?: 0) - 1)
                // Removing a step that is already gone is what we wanted anyway.
                if (item.status == 404 && step is StepPatch.Remove) continue
                val stepId = (step as? StepPatch.Update)?.id ?: (step as? StepPatch.Remove)?.id ?: taskId
                throw httpError(
                    status = item.status,
                    retryAfter = item.headers?.entries?.firstOrNull { it.key.equals("Retry-After", true) }?.value,
                    code = item.body?.let { errorCode(it.toString()) },
                    id = stepId,
                    listId = null
                )
            }
        }
    }

    private fun StepPatch.toBatchItem(id: String, base: String): BatchRequestItem = when (this) {
        is StepPatch.Add -> BatchRequestItem(
            id = id,
            method = "POST",
            url = base,
            body = buildJsonObject {
                put("displayName", title)
                put("isChecked", done)
            },
            headers = JSON_HEADERS
        )
        is StepPatch.Update -> BatchRequestItem(
            id = id,
            method = "PATCH",
            url = "$base/${this.id.urlSegment()}",
            body = buildJsonObject {
                title?.let { put("displayName", it) }
                done?.let { put("isChecked", it) }
            },
            headers = JSON_HEADERS
        )
        is StepPatch.Remove -> BatchRequestItem(id = id, method = "DELETE", url = "$base/${this.id.urlSegment()}")
    }

    /** A stored link is followed only if it points at Graph; anything else means "fetch again". */
    private fun checkedLink(link: String, listId: String?): String {
        if (baseUrl.allows(link)) return link
        throw if (listId != null) {
            ProviderError.CursorExpired(listId)
        } else {
            ProviderError.Transient("Graph returned a link outside $baseUrl")
        }
    }

    private fun TodoTaskDto.toRemote(listId: String, steps: List<ChecklistItemDto>?) =
        toRemote(listId, zone(), clock.instant(), steps)

    private fun String.urlSegment(): String =
        baseUrl.newBuilder().addPathSegment(this).build().encodedPathSegments.last()

    companion object {
        private val JSON_HEADERS = mapOf("Content-Type" to "application/json")

        /** The provider the app binds for [app.duenorth.tasks.provider.api.ProviderKind.MICROSOFT]. */
        fun create(auth: MicrosoftAccountAuth): MicrosoftTodoProvider = create(auth, GRAPH_BASE_URL.toHttpUrl())

        internal fun create(
            auth: MicrosoftAccountAuth,
            baseUrl: HttpUrl,
            client: OkHttpClient = OkHttpClient(),
            zone: () -> ZoneId = ZoneId::systemDefault,
            clock: Clock = Clock.systemUTC()
        ) = MicrosoftTodoProvider(graphApi(baseUrl, auth, client), auth, baseUrl, zone, clock)
    }
}
