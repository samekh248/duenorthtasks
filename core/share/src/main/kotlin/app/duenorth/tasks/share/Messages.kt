package app.duenorth.tasks.share

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings and compression for everything that crosses a tap. */
object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        classDiscriminator = "t"
    }

    internal fun encode(msg: TapMsg): ByteArray = deflate(json.encodeToString(TapMsg.serializer(), msg).toByteArray())

    internal fun decode(bytes: ByteArray): TapMsg = try {
        json.decodeFromString(TapMsg.serializer(), String(inflate(bytes)))
    } catch (e: Exception) {
        throw SecurityException("unreadable message", e)
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(bytes)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private const val MAX_MESSAGE = 4 * 1024 * 1024

    private fun inflate(bytes: ByteArray): ByteArray {
        val i = Inflater(true)
        i.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
            check(out.size() <= MAX_MESSAGE) { "message too large" }
        }
        i.end()
        return out.toByteArray()
    }
}

/** What the person holding the reader wants from the tap. */
enum class IntentKind { SHARE, SYNC }

/** What the other phone sees before deciding: no tasks, only these facts (spec US1). */
@Serializable
data class InvitePreview(val listId: String, val title: String, val tasks: Int, val members: List<String>)

@Serializable
data class Intent(val kind: IntentKind, val preview: InvitePreview? = null)

/** Why a card phone said no straight away. */
enum class Refusal { NOT_LOCAL_ONLY, NOT_LINKED }

/** The card phone's answer to an invite. */
enum class Answer { SYNC, COPY, NO }

@Serializable
sealed interface TapMsg

/** Message 1 payload: a commitment to the reader's identity and a nonce, revealed in message 3. */
@Serializable
@SerialName("h1")
data class Hello1(val commit: String) : TapMsg

@Serializable
@SerialName("h2")
data class Hello2(val sign: String, val bind: String, val name: String, val localOnly: Boolean) : TapMsg

@Serializable
@SerialName("h3")
data class Hello3(
    val sign: String,
    val bind: String,
    val name: String,
    val nonce: String,
    val knowsYou: Boolean,
    val intent: Intent
) : TapMsg

@Serializable
@SerialName("ready")
data class Ready(val knowsYou: Boolean, val refuse: Refusal? = null) : TapMsg

/** The reader asks how the invite is going; [sharer] is its own person's answer so far. */
@Serializable
@SerialName("poll")
data class Poll(val sharer: Boolean? = null) : TapMsg

@Serializable
@SerialName("status")
data class Status(val answer: Answer? = null, val name: String? = null) : TapMsg

/** The list itself, sent only after both people confirmed. */
@Serializable
@SerialName("give")
data class Give(val mode: Answer, val listId: String, val bundle: Bundle? = null, val copy: ListState? = null) : TapMsg

@Serializable
@SerialName("done")
data class Done(val ok: Boolean) : TapMsg

@Serializable
@SerialName("sreq")
data class SyncRequest(val summaries: Map<String, Map<String, Hlc>>) : TapMsg

@Serializable
@SerialName("sresp")
data class SyncResponse(val summaries: Map<String, Map<String, Hlc>>, val bundles: Map<String, Bundle>) : TapMsg

@Serializable
@SerialName("spush")
data class SyncPush(val bundles: Map<String, Bundle>) : TapMsg

@Serializable
@SerialName("sdone")
data class SyncDone(val received: Int) : TapMsg
