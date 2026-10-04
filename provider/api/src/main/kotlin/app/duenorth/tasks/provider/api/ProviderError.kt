package app.duenorth.tasks.provider.api

import kotlin.time.Duration

/**
 * Every provider failure, so the sync engine never sees HTTP or SDK exceptions.
 * The reaction to each is in contracts/task-provider.md, "Errors".
 */
sealed class ProviderError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The user must sign in again. Sync stops and the account is marked NEEDS_SIGN_IN. */
    class AuthRequired(cause: Throwable? = null) : ProviderError("Sign-in required", cause)

    /** The list or task no longer exists remotely. */
    class NotFound(val id: String, cause: Throwable? = null) : ProviderError("Not found: $id", cause)

    /** The remote copy changed since it was read (etag mismatch). */
    class Conflict(val id: String, cause: Throwable? = null) : ProviderError("Conflict on $id", cause)

    /** Back off for at least [retryAfter]. */
    class RateLimited(val retryAfter: Duration, cause: Throwable? = null) :
        ProviderError("Rate limited, retry after $retryAfter", cause)

    /** The change cursor is too old; fetch the list again from scratch. */
    class CursorExpired(val listId: String, cause: Throwable? = null) :
        ProviderError("Cursor expired for list $listId", cause)

    /** Network or server trouble worth retrying. */
    class Transient(message: String, cause: Throwable? = null) : ProviderError(message, cause)
}
