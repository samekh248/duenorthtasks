package app.duenorth.tasks.share

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Carries whole messages over ISO 7816 APDUs, the only way two Android phones talk over NFC since
 * Android 10 (reader mode on one, Host Card Emulation on the other). Short APDUs only, so it works
 * on every phone: requests go in chained DATA commands, long replies come back with "61 00" and
 * GET RESPONSE.
 */
object Apdu {
    /** Due North's proprietary application id ("F0" + "DNTAPS"). Also in apduservice.xml. */
    val AID = byteArrayOf(0xF0.toByte(), 0x44, 0x4E, 0x54, 0x41, 0x50, 0x53)

    const val CHUNK = 240
    private const val CLA = 0x80.toByte()
    private const val INS_DATA = 0x10.toByte()
    private const val INS_GET = 0xC0.toByte()

    val OK = byteArrayOf(0x90.toByte(), 0x00)
    val MORE = byteArrayOf(0x61, 0x00)
    val UNKNOWN = byteArrayOf(0x6D, 0x00)
    val ERROR = byteArrayOf(0x6F, 0x00)
    val NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())

    fun select(): ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID + byteArrayOf(0x00)

    internal fun data(chunk: ByteArray, more: Boolean): ByteArray =
        byteArrayOf(CLA, INS_DATA, if (more) 1 else 0, 0x00, chunk.size.toByte()) + chunk + byteArrayOf(0x00)

    internal fun get(): ByteArray = byteArrayOf(CLA, INS_GET, 0x00, 0x00, 0x00)

    internal fun isSelect(apdu: ByteArray): Boolean =
        apdu.size >= 5 + AID.size && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() && apdu[2] == 0x04.toByte() &&
            apdu.copyOfRange(5, 5 + AID.size).contentEquals(AID)

    internal fun isData(apdu: ByteArray) = apdu.size >= 5 && apdu[0] == CLA && apdu[1] == INS_DATA

    internal fun isGet(apdu: ByteArray) = apdu.size >= 4 && apdu[0] == CLA && apdu[1] == INS_GET
}

/** Reader side: wraps a raw `transceive` (IsoDep) into a [ReaderLink]. */
class ApduReader(private val transceive: suspend (ByteArray) -> ByteArray) : ReaderLink {
    /** Picks Due North on the other phone. False when it isn't there (not installed, or locked). */
    suspend fun select(): Boolean = status(transceive(Apdu.select())).contentEquals(Apdu.OK)

    override suspend fun exchange(request: ByteArray): ByteArray {
        val chunks = request.toList().chunked(Apdu.CHUNK).map { it.toByteArray() }.ifEmpty { listOf(ByteArray(0)) }
        var resp = ByteArray(0)
        chunks.forEachIndexed { i, c ->
            val last = i == chunks.lastIndex
            resp = transceive(Apdu.data(c, more = !last))
            if (!last && !status(resp).contentEquals(Apdu.OK)) throw IOException("card refused a chunk")
        }
        val out = ByteArrayOutputStream()
        while (true) {
            val sw = status(resp)
            out.write(resp, 0, resp.size - 2)
            when {
                sw.contentEquals(Apdu.OK) -> return out.toByteArray()
                sw.contentEquals(Apdu.MORE) -> resp = transceive(Apdu.get())
                else -> throw IOException("card error ${sw.hex()}")
            }
        }
    }

    private fun status(resp: ByteArray): ByteArray {
        if (resp.size < 2) throw IOException("short response")
        return resp.copyOfRange(resp.size - 2, resp.size)
    }
}

/**
 * Card side: turns APDUs into whole messages for [onSelect]'s handler and splits replies. Blocking;
 * the NFC service calls [process] off the main thread.
 */
class ApduCard(private val onSelect: () -> (ByteArray) -> ByteArray) {
    private var handler: ((ByteArray) -> ByteArray)? = null
    private val incoming = ByteArrayOutputStream()
    private var outgoing = ArrayDeque<ByteArray>()

    fun process(apdu: ByteArray): ByteArray = try {
        when {
            Apdu.isSelect(apdu) -> {
                handler = onSelect()
                incoming.reset()
                outgoing.clear()
                Apdu.OK
            }
            handler == null -> Apdu.UNKNOWN
            Apdu.isData(apdu) -> data(apdu)
            Apdu.isGet(apdu) -> next()
            else -> Apdu.UNKNOWN
        }
    } catch (e: Exception) {
        handler = null
        Apdu.ERROR
    }

    /** The reader moved away. */
    fun deactivated() {
        handler = null
        incoming.reset()
        outgoing.clear()
    }

    private fun data(apdu: ByteArray): ByteArray {
        val lc = apdu[4].toInt() and 0xFF
        require(apdu.size >= 5 + lc) { "short apdu" }
        incoming.write(apdu, 5, lc)
        if (apdu[2].toInt() == 1) return Apdu.OK
        val msg = incoming.toByteArray()
        incoming.reset()
        val reply = handler!!(msg)
        outgoing =
            ArrayDeque(reply.toList().chunked(Apdu.CHUNK).map { it.toByteArray() }.ifEmpty { listOf(ByteArray(0)) })
        return next()
    }

    private fun next(): ByteArray {
        val chunk = outgoing.removeFirstOrNull() ?: return Apdu.ERROR
        return chunk + if (outgoing.isEmpty()) Apdu.OK else Apdu.MORE
    }
}
