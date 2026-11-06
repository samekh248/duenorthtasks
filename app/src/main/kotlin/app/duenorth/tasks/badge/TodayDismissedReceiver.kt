package app.duenorth.tasks.badge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The today notification's delete intent. The badge is drawn from that notification, so when the
 * person swipes it away (Android 14+ lets ongoing notifications be swiped) the badge would go with
 * it while tasks are still due. Post it again from Room: it comes back while the count is above 0
 * and stays gone when the count is 0 or the badge setting is off.
 */
class TodayDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val badge = EntryPointAccessors.fromApplication(context.applicationContext, BadgeEntryPoint::class.java)
            .todayBadge()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                badge.updateNow()
            } finally {
                pending.finish()
            }
        }
    }
}
