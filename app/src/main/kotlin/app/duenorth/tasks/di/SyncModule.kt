package app.duenorth.tasks.di

import android.content.Context
import androidx.work.WorkManager
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.sync.AccountConnector
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.sync.PreferencesCreateJournal
import app.duenorth.tasks.sync.SyncEngine
import app.duenorth.tasks.sync.SyncScheduler
import app.duenorth.tasks.sync.SyncSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The sync engine and when it runs (US2). Screens use [ListHolds], [SyncSettingsStore] and [AccountConnector]. */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun listHolds(): ListHolds = ListHolds()

    @Provides
    @Singleton
    fun syncEngine(
        @ApplicationContext context: Context,
        db: DueNorthDatabase,
        registry: ProviderRegistry,
        holds: ListHolds,
        clock: Clock
    ): SyncEngine = SyncEngine(db, registry, holds, clock, journal = PreferencesCreateJournal(context))

    @Provides
    @Singleton
    fun syncSettings(@ApplicationContext context: Context): SyncSettingsStore = SyncSettingsStore.create(context)

    @Provides
    @Singleton
    fun syncScheduler(
        @ApplicationContext context: Context,
        settings: SyncSettingsStore,
        repository: TaskRepository
    ): SyncScheduler = SyncScheduler(
        workManager = WorkManager.getInstance(context),
        settings = settings,
        localEdits = repository.localEdits,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    @Provides
    @Singleton
    fun accountConnector(
        db: DueNorthDatabase,
        providers: Map<ProviderKind, @JvmSuppressWildcards Provider<TaskProvider>>,
        scheduler: SyncScheduler
    ): AccountConnector = AccountConnector(
        accounts = db.accountDao(),
        providers = providers.mapValues { (_, provider) -> { provider.get() } },
        onConnected = scheduler::syncNow
    )
}
