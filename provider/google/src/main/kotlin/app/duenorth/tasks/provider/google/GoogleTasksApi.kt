package app.duenorth.tasks.provider.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Google Tasks REST API v1 (research R6), only the calls the app makes.
 * Writes take a [JsonObject] so a PATCH carries exactly the fields that changed, explicit nulls
 * included (FR-024).
 */
internal interface GoogleTasksApi {
    @GET("users/@me/lists")
    suspend fun lists(
        @Query("maxResults") maxResults: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): Response<TaskListsDto>

    @POST("users/@me/lists")
    suspend fun insertList(@Body body: JsonObject): Response<TaskListDto>

    @PATCH("users/@me/lists/{list}")
    suspend fun patchList(@Path("list") listId: String, @Body body: JsonObject): Response<TaskListDto>

    @DELETE("users/@me/lists/{list}")
    suspend fun deleteList(@Path("list") listId: String): Response<Unit>

    @GET("lists/{list}/tasks")
    suspend fun tasks(
        @Path("list") listId: String,
        @Query("updatedMin") updatedMin: String? = null,
        @Query("showDeleted") showDeleted: Boolean = false,
        @Query("showHidden") showHidden: Boolean = true,
        @Query("showCompleted") showCompleted: Boolean = true,
        @Query("maxResults") maxResults: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): Response<TasksDto>

    @GET("lists/{list}/tasks/{task}")
    suspend fun task(@Path("list") listId: String, @Path("task") taskId: String): Response<TaskDto>

    @POST("lists/{list}/tasks")
    suspend fun insertTask(
        @Path("list") listId: String,
        @Body body: JsonObject,
        @Query("parent") parent: String? = null,
        @Query("previous") previous: String? = null
    ): Response<TaskDto>

    @PATCH("lists/{list}/tasks/{task}")
    suspend fun patchTask(
        @Path("list") listId: String,
        @Path("task") taskId: String,
        @Body body: JsonObject
    ): Response<TaskDto>

    @POST("lists/{list}/tasks/{task}/move")
    suspend fun moveTask(
        @Path("list") listId: String,
        @Path("task") taskId: String,
        @Query("parent") parent: String? = null,
        @Query("previous") previous: String? = null
    ): Response<TaskDto>

    @DELETE("lists/{list}/tasks/{task}")
    suspend fun deleteTask(@Path("list") listId: String, @Path("task") taskId: String): Response<Unit>
}

@Serializable
internal data class TaskListsDto(val items: List<TaskListDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class TaskListDto(
    val id: String,
    val title: String = "",
    val etag: String? = null,
    /** RFC 3339 timestamp. */
    val updated: String? = null
)

@Serializable
internal data class TasksDto(val items: List<TaskDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class TaskDto(
    val id: String,
    val etag: String? = null,
    val title: String? = null,
    val notes: String? = null,
    /** "needsAction" or "completed". */
    val status: String? = null,
    /** RFC 3339; Google keeps only the date part. */
    val due: String? = null,
    val completed: String? = null,
    val updated: String? = null,
    /** Set on subtasks: the id of the parent task (one level only). */
    val parent: String? = null,
    val position: String? = null,
    val deleted: Boolean = false,
    val hidden: Boolean = false
)

@Serializable
internal data class GoogleErrorDto(val error: GoogleErrorBody? = null)

@Serializable
internal data class GoogleErrorBody(
    val code: Int = 0,
    val message: String? = null,
    val status: String? = null,
    val errors: List<GoogleErrorReason> = emptyList()
)

@Serializable
internal data class GoogleErrorReason(val reason: String? = null, val message: String? = null)

internal object GoogleStatus {
    const val NEEDS_ACTION = "needsAction"
    const val COMPLETED = "completed"
}
