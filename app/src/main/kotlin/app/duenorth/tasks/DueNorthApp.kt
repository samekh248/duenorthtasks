package app.duenorth.tasks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DueNorthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugTools.install()
    }
}
