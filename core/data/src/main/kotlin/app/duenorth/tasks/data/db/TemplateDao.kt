package app.duenorth.tasks.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A list template with how many tasks it has, and how many of those have a due offset. */
data class TemplateListSummary(@Embedded val list: TemplateListEntity, val taskCount: Int, val datedCount: Int)

/** A template task with its step count, for rows that show "3 steps". */
data class TemplateTaskSummary(@Embedded val task: TemplateTaskEntity, val stepCount: Int)

@Dao
interface TemplateDao {
    @Query(
        """
        SELECT l.*,
            (SELECT COUNT(*) FROM template_task t WHERE t.templateListId = l.id) AS taskCount,
            (SELECT COUNT(*) FROM template_task t WHERE t.templateListId = l.id AND t.dueOffsetDays IS NOT NULL)
                AS datedCount
        FROM template_list l ORDER BY l.name COLLATE NOCASE
        """
    )
    fun observeLists(): Flow<List<TemplateListSummary>>

    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM template_step s WHERE s.templateTaskId = t.id) AS stepCount
        FROM template_task t WHERE t.templateListId IS NULL ORDER BY t.title COLLATE NOCASE
        """
    )
    fun observeTaskTemplates(): Flow<List<TemplateTaskSummary>>

    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM template_step s WHERE s.templateTaskId = t.id) AS stepCount
        FROM template_task t WHERE t.templateListId = :listId ORDER BY t.sortOrder
        """
    )
    fun observeTasksIn(listId: String): Flow<List<TemplateTaskSummary>>

    @Query("SELECT * FROM template_list WHERE id = :id")
    fun observeList(id: String): Flow<TemplateListEntity?>

    @Query("SELECT * FROM template_list WHERE id = :id")
    suspend fun getList(id: String): TemplateListEntity?

    @Query("SELECT * FROM template_task WHERE id = :id")
    fun observeTask(id: String): Flow<TemplateTaskEntity?>

    @Query("SELECT * FROM template_task WHERE id = :id")
    suspend fun getTask(id: String): TemplateTaskEntity?

    @Query("SELECT * FROM template_task WHERE templateListId = :listId ORDER BY sortOrder")
    suspend fun tasksIn(listId: String): List<TemplateTaskEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM template_task WHERE templateListId = :listId")
    suspend fun maxTaskOrder(listId: String): Int

    @Query("SELECT * FROM template_step WHERE templateTaskId = :taskId ORDER BY sortOrder")
    fun observeSteps(taskId: String): Flow<List<TemplateStepEntity>>

    @Query("SELECT * FROM template_step WHERE templateTaskId = :taskId ORDER BY sortOrder")
    suspend fun stepsFor(taskId: String): List<TemplateStepEntity>

    @Query("SELECT * FROM template_step WHERE templateTaskId IN (:taskIds) ORDER BY sortOrder")
    suspend fun stepsForTasks(taskIds: List<String>): List<TemplateStepEntity>

    @Query("SELECT * FROM template_step WHERE id = :id")
    suspend fun getStep(id: String): TemplateStepEntity?

    @Insert
    suspend fun insertList(list: TemplateListEntity)

    @Update
    suspend fun updateList(list: TemplateListEntity)

    @Query("DELETE FROM template_list WHERE id = :id")
    suspend fun deleteList(id: String)

    @Insert
    suspend fun insertTasks(tasks: List<TemplateTaskEntity>)

    @Update
    suspend fun updateTask(task: TemplateTaskEntity)

    @Query("DELETE FROM template_task WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Insert
    suspend fun insertSteps(steps: List<TemplateStepEntity>)

    @Update
    suspend fun updateStep(step: TemplateStepEntity)

    @Query("DELETE FROM template_step WHERE id = :id")
    suspend fun deleteStep(id: String)
}
