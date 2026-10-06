package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.SyncLogRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DueNorthDatabase = DueNorthDatabase.build(context)

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun taskRepository(db: DueNorthDatabase, clock: Clock, registry: ProviderRegistry): TaskRepository =
        TaskRepository(db, clock, serviceStoresOrder = { registry.current()?.capabilities?.manualOrder != false })

    @Provides
    @Singleton
    fun templateRepository(db: DueNorthDatabase, tasks: TaskRepository, clock: Clock): TemplateRepository =
        TemplateRepository(db, tasks, clock)

    @Provides
    @Singleton
    fun accountRepository(db: DueNorthDatabase): AccountRepository = AccountRepository(db)

    @Provides
    @Singleton
    fun syncLogRepository(db: DueNorthDatabase): SyncLogRepository = SyncLogRepository(db)
}
