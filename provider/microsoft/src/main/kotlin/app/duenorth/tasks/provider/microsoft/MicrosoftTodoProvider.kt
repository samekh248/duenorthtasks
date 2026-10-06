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
import app.duenorth.tasks.provider.microsoft.graph.BatchResponseItem
import app.duenorth.tasks.provider.microsoft.graph.ChecklistItemDto
import app.duenorth.tasks.provider.microsoft.graph.GRAPH_BASE_URL
import app.duenorth.tasks.provider.microsoft.graph.GraphPage
import app.duenorth.tasks.provider.microsoft.graph.GraphTodoApi
import app.duenorth.tasks.provider.microsoft.graph.MAX_BATCH
import app.duenorth.tasks.provider.microsoft.graph.TodoTaskDto
import app.duenorth.tasks.provider.microsoft.graph.allows
import app.duenorth.tasks.provider.microsoft.graph.errorCode
import app.duenorth.tasks.provider.microsoft.graph.graphApi
import app.duenorth.tasks.provider.microsoft.graph.graphCall
import app.duenorth.tasks.provider.microsoft.graph.graphJson
import app.duenorth.tasks.provider.microsoft.graph.httpError
import app.duenorth.tasks.provider.microsoft.graph.parseRetryAfter
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        val round = rounds.getOrPut(listId) { DeltaRound() }
        synchronized(round) {
            // A new round starts from scratch or from a stored delta link; anything else continues one.
            if (cursor == null || cursor != round.handedOut) round.reset()
            if (cursor != null && !round.followed.add(cursor) || round.followed.size > MAX_PAGES) {
                round.reset()
                throw ProviderError.Transient("Microsoft To Do kept paging list $listId; trying again later")
            }
        }
        val page = graphCall(listId, listId = listId) {
            if (cursor == null) api.taskDelta(listId) else api.taskDeltaPage(checkedLink(cursor, listId))
        }
        synchronized(round) {
            val signature = page.value.map { it.id to it.etag }
            // Same items again behind a fresh link: Graph is going round in circles.
            if (page.nextLink != null && signature.isNotEmpty() && signature == round.lastPage) {
                round.reset()
                throw ProviderError.Transient("Microsoft To Do repeated a page of list $listId; trying again later")
            }
            round.lastPage = signature
            round.handedOut = page.nextLink
        }
        val (removed, live) = page.value.partition { it.removed != null }
        val steps = stepsFor(listId, live)
        // Steps read for this round are only good until it ends; later rounds bring few changes.
        if (page.nextLink == null) stepCaches.remove(listId)
        return TaskChangePage(
            changed = live.map { it.toRemote(listId, it.checklistItems ?: steps[it.id].orEmpty()) },
            deletedIds = removed.map { it.id },
            nextCursor = page.nextLink ?: page.deltaLink
                ?: throw ProviderError.Transient("Delta response for list $listId had no next or delta link"),
            hasMore = page.nextLink != null
        )
    }

    /** Links followed in the current delta round of one list, so a page that repeats cannot loop sync forever. */
    private class DeltaRound {
        val followed = mutableSetOf<String>()
        var handedOut: String? = null
        var lastPage: List<Pair<String, String?>>? = null

        fun reset() {
            followed.clear()
            handedOut = null
            lastPage = null
        }
    }

    private val rounds = ConcurrentHashMap<String, DeltaRound>()

    override suspend fun getOpenTasks(listId: String): List<RemoteTask>? {
        // If Graph will not filter this list, the full fetch alone still gets everything.
        val open = try {
            readTasks(listId, filter = OPEN)
        } catch (_: ProviderError.Transient) {
            return null
        }
        stepCaches.getValue(listId).openRead = true
        val steps = missingSteps(listId, open)
        return open.map { it.toRemote(listId, it.checklistItems ?: steps[it.id].orEmpty()) }
    }

    /**
     * Steps of one list's tasks as last read through the list endpoint, keyed by task id with the
     * etag they were read at, so a delta round can reuse them instead of asking task by task.
     */
    private class StepCache {
        val steps = ConcurrentHashMap<String, Pair<String?, List<ChecklistItemDto>>>()

        @Volatile var listRead = false

        /** Open tasks were read already ([getOpenTasks]), so a full read needs only completed ones. */
        @Volatile var openRead = false

        fun remember(tasks: List<TodoTaskDto>) = tasks.forEach { task ->
            task.checklistItems?.let { steps[task.id] = task.etag to it }
        }

        fun lookup(task: TodoTaskDto): List<ChecklistItemDto>? =
            steps[task.id]?.takeIf { it.first == task.etag }?.second
    }

    private val stepCaches = ConcurrentHashMap<String, StepCache>()

    /** Every task of [listId] matching [filter], following pages; their steps are kept for the delta round. */
    private suspend fun readTasks(listId: String, filter: String?): List<TodoTaskDto> = graphCall(listId) {
        var page = api.tasks(listId, filter)
        val tasks = page.value.toMutableList()
        while (page.nextLink != null) {
            page = api.tasksPage(checkedLink(page.nextLink!!, listId = null))
            tasks += page.value
        }
        stepCaches.getOrPut(listId) { StepCache() }.remember(tasks)
        tasks
    }

    /**
     * Steps for delta [tasks] that arrived without them (Graph does not expand them on delta).
     * Ones already read this round are reused; when many are missing, as on a first sync, the
     * whole list is read once with its steps (100 tasks a request); the few left go by `$batch`.
     */
    private suspend fun stepsFor(listId: String, tasks: List<TodoTaskDto>): Map<String, List<ChecklistItemDto>> {
        val bare = tasks.filter { it.checklistItems == null }
        if (bare.isEmpty()) return emptyMap()
        val cache = stepCaches.getOrPut(listId) { StepCache() }
        var missing = bare.filter { cache.lookup(it) == null }
        if (missing.size > MAX_BATCH && !cache.listRead) {
            cache.listRead = true
            // Best effort: whatever this read cannot answer is fetched by $batch below.
            try {
                readTasks(listId, filter = if (cache.openRead) COMPLETED else null)
            } catch (_: ProviderError.Transient) {
            }
            missing = bare.filter { cache.lookup(it) == null }
        }
        val fetched = missingSteps(listId, missing)
        return bare.associate { it.id to (cache.lookup(it) ?: fetched[it.id].orEmpty()) }
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

    /**
     * Checklist items for the [tasks] that arrived without them, [MAX_BATCH] tasks per `$batch`
     * call. One request per task made a first sync crawl.
     */
    private suspend fun missingSteps(listId: String, tasks: List<TodoTaskDto>): Map<String, List<ChecklistItemDto>> {
        val ids = tasks.filter { it.checklistItems == null }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val gate = Semaphore(PARALLEL_BATCHES)
        return coroutineScope {
            ids.chunked(MAX_BATCH).map { chunk ->
                async { gate.withPermit { checklistBatch(listId, chunk) } }
            }.awaitAll().fold(emptyMap()) { all, part -> all + part }
        }
    }

    private suspend fun checklistBatch(listId: String, taskIds: List<String>): Map<String, List<ChecklistItemDto>> {
        val result = mutableMapOf<String, List<ChecklistItemDto>>()
        var pending = taskIds
        var attempt = 0
        while (true) {
            val requests = pending.mapIndexed { i, taskId ->
                BatchRequestItem(
                    id = "${i + 1}",
                    method = "GET",
                    url = "/me/todo/lists/${listId.urlSegment()}/tasks/${taskId.urlSegment()}/checklistItems"
                )
            }
            val response = graphCall(listId) { api.batch(BatchRequestDto(requests)) }
            val byId = response.responses.associateBy { it.id }
            val throttled = mutableListOf<Pair<String, BatchResponseItem>>()
            pending.forEachIndexed { i, taskId ->
                val item = byId["${i + 1}"]
                val page = item?.takeIf { it.status in 200..299 }?.body?.let {
                    runCatching {
                        graphJson.decodeFromJsonElement(GraphPage.serializer(ChecklistItemDto.serializer()), it)
                    }.getOrNull()
                }
                when {
                    // A task deleted since the delta page was read has no steps to show.
                    item?.status == 404 -> result[taskId] = emptyList()
                    // To Do runs only a few requests per mailbox at once and turns the rest of a batch
                    // away; those are asked again after a pause rather than failing the whole sync.
                    item != null && (item.status == 429 || item.status == 503) -> throttled += taskId to item
                    item != null && item.status !in 200..299 -> throw item.toError(taskId)
                    // Missing or paged answers: ask for that one task directly.
                    page == null || page.nextLink != null ->
                        result[taskId] = graphCall(taskId) { api.checklistItems(listId, taskId).value }
                    else -> result[taskId] = page.value
                }
            }
            if (throttled.isEmpty()) return result
            // Past a few rounds, stop so the sync backs off as a whole.
            if (++attempt > BATCH_RETRIES) throttled.first().let { (taskId, item) -> throw item.toError(taskId) }
            val wait = throttled.maxOf { (_, item) -> parseRetryAfter(item.retryAfter()) }
            delay(wait.coerceIn(MIN_BATCH_PAUSE, MAX_BATCH_PAUSE))
            pending = throttled.map { it.first }
        }
    }

    private fun BatchResponseItem.retryAfter(): String? =
        headers?.entries?.firstOrNull { it.key.equals("Retry-After", true) }?.value

    private fun BatchResponseItem.toError(id: String): ProviderError = httpError(
        status = status,
        retryAfter = retryAfter(),
        code = body?.let {
            errorCode(it.toString())
        },
        id = id,
        listId = null
    )

    /** Sends step changes as `$batch` calls of up to [MAX_BATCH] requests each, in order. */
    private suspend fun applySteps(listId: String, taskId: String, patches: List<StepPatch>) {
        // To Do keeps no order for checklist items that other apps can set (spec 003, research R5).
        val steps = patches.filterNot { it is StepPatch.Move }
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
        is StepPatch.Move -> error("Moves are filtered out before batching")
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
        private const val OPEN = "status ne 'completed'"
        private const val COMPLETED = "status eq 'completed'"

        /** More pages than any real list needs; past this a delta round is treated as stuck. */
        private const val MAX_PAGES = 500

        /** `$batch` calls in flight at once when reading steps; To Do serves about four requests per mailbox at once. */
        private const val PARALLEL_BATCHES = 1

        /** Times a throttled part of a step batch is asked again before the sync backs off. */
        private const val BATCH_RETRIES = 3
        private val MIN_BATCH_PAUSE = 500.milliseconds
        private val MAX_BATCH_PAUSE = 30.seconds

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
