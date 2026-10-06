package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.Assignment
import app.duenorth.tasks.provider.api.AssignmentSource
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.StepDraft
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleTasksProviderTest {
    private val server = FakeGoogleTasksServer()
    private val auth = FakeGoogleAuth()
    private val provider = googleProvider(server, auth)

    @AfterEach
    fun close() = server.close()

    @Test
    fun capabilitiesMatchGoogle() {
        assertFalse(provider.capabilities.importance)
        assertTrue(provider.capabilities.manualOrder)
        assertFalse(provider.capabilities.dueTime)
    }

    @Test
    fun tasksAssignedFromDocsAndChatAreIncludedWithTheirSource() = runTest {
        val list = provider.getLists().single()
        val doc = provider.createTask(list.id, TaskDraft("Review section 3"))
        val chat = provider.createTask(list.id, TaskDraft("Book the room"))
        provider.createTask(list.id, TaskDraft("Plain task"))
        server.assignOnWeb(doc.id, "DOCUMENT", "https://docs.google.com/document/d/abc")
        server.assignOnWeb(chat.id, "SPACE", null)

        val tasks = provider.getTaskChanges(list.id, null).changed.associateBy { it.title }

        // Google leaves assigned tasks out unless asked (spec 002, R11).
        assertTrue(server.requests.any { it.url.queryParameter("showAssigned") == "true" })
        assertEquals(
            Assignment(AssignmentSource.DOCUMENT, "https://docs.google.com/document/d/abc"),
            tasks.getValue("Review section 3").assignment
        )
        assertEquals(Assignment(AssignmentSource.SPACE, null), tasks.getValue("Book the room").assignment)
        assertEquals(null, tasks.getValue("Plain task").assignment)
        assertTrue(provider.capabilities.assignedTasks)
        assertFalse(provider.capabilities.sharedLists)
    }

    @Test
    fun listsAreNeverShared() = runTest {
        provider.createList("Errands")
        assertTrue(provider.getLists().none { it.isShared || !it.isOwner })
    }

    @Test
    fun theFirstListIsTheDefault() = runTest {
        provider.createList("Errands")
        val lists = provider.getLists()
        assertEquals(listOf("My Tasks" to true, "Errands" to false), lists.map { it.title to it.isDefault })
    }

    @Test
    fun sendsTheAccessToken() = runTest {
        provider.getLists()
        assertEquals("token-1", server.tokens.last())
    }

    @Test
    fun subtasksBecomeStepsInOrderAndNeverTasks() = runTest {
        val task = provider.createTask(
            "default",
            TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Shoes", done = true), StepDraft("Hat")))
        )
        val page = provider.getTaskChanges("default", null)

        assertEquals(listOf(task.id), page.changed.map { it.id })
        assertEquals(
            listOf("Socks" to false, "Shoes" to true, "Hat" to false),
            page.changed.single().steps.map { it.title to it.done }
        )
    }

    @Test
    fun aSubtaskEditedOnTheWebReportsItsParent() = runTest {
        val task = provider.createTask("default", TaskDraft("Pack", steps = listOf(StepDraft("Socks"))))
        provider.createTask("default", TaskDraft("Unrelated"))
        val cursor = provider.getTaskChanges("default", null).nextCursor

        server.editOnWeb(task.steps.single().id, status = GoogleStatus.COMPLETED)
        val page = provider.getTaskChanges("default", cursor)

        assertEquals(listOf(task.id), page.changed.map { it.id })
        assertTrue(page.changed.single().steps.single().done)
        assertTrue(task.id !in page.deletedIds)
    }

    @Test
    fun aSubtaskDeletedOnTheWebDropsTheStep() = runTest {
        val task = provider.createTask(
            "default",
            TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Hat")))
        )
        val cursor = provider.getTaskChanges("default", null).nextCursor

        server.deleteOnWeb(task.steps.first().id)
        val page = provider.getTaskChanges("default", cursor)

        assertEquals(listOf("Hat"), page.changed.single().steps.map { it.title })
    }

    @Test
    fun aTaskIndentedOnTheWebLeavesTheTaskListAndJoinsItsParentsSteps() = runTest {
        val parent = provider.createTask("default", TaskDraft("Pack"))
        val child = provider.createTask("default", TaskDraft("Socks"))
        val cursor = provider.getTaskChanges("default", null).nextCursor

        server.indentOnWeb(child.id, parent.id)
        val page = provider.getTaskChanges("default", cursor)

        assertEquals(listOf(child.id), page.deletedIds)
        assertEquals(listOf("Socks"), page.changed.single { it.id == parent.id }.steps.map { it.title })
    }

    @Test
    fun aParentDeletedOnTheWebIsReportedOnceWithoutItsSubtasks() = runTest {
        val task = provider.createTask("default", TaskDraft("Pack", steps = listOf(StepDraft("Socks"))))
        val cursor = provider.getTaskChanges("default", null).nextCursor

        provider.deleteTask("default", task.id)
        val page = provider.getTaskChanges("default", cursor)

        assertEquals(listOf(task.id), page.deletedIds)
        assertTrue(page.changed.isEmpty())
    }

    @Test
    fun aNewTaskTheFullListingHasNotCaughtUpWithIsChangedNotDeleted() = runTest {
        provider.createTask("default", TaskDraft("Old"))
        val cursor = provider.getTaskChanges("default", null).nextCursor

        val created = provider.createTask("default", TaskDraft("Just added", steps = listOf(StepDraft("Step"))))
        server.lagFullListing(created.id)
        val page = provider.getTaskChanges("default", cursor)

        assertFalse(created.id in page.deletedIds)
        val changed = page.changed.single()
        assertEquals(created.id, changed.id)
        assertEquals("Just added", changed.title)
        assertEquals(listOf("Step"), changed.steps.map { it.title })
    }

    @Test
    fun anUnchangedListCostsOneRequest() = runTest {
        provider.createTask("default", TaskDraft("Pack"))
        val cursor = provider.getTaskChanges("default", null).nextCursor
        val before = server.requests.size

        val page = provider.getTaskChanges("default", cursor)

        assertTrue(page.changed.isEmpty())
        assertEquals(1, server.requests.size - before)
    }

    @Test
    fun patchSendsOnlyChangedFieldsAndNullToClear() = runTest {
        val task = provider.createTask(
            "default",
            TaskDraft("Buy milk", notes = "Oat", dueDate = LocalDate.of(2026, 10, 5))
        )

        provider.updateTask("default", task.id, TaskPatch(notes = Patch.Clear))

        val patch = server.requests.last { it.method == "PATCH" }
        val body = Json.parseToJsonElement(checkNotNull(patch.body).utf8()).jsonObject
        assertEquals(setOf("notes"), body.keys)
        assertEquals(JsonNull, body["notes"])
    }

    @Test
    fun importanceIsNeverSent() = runTest {
        val task = provider.createTask("default", TaskDraft("Star me", important = true))

        val updated = provider.updateTask("default", task.id, TaskPatch(title = "Starred?", important = true))

        assertFalse(updated.important)
        server.requests.filter { it.method == "POST" || it.method == "PATCH" }.forEach {
            assertFalse(checkNotNull(it.body).utf8().contains("importan"))
        }
    }

    @Test
    fun dueDatesAreMidnightUtcDates() = runTest {
        val task = provider.createTask("default", TaskDraft("Pay rent", dueDate = LocalDate.of(2026, 11, 1)))

        val post = server.requests.last { it.method == "POST" }
        assertTrue(checkNotNull(post.body).utf8().contains("\"due\":\"2026-11-01T00:00:00.000Z\""))
        assertEquals(LocalDate.of(2026, 11, 1), task.dueDate)
    }

    @Test
    fun uncompletingClearsTheCompletedStamp() = runTest {
        val task = provider.createTask("default", TaskDraft("Buy milk"))
        provider.updateTask("default", task.id, TaskPatch(completed = true))

        val reopened = provider.updateTask("default", task.id, TaskPatch(completed = false))

        assertFalse(reopened.completed)
        assertNull(reopened.completedAt)
        val body = checkNotNull(server.requests.last { it.method == "PATCH" }.body).utf8()
        assertTrue(body.contains("\"completed\":null"))
    }

    @Test
    fun moveSendsThePreviousTask() = runTest {
        val first = provider.createTask("default", TaskDraft("First"))
        val second = provider.createTask("default", TaskDraft("Second"))

        provider.moveTask("default", first.id, afterId = second.id)

        val move = server.requests.last()
        assertEquals("POST", move.method)
        assertEquals(second.id, move.url.queryParameter("previous"))
    }

    @Test
    fun aGarbledCursorAsksForAFullFetch() = runTest {
        assertThrows<ProviderError.CursorExpired> { provider.getTaskChanges("default", "not-a-cursor") }
    }

    @Nested
    inner class Errors {
        @Test
        fun expiredTokenIsRefreshedOnceAndRetried() = runTest {
            server.failNext(401, readFixture("error_unauthenticated.json"))

            provider.getLists()

            assertEquals(1, auth.refreshes)
            assertEquals(listOf("token-1", "token-2"), server.tokens.toList())
        }

        @Test
        fun revokedAccessIsAuthRequired() = runTest {
            server.failNext(401, readFixture("error_unauthenticated.json"))
            auth.revoked = true

            assertThrows<ProviderError.AuthRequired> { provider.getLists() }
        }

        @Test
        fun quota403IsRateLimited() = runTest {
            server.failNext(403, readFixture("error_rate_limit.json"))

            val error = assertThrows<ProviderError.RateLimited> { provider.getLists() }
            assertEquals(30.seconds, error.retryAfter)
        }

        @Test
        fun otherForbiddenIsNotAllowedNotSignIn() = runTest {
            server.failNext(403)

            assertThrows<ProviderError.NotAllowed> { provider.getLists() }
        }

        @Test
        fun status429UsesRetryAfter() = runTest {
            server.failNext(429, headers = mapOf("Retry-After" to "7"))

            val error = assertThrows<ProviderError.RateLimited> { provider.getLists() }
            assertEquals(7.seconds, error.retryAfter)
        }

        @Test
        fun missingTaskIsNotFound() = runTest {
            val error = assertThrows<ProviderError.NotFound> {
                provider.updateTask("default", "nope", TaskPatch(title = "x"))
            }
            assertEquals("nope", error.id)
        }

        @Test
        fun goneIsCursorExpired() = runTest {
            server.failNext(410)
            assertThrows<ProviderError.CursorExpired> { provider.getTaskChanges("default", "u:2026-10-01T00:00:00Z") }
        }

        @Test
        fun preconditionFailedIsConflict() = runTest {
            server.failNext(412)
            assertThrows<ProviderError.Conflict> { provider.updateTask("default", "x", TaskPatch(title = "y")) }
        }

        @Test
        fun serverErrorIsTransient() = runTest {
            server.failNext(503)
            assertThrows<ProviderError.Transient> { provider.getLists() }
        }

        @Test
        fun droppedConnectionIsTransient() = runTest {
            // OkHttp quietly retries a dropped connection once, so drop it every time.
            repeat(3) { server.failNext(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }
            assertThrows<ProviderError.Transient> { provider.getLists() }
        }
    }

    /** Parses responses in the exact shape Google sends, with fields the app ignores. */
    @Nested
    inner class RecordedResponses {
        private val raw = MockWebServer().apply { start() }
        private val bearer = BearerToken()
        private val recorded =
            GoogleTasksProvider(GoogleTasksClient.api(raw.url("/tasks/v1/").toString(), bearer), auth, bearer)

        @AfterEach
        fun closeRaw() = raw.close()

        @Test
        fun lists() = runTest {
            raw.enqueue(json(readFixture("lists.json")))

            val lists = recorded.getLists()

            assertEquals(listOf("My Tasks", "Errands"), lists.map { it.title })
            assertTrue(lists[0].isDefault)
            assertEquals(Instant.parse("2026-10-02T09:01:12Z"), lists[1].updatedAt)
        }

        @Test
        fun tasksWithSubtasksHiddenAndLinks() = runTest {
            raw.enqueue(json(readFixture("tasks_full.json")))

            val page = recorded.getTaskChanges("VEdjVzRhTWtUZ2ZqZ2xYQQ", null)

            assertEquals(listOf("Pack for the trip", "Return library books"), page.changed.map { it.title })
            val pack = page.changed[0]
            assertEquals("Check the weather first", pack.notes)
            assertEquals(LocalDate.of(2026, 10, 6), pack.dueDate)
            assertEquals(listOf("Socks" to true, "Shoes" to false), pack.steps.map { it.title to it.done })
            val done = page.changed[1]
            assertTrue(done.completed)
            assertEquals(Instant.parse("2026-10-01T08:00:00Z"), done.completedAt)
            assertFalse(page.hasMore)
        }

        private fun json(body: String) =
            MockResponse(200, Headers.headersOf("Content-Type", "application/json; charset=UTF-8"), body)
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("google/$name")) { "Missing fixture $name" }.readText()
}
