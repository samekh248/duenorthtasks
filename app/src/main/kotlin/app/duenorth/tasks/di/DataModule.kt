package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.TaskRepository
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
    fun taskRepository(db: DueNorthDatabase, clock: Clock): TaskRepository = TaskRepository(db, clock)
}
