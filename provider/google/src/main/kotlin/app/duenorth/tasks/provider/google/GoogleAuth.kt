package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.SignInHost

/** The OAuth scope the app asks for, and nothing more (FR-030). */
const val GOOGLE_TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

/**
 * Signs in and hands out access tokens for [GOOGLE_TASKS_SCOPE]. The real one is
 * [AndroidGoogleAuth]; tests use a fake so they never need a Google account.
 */
interface GoogleAuth {
    /** Interactive: picks the account and asks for Tasks access. */
    suspend fun signIn(host: SignInHost): AccountInfo

    suspend fun signOut()

    /**
     * A current access token, without UI. With [forceRefresh] the cached token is dropped first
     * (after a 401). Throws [app.duenorth.tasks.provider.api.ProviderError.AuthRequired] when the
     * user has to sign in again.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String
}
