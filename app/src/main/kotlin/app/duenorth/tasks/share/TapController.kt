package app.duenorth.tasks.share

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import app.duenorth.tasks.MainActivity
import app.duenorth.tasks.R
import app.duenorth.tasks.data.repo.RoomShareStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** The reader phone's page, step by step (spec 011, mockup columns 1, 2 and 5). */
sealed interface ReaderState {
    data object Idle : ReaderState

    /** Waiting for a touch. [retry] after the phones parted too soon. */
    data class Waiting(val retry: Boolean = false) : ReaderState

    /** Touching: the other phone answered. [tiles] is null when the phones met before. */
    data class Meeting(val name: String, val tiles: List<TileFace>?, val answered: Boolean = false) : ReaderState

    /** The touched phone isn't running Due North (not installed, or locked). */
    data object NotDueNorth : ReaderState

    data class Done(val result: TapResult) : ReaderState
}

/** The tapped phone's invite page (mockup column 3). */
sealed interface CardState {
    data object None : CardState

    data class Invite(
        val fromName: String,
        val preview: InvitePreview,
        val tiles: List<TileFace>?,
        val sharerAnswer: Boolean? = null,
        val answered: Answer? = null
    ) : CardState

    data class Finished(val outcome: CardOutcome) : CardState

    /** The phones parted before the list arrived. Nothing was added. */
    data object Parted : CardState
}

/**
 * Runs share by tap on this phone (spec 011): the NFC reader while a share or "tap to sync" page is
 * open, and the card side for [ShareCardService]. Both sides' sessions run off the main thread;
 * the pages watch [reader] and [card].
 */
@Singleton
class TapController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RoomShareStore,
    private val identity: ShareIdentity
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    /** The phone has NFC at all (FR-811). */
    val hasNfc: Boolean get() = adapter != null

    fun nfcOn(): Boolean = adapter?.isEnabled == true

    private val readerState = MutableStateFlow<ReaderState>(ReaderState.Idle)
    val reader: StateFlow<ReaderState> = readerState.asStateFlow()

    @Volatile private var myAnswer: Boolean? = null

    @Volatile private var readerList: String? = null

    private val cardState = MutableStateFlow<CardState>(CardState.None)
    val card: StateFlow<CardState> = cardState.asStateFlow()

    @Volatile private var cardAnswer: Answer? = null

    // Reader side

    /** Turns this phone into the reader until [stopReader]. [listId] null means "tap to sync". */
    fun startReader(activity: Activity, listId: String?) {
        readerList = listId
        myAnswer = null
        readerState.value = ReaderState.Waiting()
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        adapter?.enableReaderMode(activity, ::onTag, flags, extras)
    }

    fun stopReader(activity: Activity) {
        runCatching { adapter?.disableReaderMode(activity) }
        readerState.value = ReaderState.Idle
    }

    /** "they match" (true) or "they don't" (false) on the reader phone. */
    fun answerAsReader(yes: Boolean) {
        myAnswer = yes
        (readerState.value as? ReaderState.Meeting)?.let { readerState.value = it.copy(answered = true) }
    }

    /** Runs on the NFC binder thread for each touch; blocks it for the length of the tap. */
    private fun onTag(tag: Tag) {
        if (readerState.value !is ReaderState.Waiting) return
        val iso = IsoDep.get(tag) ?: return
        val result = runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    iso.connect()
                    iso.timeout = 3_000
                }
                val link = ApduReader { apdu -> withContext(Dispatchers.IO) { iso.transceive(apdu) } }
                if (!link.select()) return@runBlocking null
                val me = identity.get()
                val name = identity.nameOrDevice()
                val session = ReaderSession(store, me, name, readerHost)
                withContext(Dispatchers.IO) {
                    readerList?.let { session.share(link, it) } ?: session.sync(link)
                }
            } catch (e: IOException) {
                TapResult.Lost
            } finally {
                runCatching { iso.close() }
            }
        }
        readerState.value = when (result) {
            null -> ReaderState.NotDueNorth
            TapResult.Lost -> ReaderState.Waiting(retry = true).also { myAnswer = null }
            else -> ReaderState.Done(result)
        }
    }

    private val readerHost = object : ReaderHost {
        override fun peerFound(name: String, tiles: List<TileFace>?) {
            // Phones that met before need no tiles: sharing a new list goes straight to their answer.
            if (tiles == null) myAnswer = true
            readerState.value = ReaderState.Meeting(name, tiles, answered = tiles == null)
        }

        override fun myAnswer(): Boolean? = myAnswer
    }

    // Card side

    /** A fresh session for one touch (called by [ShareCardService] on SELECT). */
    fun newCardSession(): (ByteArray) -> ByteArray {
        cardAnswer = null
        return CardSession(store, identity.get(), identity::nameOrDevice, cardHost)::handle
    }

    /** "they match, add" with the chosen mode, or "not now" ([Answer.NO]) on the invite page. */
    fun answerAsCard(answer: Answer) {
        cardAnswer = answer
        (cardState.value as? CardState.Invite)?.let { cardState.value = it.copy(answered = answer) }
        if (answer == Answer.NO) cardState.value = CardState.Finished(CardOutcome.Cancelled)
    }

    /** The reader moved away. An unfinished invite can't finish now; the next touch starts over. */
    fun cardParted() {
        if (cardState.value is CardState.Invite) cardState.value = CardState.Parted
    }

    /** The invite page closed. */
    fun dismissCard() {
        if (cardState.value is CardState.Invite) cardAnswer = Answer.NO
        cardState.value = CardState.None
        notifications().cancel(INVITE_NOTE)
    }

    private val cardHost = object : CardHost {
        override fun invite(fromName: String, preview: InvitePreview, tiles: List<TileFace>?) {
            cardState.value = CardState.Invite(fromName, preview, tiles)
            showInvite(fromName, preview.title)
        }

        override fun answer(): Answer? = cardAnswer

        override fun sharerAnswered(yes: Boolean?) {
            (cardState.value as? CardState.Invite)?.let { cardState.value = it.copy(sharerAnswer = yes) }
        }

        override fun finished(outcome: CardOutcome) {
            when (outcome) {
                is CardOutcome.Synced -> notifySynced(outcome)
                is CardOutcome.Refused -> Unit
                else -> {
                    cardState.value = CardState.Finished(outcome)
                    notifications().cancel(INVITE_NOTE)
                }
            }
        }
    }

    /**
     * Brings Due North up on the invite. A bound HostApduService may start an activity from the
     * background; the notification is there in case the phone doesn't allow it.
     */
    private fun showInvite(fromName: String, title: String) {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(open) }
        ensureChannel()
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE)
        notifications().notify(
            INVITE_NOTE,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_tap)
                .setContentTitle("$fromName wants to share “$title”")
                .setContentText("keep the phones together and check the tiles")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .build()
        )
    }

    private fun notifySynced(outcome: CardOutcome.Synced) {
        val received = outcome.lists.sumOf { it.receivedBy.values.sum() }
        val sent = outcome.lists.sumOf { it.sent }
        ensureChannel()
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifications().notify(
            SYNC_NOTE,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_tap)
                .setContentTitle("synced with ${outcome.fromName}")
                .setContentText(syncLine(received, sent))
                .setContentIntent(PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .setTimeoutAfter(10_000)
                .build()
        )
    }

    private fun notifications() = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        notifications().createNotificationChannel(
            NotificationChannel(CHANNEL, "share by tap", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Invites and syncs when you tap phones"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL = "share"
        private const val INVITE_NOTE = 4101
        private const val SYNC_NOTE = 4102

        fun syncLine(received: Int, sent: Int): String = when {
            received == 0 && sent == 0 -> "already up to date"
            else -> "$received ${plural(received)} in, $sent out"
        }

        private fun plural(n: Int) = if (n == 1) "change" else "changes"
    }
}
