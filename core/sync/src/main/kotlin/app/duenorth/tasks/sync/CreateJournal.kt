package app.duenorth.tasks.sync

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers creates that were sent but not yet confirmed (FR-022). Neither service accepts a
 * client-chosen id, so when an answer is lost the only way to find the copy that may have been
 * made is by what was sent. The title is kept here, because the user may rename the task before
 * the retry.
 */
interface CreateJournal {
    fun record(localId: String, sentTitle: String)

    fun sentTitle(localId: String): String?

    fun clear(localId: String)
}

class InMemoryCreateJournal : CreateJournal {
    private val sent = ConcurrentHashMap<String, String>()

    override fun record(localId: String, sentTitle: String) {
        sent[localId] = sentTitle
    }

    override fun sentTitle(localId: String): String? = sent[localId]

    override fun clear(localId: String) {
        sent.remove(localId)
    }
}

/** Survives the process being killed mid-sync, which is when answers get lost. */
class PreferencesCreateJournal(context: Context) : CreateJournal {
    private val prefs = context.getSharedPreferences("sync_create_journal", Context.MODE_PRIVATE)

    override fun record(localId: String, sentTitle: String) {
        prefs.edit().putString(localId, sentTitle).commit()
    }

    override fun sentTitle(localId: String): String? = prefs.getString(localId, null)

    override fun clear(localId: String) {
        prefs.edit().remove(localId).apply()
    }
}
