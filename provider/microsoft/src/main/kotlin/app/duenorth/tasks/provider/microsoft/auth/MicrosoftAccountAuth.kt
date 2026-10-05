package app.duenorth.tasks.provider.microsoft.auth

import android.app.Activity
import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.SignInHost

/** Supplies Graph access tokens. */
fun interface AccessTokenSource {
    /**
     * A valid access token, refreshed silently when needed. Blocks, so it is called only from
     * OkHttp's background threads. Throws [app.duenorth.tasks.provider.api.ProviderError.AuthRequired]
     * when the user has to sign in again.
     */
    fun accessToken(): String
}

/** Sign-in for one Microsoft account at a time (MSAL single-account mode, research R7). */
interface MicrosoftAccountAuth : AccessTokenSource {
    suspend fun signIn(host: SignInHost): AccountInfo

    suspend fun signOut()
}

/**
 * The [SignInHost] the app passes when signing in to Microsoft: MSAL shows its browser sign-in
 * from this Activity.
 */
interface ActivitySignInHost : SignInHost {
    val activity: Activity
}
