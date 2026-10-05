package app.duenorth.tasks.provider.microsoft

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.microsoft.auth.MicrosoftAccountAuth

/** Stands in for MSAL: hands out [FakeGraphServer.TOKEN] while signed in. */
class TestAuth(var token: String? = FakeGraphServer.TOKEN) : MicrosoftAccountAuth {
    var signedIn = false
        private set

    override suspend fun signIn(host: SignInHost): AccountInfo {
        signedIn = true
        return AccountInfo(id = "account-1", displayName = "Dustin", email = "dustin@example.com")
    }

    override suspend fun signOut() {
        signedIn = false
    }

    override fun accessToken(): String = token ?: throw ProviderError.AuthRequired()
}

object TestHost : SignInHost
