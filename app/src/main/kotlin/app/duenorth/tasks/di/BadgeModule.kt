package app.duenorth.tasks.di

import android.content.Context
import app.duenorth.tasks.badge.TodayBadge
import app.duenorth.tasks.badge.TodayBadgeSettings
import app.duenorth.tasks.badge.TodayNotifier
import app.duenorth.tasks.data.repo.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The app icon badge: today's count, posted as one quiet notification. */
@Module
@InstallIn(SingletonComponent::class)
object BadgeModule {
    @Provides
    @Singleton
    fun todayBadgeSettings(@ApplicationContext context: Context): TodayBadgeSettings =
        TodayBadgeSettings.create(context)

    @Provides
    @Singleton
    fun todayNotifier(@ApplicationContext context: Context): TodayNotifier = TodayNotifier(context)

    @Provides
    @Singleton
    fun todayBadge(
        @ApplicationContext context: Context,
        tasks: TaskRepository,
        settings: TodayBadgeSettings,
        notifier: TodayNotifier,
        clock: Clock
    ): TodayBadge = TodayBadge(
        context,
        tasks,
        settings,
        notifier,
        clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )
}
