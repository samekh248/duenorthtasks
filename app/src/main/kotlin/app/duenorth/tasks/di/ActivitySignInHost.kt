package app.duenorth.tasks.di

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.duenorth.tasks.provider.google.GoogleSignInHost
import app.duenorth.tasks.provider.microsoft.auth.ActivitySignInHost as MicrosoftSignInHost
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The [app.duenorth.tasks.provider.api.SignInHost] the sync account screen passes to
 * `AccountConnector.connect`. It wraps the current Activity and shows a provider's consent page
 * for a result. Works for both services. Built per sign-in; nothing is kept after it finishes.
 */
class ActivitySignInHost(override val activity: ComponentActivity) :
    GoogleSignInHost,
    MicrosoftSignInHost {
    override suspend fun launchForResult(intentSender: IntentSender): Intent? = suspendCancellableCoroutine { cont ->
        val key = "sign-in-${UUID.randomUUID()}"
        var launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>? = null
        launcher =
            activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                launcher?.unregister()
                cont.resume(result.data.takeIf { result.resultCode == Activity.RESULT_OK })
            }
        cont.invokeOnCancellation { launcher.unregister() }
        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
    }
}
