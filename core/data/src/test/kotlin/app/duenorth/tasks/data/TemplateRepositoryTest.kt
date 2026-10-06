package app.duenorth.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** specs/004-templates: saving, editing and using templates, and clearing them on sign-out. */
@RunWith(RobolectricTestRunner::class)
class TemplateRepositoryTest {
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var templates: TemplateRepository
    private var storesOrder = true
    private var nextId = 0
    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC)
    private val list = "list-1"
    private val start = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock, { storesOrder }) { "id${++nextId}" }
        templates = TemplateRepository(db, tasks, clock) { "t${++nextId}" }
        db.accountDao().upsert(AccountEntity(provider = ProviderKind.FAKE, displayName = "Demo", email = null))
        db.taskListDao().insert(
            TaskListEntity(localId = list, remoteId = "r-list", title = "Packing", localUpdatedAt = clock.instant())
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun savingATaskCopiesItsContentUnticked() = runBlocking {
        val task = task("chargers", notes = "phone, watch", due = start, important = true)
        db.stepDao().insert(StepEntity(localId = "s1", taskId = task, title = "phone", done = true, sortOrder = 0))
        db.stepDao().insert(StepEntity(localId = "s2", taskId = task, title = "watch", sortOrder = 1))

        val id = templates.saveTaskAsTemplate(task)

        val saved = templates.templateTask(id).first()!!
        assertEquals("chargers", saved.title)
        assertEquals("phone, watch", saved.notes)
        assertTrue(saved.important)
        assertNull(saved.templateListId)
        assertNull(saved.dueOffsetDays)
        assertEquals(listOf("phone", "watch"), templates.steps(id).first().map { it.title })
        assertEquals(listOf(id), templates.taskTemplates().first().map { it.task.id })
    }

    @Test
    fun savingAListCountsOffsetsFromTheEarliestDueDate() = runBlocking {
        task("water the plants", due = start, position = "3")
        task("passport", due = start.minusDays(1), position = "2")
        task("book parking", due = start.minusDays(3), position = "1")
        task("chargers", position = "4")
        task("old", completed = true)

        val saved = templates.saveListAsTemplate(list, shadeStep = 2)

        val summary = templates.listTemplates().first().single()
        assertEquals("Packing", summary.list.name)
        assertEquals(2, summary.list.shadeStep)
        assertEquals(5, summary.taskCount)
        assertEquals(3, summary.datedCount)
        assertEquals(0, saved.leftOut)
        val rows = templates.tasksIn(saved.id).first().map { it.task.title to it.task.dueOffsetDays }
        assertEquals(
            listOf("book parking" to 0, "passport" to 2, "water the plants" to 3, "chargers" to null, "old" to null),
            rows
        )
    }

    @Test
    fun usingAListTemplateMakesAQueuedListWithRealDatesInTemplateOrder() = runBlocking {
        val id = templates.createListTemplate("trip packing")
        val parking = templates.createTemplateTask("book parking", id)
        templates.editTemplateTask(parking, dueOffset = Patch.Set(-3))
        val chargers = templates.createTemplateTask("chargers", id)
        templates.addTemplateStep(chargers, "phone")
        templates.addTemplateStep(chargers, "laptop")
        val plants = templates.createTemplateTask("water the plants", id)
        templates.editTemplateTask(plants, dueOffset = Patch.Set(0))
        clearOps()

        val newList = templates.useListTemplate(id, "Denver trip", start)

        assertEquals("Denver trip", db.taskListDao().get(newList)!!.title)
        val created = db.taskDao().openInList(newList).sortedWith(OrderKeys.taskComparator)
        assertEquals(listOf("book parking", "chargers", "water the plants"), created.map { it.title })
        assertEquals(listOf(start.minusDays(3), null, start), created.map { it.dueDate })
        val stepTitles = db.stepDao().forTask(created[1].localId).map { it.title }
        assertEquals(listOf("phone", "laptop"), stepTitles)
        val queued = db.pendingOperationDao().all()
        assertEquals(EntityType.LIST to OperationKind.CREATE, queued.first().entity to queued.first().kind)
        // Google puts each new task on top, so the first task must be sent last.
        val taskCreates = queued.filter { it.entity == EntityType.TASK }.map { it.entityLocalId }
        assertEquals(created.map { it.localId }.reversed(), taskCreates)
        // The template is unchanged.
        assertEquals(3, templates.tasksIn(id).first().size)
    }

    @Test
    fun usingAListTemplateKeepsOrderWhereTheServiceDoesNot() = runBlocking {
        storesOrder = false
        val id = templates.createListTemplate("weekly review")
        templates.createTemplateTask("inbox", id)
        templates.createTemplateTask("calendar", id)
        templates.createTemplateTask("goals", id)

        val newList = templates.useListTemplate(id, "weekly review", start)

        val created = db.taskDao().openInList(newList).sortedWith(OrderKeys.taskComparator)
        assertEquals(listOf("inbox", "calendar", "goals"), created.map { it.title })
    }

    @Test
    fun usingATaskTemplateAddsItDueFromTodayAndMarksItUsed() = runBlocking {
        val id = templates.createTemplateTask("pay rent")
        templates.editTemplateTask(id, dueOffset = Patch.Set(3), important = true)
        templates.addTemplateStep(id, "transfer")
        clearOps()

        templates.useTaskTemplate(id, list, LocalDate.of(2026, 10, 6), taskId = "new-task")

        val task = db.taskDao().get("new-task")!!
        assertEquals("pay rent", task.title)
        assertEquals(LocalDate.of(2026, 10, 9), task.dueDate)
        assertTrue(task.important)
        assertEquals(listOf("transfer"), db.stepDao().forTask("new-task").map { it.title })
        assertEquals(clock.instant(), templates.templateTask(id).first()!!.lastUsedAt)
        assertTrue(
            db.pendingOperationDao().all().any {
                it.entityLocalId == "new-task" &&
                    it.kind == OperationKind.CREATE
            }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun aTaskTemplateCannotBeDueBeforeItIsUsed() = runBlocking {
        val id = templates.createTemplateTask("pay rent")
        templates.editTemplateTask(id, dueOffset = Patch.Set(-1))
    }

    @Test
    fun reorderingAListTemplateRenumbersItsTasks() = runBlocking {
        val id = templates.createListTemplate("move house")
        val a = templates.createTemplateTask("a", id)
        val b = templates.createTemplateTask("b", id)
        val c = templates.createTemplateTask("c", id)

        templates.moveTemplateTasks(id, listOf(c, a, b))

        assertEquals(listOf("c", "a", "b"), templates.tasksIn(id).first().map { it.task.title })
    }

    @Test
    fun signingOutRemovesEveryTemplate() = runBlocking {
        val listTemplate = templates.createListTemplate("trip")
        templates.addTemplateStep(templates.createTemplateTask("passport", listTemplate), "renew")
        templates.addTemplateStep(templates.createTemplateTask("pay rent"), "transfer")

        AccountRepository(db).disconnect()

        assertTrue(templates.listTemplates().first().isEmpty())
        assertTrue(templates.taskTemplates().first().isEmpty())
        db.query("SELECT COUNT(*) FROM template_step", null).use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private suspend fun task(
        title: String,
        notes: String? = null,
        due: LocalDate? = null,
        important: Boolean = false,
        position: String? = null,
        completed: Boolean = false
    ): String {
        val id = "task-${++nextId}"
        db.taskDao().insert(
            TaskEntity(
                localId = id,
                listId = list,
                title = title,
                notes = notes,
                dueDate = due,
                important = important,
                position = position,
                completed = completed,
                completedAt = if (completed) clock.instant() else null,
                localUpdatedAt = clock.instant()
            )
        )
        return id
    }

    private suspend fun clearOps() {
        db.pendingOperationDao().all().forEach { db.pendingOperationDao().delete(it.seq) }
    }
}
