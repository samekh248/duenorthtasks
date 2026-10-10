package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.settings.ListOrderStore
import app.duenorth.tasks.settings.ListShadeStore
import app.duenorth.tasks.settings.ListShades
import app.duenorth.tasks.settings.PinnedListStore
import app.duenorth.tasks.settings.PinnedLists
import app.duenorth.tasks.settings.ThemeSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Theme, accent, list shades (US5), list order (spec 003) and pinned lists: phone-only settings in
 * DataStore.
 */
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

    @Provides
    @Singleton
    fun listOrderStore(@ApplicationContext context: Context): ListOrderStore = ListOrderStore.create(context)

    @Provides
    @Singleton
    fun listOrder(store: ListOrderStore, tasks: TaskRepository, accounts: AccountRepository): ListOrder =
        ListOrder(store, tasks, accounts)

    @Provides
    @Singleton
    fun pinnedListStore(@ApplicationContext context: Context): PinnedListStore = PinnedListStore.create(context)

    @Provides
    @Singleton
    fun pinnedLists(store: PinnedListStore, tasks: TaskRepository, accounts: AccountRepository): PinnedLists =
        PinnedLists(store, tasks, accounts)
}
