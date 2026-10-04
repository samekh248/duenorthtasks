package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.SignInHost

/** Hands out numbered tokens; [revoked] makes every refresh fail like a revoked grant. */
class FakeGoogleAuth : GoogleAuth {
    var issued = 0
        private set
    var refreshes = 0
        private set
    var revoked = false
    var signedOut = false
        private set

    override suspend fun signIn(host: SignInHost) =
        AccountInfo(id = "dustin@example.com", displayName = "Dustin", email = "dustin@example.com")

    override suspend fun signOut() {
        signedOut = true
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        if (forceRefresh) {
            refreshes++
            if (revoked) throw ProviderError.AuthRequired()
            issued++
        }
        if (issued == 0) issued = 1
        return "token-$issued"
    }
}

internal fun googleProvider(server: FakeGoogleTasksServer, auth: GoogleAuth = FakeGoogleAuth()): GoogleTasksProvider {
    val bearer = BearerToken()
    return GoogleTasksProvider(GoogleTasksClient.api(server.baseUrl, bearer), auth, bearer)
}
