package app.duenorth.tasks.provider.google

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.SignInHost
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What Google sign-in needs from the screen: the current Activity, and a way to show Google's
 * consent page and get its result back. The app implements this around its Activity.
 */
interface GoogleSignInHost : SignInHost {
    val activity: Activity

    /** Shows [intentSender] and returns the result data, or null when the user backed out. */
    suspend fun launchForResult(intentSender: IntentSender): Intent?
}

/**
 * Google sign-in with Credential Manager ("Sign in with Google") for the identity, then Google
 * Identity Services `AuthorizationClient` for a [GOOGLE_TASKS_SCOPE] access token (research R6).
 *
 * Tokens stay in Google Play services; only the signed-in email is kept here, in private
 * preferences, so background syncs can ask for a token for the right account without UI.
 */
class AndroidGoogleAuth(private val context: Context, private val webClientId: String) : GoogleAuth {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val authorization = Identity.getAuthorizationClient(context)
    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    override suspend fun signIn(host: SignInHost): AccountInfo {
        val screen = host as? GoogleSignInHost
            ?: error("Google sign-in needs a GoogleSignInHost, got ${host::class.simpleName}")
        check(webClientId.isNotBlank()) { "google.webClientId is not set in local.properties" }

        val credential = try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            CredentialManager.create(screen.activity).getCredential(screen.activity, request).credential
        } catch (e: GetCredentialException) {
            throw ProviderError.AuthRequired(e)
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val email = google.id
        prefs.edit().putString(KEY_EMAIL, email).apply()

        var result = authorization.authorize(request(email)).await()
        if (result.hasResolution()) {
            val sender = checkNotNull(result.pendingIntent).intentSender
            val data = screen.launchForResult(sender) ?: throw ProviderError.AuthRequired()
            result = try {
                authorization.getAuthorizationResultFromIntent(data)
            } catch (e: ApiException) {
                throw ProviderError.AuthRequired(e)
            }
        }
        cachedToken = result.accessToken ?: throw ProviderError.AuthRequired()
        return AccountInfo(id = email, displayName = google.displayName ?: email, email = email)
    }

    override suspend fun signOut() {
        cachedToken?.let { clear(it) }
        cachedToken = null
        prefs.edit().clear().apply()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = mutex.withLock {
        if (forceRefresh) cachedToken?.let { clear(it) }.also { cachedToken = null }
        cachedToken?.let { return@withLock it }
        val email = prefs.getString(KEY_EMAIL, null) ?: throw ProviderError.AuthRequired()
        val result: AuthorizationResult = try {
            authorization.authorize(request(email)).await()
        } catch (e: ApiException) {
            throw ProviderError.AuthRequired(e)
        }
        // Needing UI means the grant was revoked: the user has to sign in again.
        if (result.hasResolution()) throw ProviderError.AuthRequired()
        (result.accessToken ?: throw ProviderError.AuthRequired()).also { cachedToken = it }
    }

    private suspend fun clear(token: String) {
        runCatching { authorization.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    private fun request(email: String): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(GOOGLE_TASKS_SCOPE)))
        .setAccount(Account(email, ACCOUNT_TYPE))
        .build()

    private companion object {
        const val PREFS = "google_tasks_account"
        const val KEY_EMAIL = "email"
        const val ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
