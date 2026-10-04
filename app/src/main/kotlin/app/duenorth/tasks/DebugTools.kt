package app.duenorth.tasks

import android.app.Activity
import android.os.StrictMode
import android.util.Log
import androidx.metrics.performance.JankStats

/**
 * Debug-build guards for constitution Principle II: main-thread disk or network access is reported,
 * and every janky frame is logged. Both are no-ops in release builds.
 */
object DebugTools {
    fun install() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    fun trackJank(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        JankStats.createAndTrack(activity.window) { frame ->
            if (frame.isJank) {
                Log.w("Jank", "${frame.frameDurationUiNanos / 1_000_000} ms: ${frame.states}")
            }
        }
    }
}
