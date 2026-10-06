package app.duenorth.tasks.provider.api

/**
 * The only seam between the app and a remote task service (constitution Principle III).
 *
 * Implemented by the Google Tasks, Microsoft To Do and fake providers. Nothing outside the
 * `provider:*` modules sees Google or Microsoft types; every failure surfaces as [ProviderError].
 * See specs/001-metro-todo-app/contracts/task-provider.md.
 */
interface TaskProvider {
    val kind: ProviderKind
    val capabilities: ProviderCapabilities

    /** Interactive sign-in. [host] is supplied by the app and wraps the current Activity. */
    suspend fun signIn(host: SignInHost): AccountInfo

    suspend fun signOut()

    suspend fun getLists(): List<RemoteList>

    suspend fun createList(title: String): RemoteList

    suspend fun updateList(id: String, patch: ListPatch): RemoteList

    suspend fun deleteList(id: String)

    /** Changes in list [listId] since [cursor]; a null cursor means a full fetch. Call again while [TaskChangePage.hasMore]. */
    suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage

    /**
     * Every task in list [listId] that is not completed, with its steps, or null when the service
     * cannot filter by status. Lets a first sync show what matters before the full history arrives;
     * the full fetch through [getTaskChanges] still follows and stays the source of truth.
     */
    suspend fun getOpenTasks(listId: String): List<RemoteTask>? = null

    suspend fun createTask(listId: String, draft: TaskDraft): RemoteTask

    /** Sends only the non-null fields of [patch] (PATCH semantics, FR-024). */
    suspend fun updateTask(listId: String, id: String, patch: TaskPatch): RemoteTask

    /** Moves task [id] to just after [afterId], or to the top when null. No-op when ordering is unsupported. */
    suspend fun moveTask(listId: String, id: String, afterId: String?)

    suspend fun deleteTask(listId: String, id: String)
}

enum class ProviderKind { GOOGLE, MICROSOFT, FAKE }

data class ProviderCapabilities(
    /** Microsoft To Do only. */
    val importance: Boolean,
    /** Google Tasks only. */
    val manualOrder: Boolean,
    /** False for both in v1: the UI edits dates only. */
    val dueTime: Boolean,
    /** Lists can be shared with other people (Microsoft To Do). */
    val sharedLists: Boolean = false,
    /** Tasks can be assigned to the user from Docs or Chat (Google Tasks). */
    val assignedTasks: Boolean = false
)

/** Opaque handle to whatever the platform sign-in flow needs (an Activity on Android). */
interface SignInHost

data class AccountInfo(val id: String, val displayName: String, val email: String?)
