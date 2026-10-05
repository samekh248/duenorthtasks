package app.duenorth.tasks

import android.app.Application
import app.duenorth.tasks.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DueNorthApp : Application() {
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        DebugTools.install()
        syncScheduler.start()
    }
}
