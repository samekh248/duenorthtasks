package app.duenorth.tasks.share

import java.io.IOException
import kotlinx.coroutines.delay

/** Carries one message to the card phone and returns its reply. Throws [IOException] when the tap breaks. */
fun interface ReaderLink {
    suspend fun exchange(request: ByteArray): ByteArray
}

/** The screen on the reader phone. */
interface ReaderHost {
    /** The other phone answered. [tiles] is null when the two phones verified each other before. */
    fun peerFound(name: String, tiles: List<TileFace>?)

    /** This person's answer so far: null while deciding, then "they match" (true) or "they don't" (false). */
    fun myAnswer(): Boolean?

    /** The other person accepted; still waiting on [myAnswer]. */
    fun theyAnswered(answer: Answer) {}
}

sealed interface TapResult {
    data class Shared(val peerName: String, val mode: Answer) : TapResult

    data class Synced(val peerName: String, val lists: List<ListResult>) : TapResult

    data class Refused(val peerName: String, val reason: Refusal) : TapResult

    /** Someone said the tiles don't match, or "not now". */
    data class Declined(val byMe: Boolean) : TapResult

    /** The phones moved apart before the end. Nothing was applied on this phone. */
    data object Lost : TapResult

    /** Ran out of time waiting for both people. */
    data object TimedOut : TapResult

    /** Something was wrong with the other phone's messages (a forged or foreign phone). */
    data class Failed(val reason: String) : TapResult
}

private fun commitment(dhPublic: ByteArray, signPublic: ByteArray, nonce: ByteArray) =
    Crypto.sha256("due-north commit v1".toByteArray(), dhPublic, signPublic, nonce)

/**
 * The phone that started the tap (the NFC reader). [run] takes it from the first message to the
 * end, asking [host] when it needs the person (FR-802 to FR-806).
 */
class ReaderSession(
    private val store: ShareStore,
    private val me: Identity,
    private val myName: String,
    private val host: ReaderHost,
    private val now: () -> Long = System::currentTimeMillis,
    private val pollMs: Long = 150,
    private val waitMs: Long = 60_000
) {
    private val engine = ShareEngine(store, me, now)

    suspend fun share(link: ReaderLink, listId: String): TapResult = run(link, IntentKind.SHARE, listId)

    suspend fun sync(link: ReaderLink): TapResult = run(link, IntentKind.SYNC, null)

    private suspend fun run(link: ReaderLink, kind: IntentKind, listId: String?): TapResult = try {
        session(link, kind, listId)
    } catch (e: IOException) {
        TapResult.Lost
    } catch (e: SecurityException) {
        TapResult.Failed(e.message ?: "bad message")
    }

    private suspend fun session(link: ReaderLink, kind: IntentKind, listId: String?): TapResult {
        val noise = NoiseXX(true, me)
        val nonce = Crypto.randomBytes(32)
        val m2 = link.exchange(
            noise.writeMessage(Wire.encode(Hello1(b64(commitment(me.dhPublic, me.signPublic, nonce)))))
        )
        val h2 = Wire.decode(noise.readMessage(m2)) as? Hello2 ?: throw SecurityException("expected hello")
        val remoteDh = noise.remoteStatic!!
        val peerSign = unb64(h2.sign)
        if (!Identity.verifyBinding(peerSign, remoteDh, unb64(h2.bind))) throw SecurityException("identity not bound")
        val peerId = peerSign.hex()
        val known = store.peer(peerId)?.takeIf { it.dhPublic == remoteDh.hex() }

        val preview = listId?.let { id ->
            val rows = store.read(id) ?: throw IllegalArgumentException("no list $id")
            val names = engine.roster(id).let { r -> r.members.mapNotNull { r.name(it) } }.ifEmpty { listOf(myName) }
            InvitePreview(id, rows.title, rows.tasks.size, names)
        }
        val m3 = noise.writeMessage(
            Wire.encode(
                Hello3(b64(me.signPublic), b64(me.binding()), myName, b64(nonce), known != null, Intent(kind, preview))
            )
        )
        val reply = link.exchange(m3)
        val (send, recv) = noise.split()
        suspend fun ask(msg: TapMsg): TapMsg = Wire.decode(recv.decrypt(link.exchange(send.encrypt(Wire.encode(msg)))))

        val ready = Wire.decode(recv.decrypt(reply)) as? Ready ?: throw SecurityException("expected ready")
        ready.refuse?.let { return TapResult.Refused(h2.name, it) }
        val mutual = known != null && ready.knowsYou

        return when (kind) {
            IntentKind.SHARE -> share(
                listId!!,
                peerId,
                h2.name,
                remoteDh,
                mutual,
                SafetyTiles.of(noise.handshakeHash),
                ::ask
            )
            IntentKind.SYNC -> {
                if (!mutual) return TapResult.Refused(h2.name, Refusal.NOT_LINKED)
                sync(peerId, h2.name, ::ask)
            }
        }
    }

    private suspend fun share(
        listId: String,
        peerId: String,
        peerName: String,
        remoteDh: ByteArray,
        mutual: Boolean,
        tiles: List<TileFace>,
        ask: suspend (TapMsg) -> TapMsg
    ): TapResult {
        host.peerFound(peerName, if (mutual) null else tiles)
        val deadline = now() + waitMs
        var theirs: Answer?
        while (true) {
            val mine = host.myAnswer()
            theirs = (ask(Poll(mine)) as? Status ?: throw SecurityException("expected status")).answer
            if (mine == false) return TapResult.Declined(byMe = true)
            if (theirs == Answer.NO) return TapResult.Declined(byMe = false)
            if (theirs != null) host.theyAnswered(theirs)
            if (mine == true && theirs != null) break
            if (now() > deadline) return TapResult.TimedOut
            delay(pollMs)
        }

        val give: Give
        var entry: RosterEntry? = null
        if (theirs == Answer.SYNC) {
            val bundle = store.transaction {
                engine.start(listId, myName)
                engine.capture(listId)
                val roster = engine.roster(listId)
                check(peerId in roster.members || roster.members.size < Roster.MAX_MEMBERS) { "the list is full" }
                entry =
                    RosterEntry(listId, peerId, peerName, RosterKind.ADD, me.id, engine.nextClock(listId)).signedBy(me)
                val all = engine.everything(listId)
                all.copy(roster = all.roster + entry!!)
            }
            give = Give(Answer.SYNC, listId, bundle = bundle)
        } else {
            give = Give(Answer.COPY, listId, copy = store.read(listId))
        }
        val done = ask(give) as? Done ?: throw SecurityException("expected done")
        if (!done.ok) return TapResult.Failed("the other phone couldn't add the list")
        store.transaction {
            entry?.let(store::addRoster)
            val old = store.peer(peerId)
            store.savePeer(
                Peer(peerId, remoteDh.hex(), peerName, verifiedAt = old?.verifiedAt ?: now(), lastTapAt = now())
            )
        }
        return TapResult.Shared(peerName, theirs!!)
    }

    private suspend fun sync(peerId: String, peerName: String, ask: suspend (TapMsg) -> TapMsg): TapResult {
        val lists = store.transaction {
            engine.listsWith(peerId).onEach { engine.capture(it) }
        }
        val summaries = store.transaction { lists.associateWith { engine.summary(it) } }
        val resp = ask(SyncRequest(summaries)) as? SyncResponse ?: throw SecurityException("expected sync reply")
        val push = store.transaction {
            resp.summaries.filterKeys { it in lists }.mapValues { (id, theirs) -> engine.missing(id, theirs) }
        }
        ask(SyncPush(push.filterValues { !it.isEmpty })) as? SyncDone ?: throw SecurityException("expected sync done")
        val results = store.transaction {
            val out = lists.map { id ->
                val b = resp.bundles[id]
                val r = if (b == null ||
                    b.isEmpty
                ) {
                    ListResult(id, store.read(id)?.title.orEmpty())
                } else {
                    engine.receive(id, b)
                }
                r.copy(sent = push[id]?.records?.size ?: 0)
            }
            store.peer(peerId)?.let { store.savePeer(it.copy(name = peerName, lastTapAt = now())) }
            out
        }
        return TapResult.Synced(peerName, results)
    }
}

/** The screen on the card phone (the one that was tapped). */
interface CardHost {
    /** Someone wants to share a list. [tiles] is null when the phones verified each other before. */
    fun invite(fromName: String, preview: InvitePreview, tiles: List<TileFace>?)

    /** This person's answer so far. */
    fun answer(): Answer?

    /** The sharer's own answer so far (null while deciding). */
    fun sharerAnswered(yes: Boolean?) {}

    fun finished(outcome: CardOutcome)
}

sealed interface CardOutcome {
    data class Added(val fromName: String, val listId: String, val title: String, val mode: Answer) : CardOutcome

    data class Synced(val fromName: String, val lists: List<ListResult>) : CardOutcome

    data class Refused(val fromName: String, val reason: Refusal) : CardOutcome

    data object Cancelled : CardOutcome
}

/**
 * The tapped phone's side (Host Card Emulation). The NFC service makes one per tap and feeds it
 * each whole message through [handle]; every reply goes back the same way. It never starts
 * anything: it answers.
 */
class CardSession(
    private val store: ShareStore,
    private val me: Identity,
    private val myName: () -> String,
    private val host: CardHost,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val engine = ShareEngine(store, me, now)
    private val noise = NoiseXX(false, me)
    private var commit: ByteArray? = null
    private var send: CipherState? = null
    private var recv: CipherState? = null
    private var peerId = ""
    private var peerName = ""
    private var peerDh = ""
    private var preview: InvitePreview? = null
    private var pendingLists: List<String> = emptyList()
    private var pendingReply: Map<String, Bundle> = emptyMap()
    private var done = false

    /** Throws [SecurityException] when the reader's messages don't check out. */
    fun handle(input: ByteArray): ByteArray {
        check(!done) { "session over" }
        if (!noise.isComplete) return handshake(input)
        val msg = Wire.decode(recv!!.decrypt(input))
        return send!!.encrypt(Wire.encode(reply(msg)))
    }

    private fun handshake(input: ByteArray): ByteArray {
        if (commit == null) {
            val h1 = Wire.decode(noise.readMessage(input)) as? Hello1 ?: throw SecurityException("expected hello")
            commit = unb64(h1.commit)
            return noise.writeMessage(
                Wire.encode(Hello2(b64(me.signPublic), b64(me.binding()), myName(), store.isLocalOnly()))
            )
        }
        val h3 = Wire.decode(noise.readMessage(input)) as? Hello3 ?: throw SecurityException("expected hello")
        val remoteDh = noise.remoteStatic!!
        val sign = unb64(h3.sign)
        if (!Identity.verifyBinding(sign, remoteDh, unb64(h3.bind))) throw SecurityException("identity not bound")
        if (!commitment(
                remoteDh,
                sign,
                unb64(h3.nonce)
            ).contentEquals(commit)
        ) {
            throw SecurityException("commitment broken")
        }
        peerId = sign.hex()
        peerName = h3.name
        peerDh = remoteDh.hex()
        val (s, r) = noise.split()
        send = s
        recv = r
        val known = store.peer(peerId)?.takeIf { it.dhPublic == peerDh } != null
        val mutual = known && h3.knowsYou
        val ready = when {
            !store.isLocalOnly() -> Ready(known, Refusal.NOT_LOCAL_ONLY)
            h3.intent.kind == IntentKind.SYNC && !mutual -> Ready(known, Refusal.NOT_LINKED)
            h3.intent.kind == IntentKind.SHARE && h3.intent.preview == null -> throw SecurityException("no preview")
            else -> Ready(known)
        }
        ready.refuse?.let {
            done = true
            host.finished(CardOutcome.Refused(peerName, it))
        }
        if (ready.refuse == null && h3.intent.kind == IntentKind.SHARE) {
            preview = h3.intent.preview
            host.invite(peerName, preview!!, if (mutual) null else SafetyTiles.of(noise.handshakeHash))
        }
        return send!!.encrypt(Wire.encode(ready))
    }

    private fun reply(msg: TapMsg): TapMsg = when (msg) {
        is Poll -> {
            host.sharerAnswered(msg.sharer)
            if (msg.sharer == false) {
                done = true
                host.finished(CardOutcome.Cancelled)
            }
            Status(host.answer())
        }
        is Give -> give(msg)
        is SyncRequest -> syncRequest(msg)
        is SyncPush -> syncPush(msg)
        else -> throw SecurityException("unexpected ${msg::class.simpleName}")
    }

    private fun give(msg: Give): TapMsg {
        val p = preview ?: throw SecurityException("no invite")
        if (msg.listId != p.listId || host.answer() != msg.mode) throw SecurityException("not what was agreed")
        val (listId, title) = store.transaction {
            val added = if (msg.mode == Answer.SYNC) {
                val bundle = msg.bundle ?: throw SecurityException("no list")
                val roster = Roster(store.roster(msg.listId) + bundle.roster)
                if (me.id !in roster.members) throw SecurityException("not added")
                store.saveSharedList(SharedList(msg.listId, owner = roster.owner ?: peerId, linkedAt = now()))
                msg.listId to engine.receive(msg.listId, bundle).title
            } else {
                val copy = msg.copy ?: throw SecurityException("no list")
                store.createCopy(copy) to copy.title
            }
            val old = store.peer(peerId)
            store.savePeer(Peer(peerId, peerDh, peerName, verifiedAt = old?.verifiedAt ?: now(), lastTapAt = now()))
            added
        }
        done = true
        host.finished(CardOutcome.Added(peerName, listId, title, msg.mode))
        return Done(true)
    }

    private fun syncRequest(msg: SyncRequest): TapMsg {
        val summaries = mutableMapOf<String, Map<String, Hlc>>()
        val bundles = mutableMapOf<String, Bundle>()
        store.transaction {
            for ((id, theirs) in msg.summaries) {
                val shared = store.sharedList(id) ?: continue
                val roster = engine.roster(id)
                if (peerId !in roster.everMembers) continue
                if (!shared.left && peerId in roster.members) {
                    engine.capture(id)
                    summaries[id] = engine.summary(id)
                    bundles[id] = engine.missing(id, theirs)
                } else {
                    // They were removed, or this phone left: only tell them who's in.
                    bundles[id] = Bundle(roster = engine.missing(id, theirs).roster)
                }
            }
        }
        pendingLists = summaries.keys.toList()
        pendingReply = bundles
        return SyncResponse(summaries, bundles.filterValues { !it.isEmpty })
    }

    private fun syncPush(msg: SyncPush): TapMsg {
        var count = 0
        val results = store.transaction {
            val out = pendingLists.map { id ->
                val b = msg.bundles[id]
                count += b?.records?.size ?: 0
                val r = if (b == null ||
                    b.isEmpty
                ) {
                    ListResult(id, store.read(id)?.title.orEmpty())
                } else {
                    engine.receive(id, b)
                }
                r.copy(sent = pendingReply[id]?.records?.size ?: 0)
            }
            store.peer(peerId)?.let { store.savePeer(it.copy(name = peerName, lastTapAt = now())) }
            out
        }
        done = true
        host.finished(CardOutcome.Synced(peerName, results))
        return SyncDone(count)
    }
}
