package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.BuildConfig
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.microsoft.MicrosoftTodoProvider
import app.duenorth.tasks.provider.microsoft.auth.MsalMicrosoftAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/** Binds Microsoft To Do into the provider map (user story 3). Created only when that account is used. */
@Module
@InstallIn(SingletonComponent::class)
object MicrosoftProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.MICROSOFT)
    fun microsoftTodo(@ApplicationContext context: Context): TaskProvider = MicrosoftTodoProvider.create(
        MsalMicrosoftAuth(context, BuildConfig.MSAL_CLIENT_ID, BuildConfig.MSAL_SIGNATURE_HASH)
    )
}
