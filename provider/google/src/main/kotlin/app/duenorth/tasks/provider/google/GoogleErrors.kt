package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.ProviderError
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Maps Google responses to [ProviderError] (contracts/task-provider.md, "Errors"), so nothing
 * above the provider sees HTTP.
 */
internal object GoogleErrors {
    private val json = Json { ignoreUnknownKeys = true }
    private val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded")
    private val DEFAULT_BACKOFF = 30.seconds

    /** Throws the matching [ProviderError] for a non-2xx [response] about entity [id]. */
    fun fail(response: Response<*>, id: String): Nothing {
        val code = response.code()
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        val error = body?.let { runCatching { json.decodeFromString<GoogleErrorDto>(it).error }.getOrNull() }
        val reasons = error?.errors.orEmpty().mapNotNull { it.reason }.toSet()
        val message = "Google Tasks $code: ${error?.message ?: response.message()}"
        throw when {
            code == 401 -> ProviderError.AuthRequired()
            code == 429 || (code == 403 && reasons.any { it in RATE_LIMIT_REASONS }) ->
                ProviderError.RateLimited(retryAfter(response))
            code == 403 -> ProviderError.AuthRequired()
            code == 404 -> ProviderError.NotFound(id)
            code == 410 -> ProviderError.CursorExpired(id)
            code == 412 -> ProviderError.Conflict(id)
            else -> ProviderError.Transient(message)
        }
    }

    /** Wraps transport failures; [ProviderError]s pass through untouched. */
    fun wrap(error: Throwable): Throwable = when (error) {
        is ProviderError -> error
        is IOException -> ProviderError.Transient("Network error talking to Google Tasks", error)
        is SerializationException -> ProviderError.Transient("Unexpected response from Google Tasks", error)
        else -> error
    }

    private fun retryAfter(response: Response<*>): Duration =
        response.headers()["Retry-After"]?.trim()?.toLongOrNull()?.seconds ?: DEFAULT_BACKOFF
}
