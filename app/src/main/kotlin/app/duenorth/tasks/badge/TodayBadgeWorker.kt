package app.duenorth.tasks.badge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Just after midnight: today's count changes with the date, so the badge is posted again. */
class TodayBadgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, BadgeEntryPoint::class.java).todayBadge().updateNow()
        return Result.success()
    }

    companion object {
        const val NAME = "today-badge"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BadgeEntryPoint {
    fun todayBadge(): TodayBadge
}
