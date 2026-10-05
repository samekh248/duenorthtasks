package app.duenorth.tasks.ui.account

import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.common.serviceName
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** What to tell the user when signing in to [kind] failed, in plain words. */
internal fun signInError(kind: ProviderKind, error: Exception): String {
    if (error is CancellationException) throw error
    val service = serviceName(kind)
    return when (error) {
        is ProviderError.AuthRequired -> "Couldn't sign in to $service. Try again."
        is ProviderError.Transient, is IOException -> "Couldn't reach $service. Check your connection and try again."
        else -> "Couldn't sign in to $service."
    }
}
