package app.duenorth.tasks.ui.account

import android.util.Log
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.common.serviceName
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * What to tell the user when signing in to [kind] failed, in plain words, followed by the reason
 * the service or Android gave, so a screenshot of the error says which step refused.
 */
internal fun signInError(kind: ProviderKind, error: Exception): String {
    if (error is CancellationException) throw error
    runCatching { Log.w(TAG, "Sign-in to $kind failed", error) }
    val service = serviceName(kind).ifEmpty { serviceLabel(kind) }
    val summary = when (error) {
        is ProviderError.AuthRequired -> "Couldn't sign in to $service. Try again."
        is ProviderError.Transient, is IOException -> "Couldn't reach $service. Check your connection and try again."
        else -> "Couldn't sign in to $service."
    }
    return signInReason(error)?.let { "$summary\n$it" } ?: summary
}

/**
 * The innermost cause's own words, such as "[28444] Developer console is not set up correctly."
 * from Google, or null when there is nothing beyond our own wrapper.
 */
internal fun signInReason(error: Throwable): String? {
    var root = error
    while (true) {
        root = root.cause?.takeIf { it !== root } ?: break
    }
    if (root === error && error is ProviderError) return null
    val message = root.message?.trim().orEmpty().take(MAX_REASON)
    val type = root::class.simpleName.orEmpty()
    return when {
        message.isNotEmpty() -> message
        type.isNotEmpty() -> type
        else -> null
    }
}

private const val TAG = "DueNorthSignIn"
private const val MAX_REASON = 200
