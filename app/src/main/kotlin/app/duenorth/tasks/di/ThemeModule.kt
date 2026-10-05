package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.settings.ListShadeStore
import app.duenorth.tasks.settings.ListShades
import app.duenorth.tasks.settings.ThemeSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Theme, accent and list shades (US5): phone-only settings in DataStore. */
@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    @Provides
    @Singleton
    fun themeSettings(@ApplicationContext context: Context): ThemeSettingsStore = ThemeSettingsStore.create(context)

    @Provides
    @Singleton
    fun listShadeStore(@ApplicationContext context: Context): ListShadeStore = ListShadeStore.create(context)

    @Provides
    @Singleton
    fun listShades(store: ListShadeStore, tasks: TaskRepository, accounts: AccountRepository): ListShades =
        ListShades(store, tasks, accounts)
}
