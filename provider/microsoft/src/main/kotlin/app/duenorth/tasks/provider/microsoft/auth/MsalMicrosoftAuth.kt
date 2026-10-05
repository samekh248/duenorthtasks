package app.duenorth.tasks.provider.microsoft.auth

import android.content.Context
import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.SignInHost
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Microsoft sign-in through MSAL for Android (task T051): single-account mode, `common` authority
 * so both personal Microsoft accounts and work or school accounts work, scope `Tasks.ReadWrite`.
 * MSAL keeps the tokens in its own encrypted store; this class never persists one.
 *
 * [clientId] and [signatureHash] come from the Entra app registration (plan.md, "External setup")
 * through untracked `local.properties`, never from the repo.
 */
class MsalMicrosoftAuth(context: Context, private val clientId: String, private val signatureHash: String) :
    MicrosoftAccountAuth {
    private val appContext = context.applicationContext

    @Volatile
    private var app: ISingleAccountPublicClientApplication? = null

    override suspend fun signIn(host: SignInHost): AccountInfo {
        val activity = (host as? ActivitySignInHost)?.activity
            ?: throw IllegalArgumentException("Microsoft sign-in needs an ActivitySignInHost")
        val app = application()
        withContext(Dispatchers.IO) {
            // Single-account mode refuses a second sign-in; start clean (constitution Principle III).
            if (app.currentAccount?.currentAccount != null) app.signOut()
        }
        val result = suspendCancellableCoroutine { cont ->
            app.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(SCOPES)
                    .withCallback(
                        object : AuthenticationCallback {
                            override fun onSuccess(result: IAuthenticationResult) = cont.resume(result)

                            override fun onError(exception: MsalException) =
                                cont.resumeWithException(ProviderError.AuthRequired(exception))

                            override fun onCancel() = cont.resumeWithException(
                                ProviderError.AuthRequired(IllegalStateException("Sign-in cancelled"))
                            )
                        }
                    )
                    .build()
            )
        }
        return result.account.toAccountInfo()
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            val app = application()
            if (app.currentAccount?.currentAccount != null) app.signOut()
        }
    }

    override fun accessToken(): String {
        val app = runCatching { applicationBlocking() }.getOrElse { throw ProviderError.AuthRequired(it) }
        val account = app.currentAccount?.currentAccount ?: throw ProviderError.AuthRequired()
        return try {
            app.acquireTokenSilent(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(SCOPES)
                    .build()
            ).accessToken
        } catch (e: MsalUiRequiredException) {
            throw ProviderError.AuthRequired(e)
        } catch (e: MsalException) {
            // Network trouble, a throttled token endpoint, and similar: retry later.
            throw IOException("Could not refresh the Microsoft token", e)
        }
    }

    private suspend fun application(): ISingleAccountPublicClientApplication =
        app ?: withContext(Dispatchers.IO) { applicationBlocking() }

    @Synchronized
    private fun applicationBlocking(): ISingleAccountPublicClientApplication = app ?: run {
        check(clientId.isNotBlank()) {
            "Microsoft To Do is not set up: add msal.clientId and msal.signatureHash to local.properties"
        }
        PublicClientApplication.createSingleAccountPublicClientApplication(appContext, writeConfig())
            .also { app = it }
    }

    /** MSAL reads its configuration from a file; build it from the injected ids. */
    private fun writeConfig(): File {
        val redirectUri = "msauth://${appContext.packageName}/${URLEncoder.encode(signatureHash, "UTF-8")}"
        val config = buildJsonObject {
            put("client_id", clientId)
            put("redirect_uri", redirectUri)
            put("authorization_user_agent", "DEFAULT")
            put("account_mode", "SINGLE")
            put("broker_redirect_uri_registered", false)
            putJsonArray("authorities") {
                addJsonObject {
                    put("type", "AAD")
                    put("default", true)
                    putJsonObject("audience") {
                        put("type", "AzureADandPersonalMicrosoftAccount")
                        put("tenant_id", "common")
                    }
                }
            }
        }
        return File(appContext.noBackupFilesDir, "msal_config.json").apply { writeText(config.toString()) }
    }

    private fun IAccount.toAccountInfo() = AccountInfo(
        id = id,
        displayName = (claims?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: username,
        email = username.takeIf { '@' in it }
    )

    private companion object {
        /** MSAL adds openid, profile and offline_access itself. */
        val SCOPES = listOf("Tasks.ReadWrite")
    }
}
