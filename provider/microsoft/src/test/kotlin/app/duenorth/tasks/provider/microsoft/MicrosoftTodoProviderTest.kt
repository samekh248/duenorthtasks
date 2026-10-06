package app.duenorth.tasks.provider.microsoft

import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.StepDraft
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.microsoft.graph.PREFER_LARGE_PAGES
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Mapping, request shapes and error handling, checked against recorded Graph responses. */
class MicrosoftTodoProviderTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val server = MockWebServer().apply { start() }
    private val auth = TestAuth()
    private val provider = MicrosoftTodoProvider.create(auth, server.url("/v1.0/"), zone = { chicago })

    @AfterEach
    fun stop() = server.close()

    private fun fixture(name: String) = javaClass.getResource("/graph/$name")!!.readText()
        .replace("BASE_URL", server.url("/v1.0/").toString())

    private fun respond(status: Int, body: String = "", vararg headers: Pair<String, String>) = server.enqueue(
        MockResponse(
            status,
            headersOf(
                "Content-Type",
                "application/json",
                *headers.flatMap {
                    listOf(it.first, it.second)
                }.toTypedArray()
            ),
            body
        )
    )

    private fun sentJson(): JsonObject = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test
    fun readsListsAndFlagsTheDefaultOne() = runTest {
        respond(200, fixture("lists.json"))

        val lists = provider.getLists()

        assertEquals(listOf("Tasks" to true, "Groceries" to false), lists.map { it.title to it.isDefault })
        assertEquals("W/\"vVwdQvxCiE6779iYhchMrAAGgwrltg==\"", lists.first().etag)
        assertEquals("Bearer ${FakeGraphServer.TOKEN}", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun mapsARecordedTask() = runTest {
        respond(200, fixture("task.json"))

        // An empty patch just reads the task back.
        val task = provider.updateTask("AAMkADIyAAAhrbPWAAA=", "AlMKXwbQAAAJws6wcAAAA=", TaskPatch())

        assertEquals("Plan the camping trip", task.title)
        assertEquals("Book the site at Starved Rock\nBring & share snacks", task.notes)
        // 05:00 UTC is midnight in Chicago, which is how the To Do apps store a due date.
        assertEquals(LocalDate.of(2026, 10, 6), task.dueDate)
        assertTrue(task.important)
        assertFalse(task.completed)
        assertEquals("inProgress", task.rawStatus)
        assertEquals(
            listOf("Reserve the campsite" to true, "Buy firewood" to false),
            task.steps.map {
                it.title to
                    it.done
            }
        )
        assertEquals("2026-10-03T18:02:09.417106300Z", task.updatedAt.toString())
        assertEquals(
            "/v1.0/me/todo/lists/AAMkADIyAAAhrbPWAAA=/tasks/AlMKXwbQAAAJws6wcAAAA=?%24expand=checklistItems",
            server.takeRequest().target
        )
    }

    @Test
    fun readsADeltaPageWithRemovedItems() = runTest {
        respond(200, fixture("delta.json"))

        val page = provider.getTaskChanges("AAMkADIyAAAhrbPWAAA=", null)

        assertEquals(listOf("Return library books"), page.changed.map { it.title })
        assertTrue(page.changed.single().completed)
        assertNull(page.changed.single().notes)
        assertEquals(listOf("AlMKXwbQAAAJws6wcAAAC="), page.deletedIds)
        assertFalse(page.hasMore)
        assertTrue(page.nextCursor.endsWith("\$deltatoken=ki-Ow8ZLz4bFQLzFLCpXpnXwJFSAiqvqhdvNHvW2QjLp"))
    }

    @Test
    fun newStepsAreChainedSoTheyKeepTheirOrder() = runTest {
        respond(200, """{"responses":[{"id":"1","status":200},{"id":"2","status":201},{"id":"3","status":201}]}""")
        respond(200, """{"id":"t1","checklistItems":[]}""")

        provider.updateTask(
            "l1",
            "t1",
            TaskPatch(steps = listOf(StepPatch.Update("s1", done = true), StepPatch.Add("A"), StepPatch.Add("B")))
        )

        val requests = sentJson().getValue("requests").jsonArray.map { it.jsonObject }
        assertEquals(listOf("PATCH", "POST", "POST"), requests.map { it.getValue("method").jsonPrimitive.content })
        assertEquals(listOf(null, null, "[\"2\"]"), requests.map { it["dependsOn"]?.toString() })
        assertEquals(
            "/me/todo/lists/l1/tasks/t1/checklistItems/s1",
            requests.first().getValue("url").jsonPrimitive.content
        )
    }

    @Test
    fun aBareUtcMidnightDueDateKeepsItsDay() = runTest {
        // Some other clients write the due day as midnight UTC; that must not become the 5th in Chicago.
        respond(
            200,
            """{"id":"t1","dueDateTime":{"dateTime":"2026-10-06T00:00:00.0000000","timeZone":"UTC"},"checklistItems":[]}"""
        )

        val task = provider.updateTask("l1", "t1", TaskPatch())

        assertEquals(LocalDate.of(2026, 10, 6), task.dueDate)
    }

    @Test
    fun writesDueDatesAsLocalMidnightInUtc() = runTest {
        respond(201, """{"id":"t1","title":"Vote","status":"notStarted"}""")

        provider.createTask("l1", TaskDraft("Vote", dueDate = LocalDate.of(2026, 11, 3)))

        // November 3 is after the switch to standard time: Chicago midnight is 06:00 UTC.
        val due = sentJson().getValue("dueDateTime").jsonObject
        assertEquals("2026-11-03T06:00:00.0000000", due.getValue("dateTime").jsonPrimitive.content)
        assertEquals("UTC", due.getValue("timeZone").jsonPrimitive.content)
    }

    @Test
    fun patchSendsOnlyTheChangedFields() = runTest {
        respond(200, """{"id":"t1","title":"Buy oat milk","status":"notStarted"}""")
        respond(200, """{"value":[]}""")

        provider.updateTask("l1", "t1", TaskPatch(title = "Buy oat milk", dueDate = Patch.Clear))

        val sent = sentJson()
        assertEquals(setOf("title", "dueDateTime"), sent.keys)
        assertEquals(JsonNull, sent["dueDateTime"])
    }

    @Test
    fun uncompletingRestoresTheStatusTheTaskHadBefore() = runTest {
        respond(200, """{"id":"t1","status":"inProgress","checklistItems":[]}""")
        respond(200, """{"id":"t1","status":"notStarted","checklistItems":[]}""")

        val reopened = provider.updateTask("l1", "t1", TaskPatch(completed = false, reopenStatus = "inProgress"))
        provider.updateTask("l1", "t1", TaskPatch(completed = false))

        assertEquals("inProgress", sentJson().getValue("status").jsonPrimitive.content)
        assertEquals("notStarted", sentJson().getValue("status").jsonPrimitive.content)
        assertEquals("inProgress", reopened.rawStatus)
        assertFalse(reopened.completed)
    }

    @Test
    fun importanceTogglesBetweenNormalAndHigh() = runTest {
        respond(200, """{"id":"t1","importance":"normal","checklistItems":[]}""")

        val task = provider.updateTask("l1", "t1", TaskPatch(important = false))

        assertEquals("normal", sentJson().getValue("importance").jsonPrimitive.content)
        assertFalse(task.important)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "401, AuthRequired",
        "403, NotAllowed",
        "404, NotFound",
        "409, Conflict",
        "412, Conflict",
        "429, RateLimited",
        "500, Transient",
        "502, Transient"
    )
    fun mapsHttpErrors(status: Int, expected: String) = runTest {
        respond(status, """{"error":{"code":"x","message":"x"}}""")

        val error = assertThrows<ProviderError> { provider.updateTask("l1", "t1", TaskPatch(title = "x")) }

        assertEquals(expected, error::class.simpleName)
    }

    @Test
    fun throttlingCarriesRetryAfter() = runTest {
        respond(429, fixture("error-throttled.json"), "Retry-After" to "7")

        val error = assertThrows<ProviderError.RateLimited> { provider.getLists() }

        assertEquals(7.seconds, error.retryAfter)
    }

    @Test
    fun serviceUnavailableWithRetryAfterBacksOff() = runTest {
        respond(503, "", "Retry-After" to "3")

        val error = assertThrows<ProviderError.RateLimited> { provider.getLists() }

        assertEquals(3.seconds, error.retryAfter)
    }

    @Test
    fun expiredDeltaTokenMeansFetchAgain() = runTest {
        respond(400, fixture("error-sync-state.json"))
        respond(410, "")

        val cursor = server.url("/v1.0/me/todo/lists/l1/tasks/delta?\$deltatoken=old").toString()
        val first = assertThrows<ProviderError.CursorExpired> { provider.getTaskChanges("l1", cursor) }
        val second = assertThrows<ProviderError.CursorExpired> { provider.getTaskChanges("l1", cursor) }

        assertEquals("l1", first.listId)
        assertEquals("l1", second.listId)
    }

    @Test
    fun aCursorOutsideGraphIsNeverFollowed() = runTest {
        assertThrows<ProviderError.CursorExpired> {
            provider.getTaskChanges("l1", "https://evil.example.com/v1.0/me/todo/lists/l1/tasks/delta")
        }

        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun droppedConnectionIsTransient() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())

        assertThrows<ProviderError.Transient> { provider.getLists() }
    }

    @Test
    fun noTokenMeansSignInAgainAndNothingIsSent() = runTest {
        auth.token = null

        assertThrows<ProviderError.AuthRequired> { provider.getLists() }

        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }
}

/** Step handling and unknown-field safety, against the stateful fake Graph endpoint. */
class MicrosoftTodoStepsTest {
    private val graph = FakeGraphServer()
    private val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl, zone = {
        ZoneId.of("Europe/Berlin")
    })

    @AfterEach
    fun stop() = graph.shutdown()

    @Test
    fun stepChangesGoOutInOneBatch() = runTest {
        val list = provider.createList("Trip")
        val task = provider.createTask(list.id, TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Hat"))))
        graph.calls.clear()

        val (socks, hat) = task.steps
        val updated = provider.updateTask(
            list.id,
            task.id,
            TaskPatch(
                steps = listOf(
                    StepPatch.Update(socks.id, done = true),
                    StepPatch.Remove(hat.id),
                    StepPatch.Add("Shoes")
                )
            )
        )

        assertEquals(listOf("Socks" to true, "Shoes" to false), updated.steps.map { it.title to it.done })
        assertEquals(1, graph.calls.count { it == "POST /\$batch" })
        // One round trip carries all three step edits, then the task is read back once.
        assertEquals(listOf("POST", "PATCH", "DELETE", "POST", "GET"), graph.calls.map { it.substringBefore(' ') })
    }

    @Test
    fun manyStepsAreSplitIntoBatchesOfTwenty() = runTest {
        val list = provider.createList("Trip")
        graph.calls.clear()

        val task = provider.createTask(list.id, TaskDraft("Pack", steps = (1..25).map { StepDraft("Item $it") }))

        assertEquals(25, task.steps.size)
        assertEquals((1..25).map { "Item $it" }, task.steps.map { it.title })
        assertEquals(2, graph.calls.count { it == "POST /\$batch" })
    }

    @Test
    fun aFailedStepInsideABatchIsReported() = runTest {
        val list = provider.createList("Trip")
        val task = provider.createTask(list.id, TaskDraft("Pack"))

        val error = assertThrows<ProviderError.NotFound> {
            provider.updateTask(list.id, task.id, TaskPatch(steps = listOf(StepPatch.Update("gone", done = true))))
        }

        assertEquals("gone", error.id)
    }

    @Test
    fun removingAStepThatIsAlreadyGoneIsFine() = runTest {
        val list = provider.createList("Trip")
        val task = provider.createTask(list.id, TaskDraft("Pack"))

        val updated = provider.updateTask(list.id, task.id, TaskPatch(steps = listOf(StepPatch.Remove("gone"))))

        assertTrue(updated.steps.isEmpty())
    }

    @Test
    fun fieldsDueNorthDoesNotModelSurviveEdits() = runTest {
        val list = provider.createList("Trip")
        val task = provider.createTask(list.id, TaskDraft("Pack", dueDate = LocalDate.of(2026, 10, 6)))
        graph.editRemotely(
            task.id,
            Json.parseToJsonElement("""{"categories":["Red"],"isReminderOn":true}""").jsonObject
        )

        provider.updateTask(list.id, task.id, TaskPatch(title = "Pack bags", completed = true))

        val raw = graph.rawTask(task.id)
        assertEquals("""["Red"]""", raw.getValue("categories").toString())
        assertEquals("true", raw.getValue("isReminderOn").toString())
        // Midnight in Berlin (UTC+2 in October) is 22:00 UTC the day before, and reads back as the 6th.
        assertEquals(
            "2026-10-05T22:00:00.0000000",
            raw.getValue("dueDateTime").jsonObject.getValue("dateTime").jsonPrimitive.content
        )
        assertEquals(LocalDate.of(2026, 10, 6), provider.getTaskChanges(list.id, null).changed.single().dueDate)
    }

    @Test
    fun aBigListReadsStepsWithItsTasksNotOneCallPerTask() = runTest {
        val graph = FakeGraphServer(pageSize = 50, expandOnDelta = false)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.bigList(45)
            graph.prefers.clear()

            val page = provider.getTaskChanges(list.id, null)

            assertStepsMatch(45, page.changed)
            // One delta page plus one read of the list with its steps, instead of 45 separate requests.
            assertEquals(listOf("GET /me/todo/lists/${list.id}/tasks"), graph.calls.takeLast(1))
            assertEquals(2, graph.prefers.size)
            assertEquals(PREFER_LARGE_PAGES, graph.prefers.first())
        } finally {
            graph.shutdown()
        }
    }

    @Test
    fun withoutExpandedListsStepsComeInBatches() = runTest {
        val graph = FakeGraphServer(pageSize = 50, expandOnDelta = false, expandOnList = false)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.bigList(45)
            graph.prefers.clear()

            val page = provider.getTaskChanges(list.id, null)

            assertStepsMatch(45, page.changed)
            // Delta, the list read that brought no steps, then three $batch calls of up to 20.
            assertEquals(5, graph.prefers.size)
        } finally {
            graph.shutdown()
        }
    }

    @Test
    fun aThrottledStepBatchIsAskedAgainInsteadOfFailingTheSync() = runTest {
        val graph = FakeGraphServer(pageSize = 50, expandOnDelta = false, expandOnList = false)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.bigList(10)
            // A busy mailbox turns most of the batch away; only those are asked again.
            graph.throttleBatchItems = 6
            graph.calls.clear()

            val page = provider.getTaskChanges(list.id, null)

            assertStepsMatch(10, page.changed)
            assertEquals(2, graph.calls.count { it == "POST /\$batch" })
        } finally {
            graph.shutdown()
        }
    }

    @Test
    fun openTasksArriveWithStepsAndAreNotReadAgainByTheFullFetch() = runTest {
        val graph = FakeGraphServer(pageSize = 200, expandOnDelta = false)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.bigList(30)
            val done = provider.getTaskChanges(list.id, null).changed.take(25)
            done.forEach { provider.updateTask(list.id, it.id, TaskPatch(completed = true)) }
            val cursorless = provider.createList("Fresh")
            graph.calls.clear()

            val open = provider.getOpenTasks(list.id)!!

            assertStepsMatch(5, open)
            assertEquals(listOf("GET /me/todo/lists/${list.id}/tasks"), graph.calls)
            graph.calls.clear()
            val full = provider.getTaskChanges(list.id, null)
            assertStepsMatch(30, full.changed)
            // The rest of the round reads only the completed tasks' steps.
            assertEquals(
                listOf("GET /me/todo/lists/${list.id}/tasks/delta", "GET /me/todo/lists/${list.id}/tasks"),
                graph.calls
            )
            assertEquals(emptyList<RemoteTask>(), provider.getOpenTasks(cursorless.id))
        } finally {
            graph.shutdown()
        }
    }

    @Test
    fun aListThatCannotBeFilteredFallsBackToTheFullFetch() = runTest {
        val graph = FakeGraphServer(pageSize = 50, expandOnDelta = false, filterOnList = false)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.bigList(25)

            assertNull(provider.getOpenTasks(list.id))
            assertStepsMatch(25, provider.getTaskChanges(list.id, null).changed)
        } finally {
            graph.shutdown()
        }
    }

    private suspend fun MicrosoftTodoProvider.bigList(size: Int): RemoteList {
        val list = createList("Big")
        repeat(size) { createTask(list.id, TaskDraft("Task $it", steps = listOf(StepDraft("Step $it")))) }
        return list
    }

    private fun assertStepsMatch(count: Int, tasks: List<RemoteTask>) {
        assertEquals(count, tasks.size)
        assertTrue(tasks.all { it.steps.single().title == "Step " + it.title.removePrefix("Task ") })
    }

    @Test
    fun aDeltaThatNeverEndsStopsInsteadOfSpinning() = runTest {
        val graph = FakeGraphServer(pageSize = 2, loopDelta = true)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            val list = provider.createList("Loop")
            repeat(3) { provider.createTask(list.id, TaskDraft("Task $it")) }

            var pages = 0
            assertThrows<ProviderError.Transient> {
                var page = provider.getTaskChanges(list.id, null)
                while (page.hasMore && pages++ < 20) page = provider.getTaskChanges(list.id, page.nextCursor)
            }
            assertTrue(pages <= 2, "stopped after $pages extra pages")
        } finally {
            graph.shutdown()
        }
    }

    @Test
    fun theBuiltInTasksListIsTheDefault() = runTest {
        val graph = FakeGraphServer(withDefaultList = true)
        try {
            val provider = MicrosoftTodoProvider.create(TestAuth(), graph.baseUrl)
            assertTrue(provider.getLists().single().isDefault)
        } finally {
            graph.shutdown()
        }
    }
}
