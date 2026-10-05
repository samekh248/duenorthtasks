package app.duenorth.tasks.provider.microsoft.graph

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The ~10 Microsoft Graph calls the To Do provider needs (research R7), relative to
 * `https://graph.microsoft.com/v1.0/`. Bodies of writes are [JsonObject]s so a PATCH carries
 * exactly the fields being changed, including explicit nulls for cleared fields.
 */
internal interface GraphTodoApi {
    @GET("me/todo/lists")
    suspend fun lists(): GraphPage<TodoTaskListDto>

    /** Follows an `@odata.nextLink` from [lists]. */
    @GET
    suspend fun listsPage(@Url url: String): GraphPage<TodoTaskListDto>

    @POST("me/todo/lists")
    suspend fun createList(@Body body: JsonObject): TodoTaskListDto

    @PATCH("me/todo/lists/{listId}")
    suspend fun updateList(@Path("listId") listId: String, @Body body: JsonObject): TodoTaskListDto

    @DELETE("me/todo/lists/{listId}")
    suspend fun deleteList(@Path("listId") listId: String)

    /** Starts a delta round for one list: every task now, then a delta link for later changes. */
    @GET("me/todo/lists/{listId}/tasks/delta")
    suspend fun taskDelta(
        @Path("listId") listId: String,
        @Query("\$expand") expand: String = "checklistItems",
        @Header("Prefer") prefer: String = PREFER_LARGE_PAGES
    ): GraphPage<TodoTaskDto>

    /** Follows an `@odata.nextLink` or a stored `@odata.deltaLink`. */
    @GET
    suspend fun taskDeltaPage(
        @Url url: String,
        @Header("Prefer") prefer: String = PREFER_LARGE_PAGES
    ): GraphPage<TodoTaskDto>

    @GET("me/todo/lists/{listId}/tasks/{taskId}")
    suspend fun task(
        @Path("listId") listId: String,
        @Path("taskId") taskId: String,
        @Query("\$expand") expand: String = "checklistItems"
    ): TodoTaskDto

    @POST("me/todo/lists/{listId}/tasks")
    suspend fun createTask(@Path("listId") listId: String, @Body body: JsonObject): TodoTaskDto

    @PATCH("me/todo/lists/{listId}/tasks/{taskId}")
    suspend fun updateTask(
        @Path("listId") listId: String,
        @Path("taskId") taskId: String,
        @Body body: JsonObject
    ): TodoTaskDto

    @DELETE("me/todo/lists/{listId}/tasks/{taskId}")
    suspend fun deleteTask(@Path("listId") listId: String, @Path("taskId") taskId: String)

    @GET("me/todo/lists/{listId}/tasks/{taskId}/checklistItems")
    suspend fun checklistItems(
        @Path("listId") listId: String,
        @Path("taskId") taskId: String
    ): GraphPage<ChecklistItemDto>

    @POST("\$batch")
    suspend fun batch(@Body body: BatchRequestDto): BatchResponseDto
}

/**
 * Asks for bigger delta pages than Graph's small default, so a first sync takes a few round trips
 * instead of dozens. Graph may still send fewer; paging handles that.
 */
internal const val PREFER_LARGE_PAGES = "odata.maxpagesize=200"
