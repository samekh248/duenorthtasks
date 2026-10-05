package app.duenorth.tasks.provider.microsoft.graph

import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.microsoft.auth.AccessTokenSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0/"

internal val graphJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun graphApi(
    baseUrl: HttpUrl,
    tokens: AccessTokenSource,
    client: OkHttpClient = OkHttpClient()
): GraphTodoApi {
    val http = client.newBuilder()
        .addInterceptor(BearerTokenInterceptor(baseUrl, tokens))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(graphJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GraphTodoApi::class.java)
}

/** Signals "sign in again" through OkHttp, which only lets [IOException]s out of interceptors. */
internal class AuthRequiredIOException(cause: Throwable? = null) : IOException("Microsoft sign-in required", cause)

/**
 * Adds the MSAL access token to calls to Graph, and only to Graph: a stored delta link that
 * points anywhere else never receives the token. Runs on OkHttp's threads, never the main thread.
 */
private class BearerTokenInterceptor(private val baseUrl: HttpUrl, private val tokens: AccessTokenSource) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isUnder(baseUrl)) throw IOException("Refusing to call ${request.url.host}")
        val token = try {
            tokens.accessToken()
        } catch (e: ProviderError.AuthRequired) {
            throw AuthRequiredIOException(e)
        }
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

internal fun HttpUrl.isUnder(base: HttpUrl): Boolean =
    scheme == base.scheme && host == base.host && port == base.port && encodedPath.startsWith(base.encodedPath)

/** True when [url] is a Graph link we may follow (a stored delta or next link). */
internal fun HttpUrl.allows(url: String): Boolean = runCatching { url.toHttpUrl().isUnder(this) }.getOrDefault(false)

/**
 * Runs one Graph call and turns every failure into a [ProviderError] (contracts/task-provider.md,
 * "Errors"). [id] names the list or task the call is about; [listId] is set for delta calls so an
 * expired link maps to [ProviderError.CursorExpired].
 */
internal suspend fun <T> graphCall(id: String, listId: String? = null, block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val response = e.response()
    val body = runCatching { response?.errorBody()?.string() }.getOrNull()
    throw httpError(e.code(), response?.headers()?.get("Retry-After"), errorCode(body), id, listId, e)
} catch (e: ProviderError) {
    throw e
} catch (e: AuthRequiredIOException) {
    throw ProviderError.AuthRequired(e.cause ?: e)
} catch (e: IOException) {
    throw ProviderError.Transient("Network error talking to Microsoft To Do", e)
} catch (e: SerializationException) {
    throw ProviderError.Transient("Unexpected response from Microsoft To Do", e)
}

private val cursorErrorCodes = setOf("syncStateNotFound", "syncStateInvalid", "resyncRequired", "badDeltaToken")

internal fun httpError(
    status: Int,
    retryAfter: String?,
    code: String?,
    id: String,
    listId: String?,
    cause: Throwable? = null
): ProviderError = when {
    status == 401 || status == 403 -> ProviderError.AuthRequired(cause)
    status == 404 -> ProviderError.NotFound(id, cause)
    status == 409 || status == 412 -> ProviderError.Conflict(id, cause)
    status == 429 -> ProviderError.RateLimited(parseRetryAfter(retryAfter), cause)
    listId != null && (status == 410 || code in cursorErrorCodes) -> ProviderError.CursorExpired(listId, cause)
    status == 503 && retryAfter != null -> ProviderError.RateLimited(parseRetryAfter(retryAfter), cause)
    else -> ProviderError.Transient("Microsoft To Do answered $status${code?.let { " ($it)" }.orEmpty()}", cause)
}

private val DEFAULT_RETRY_AFTER = 10.seconds

/** Graph sends Retry-After in seconds. */
internal fun parseRetryAfter(value: String?): Duration =
    value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds ?: DEFAULT_RETRY_AFTER

internal fun errorCode(body: String?): String? = body?.let {
    runCatching { graphJson.decodeFromString(GraphErrorBody.serializer(), it).error?.code }.getOrNull()
}
