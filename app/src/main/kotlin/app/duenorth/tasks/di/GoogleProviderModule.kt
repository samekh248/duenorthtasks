package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.BuildConfig
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.google.GoogleTasksProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/** Google Tasks (US2). The web client ID comes from local.properties or CI secrets, never git. */
@Module
@InstallIn(SingletonComponent::class)
object GoogleProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.GOOGLE)
    fun googleTasks(@ApplicationContext context: Context): TaskProvider =
        GoogleTasksProvider.create(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)
}
