package app.duenorth.tasks.share

/**
 * Noise_XX_25519_ChaChaPoly_SHA256 (https://noiseprotocol.org/noise.html, revision 34), the
 * handshake behind every tap (FR-802):
 *
 * ```
 * -> e                 initiator (the reader)
 * <- e, ee, s, es      responder (the card)
 * -> s, se
 * ```
 *
 * Each side then holds two [CipherState]s and the [handshakeHash] the safety tiles come from.
 * Transport messages are not capped at 65535 bytes: APDU chaining delimits them, and nothing else
 * speaks this protocol.
 */
class NoiseXX internal constructor(
    private val initiator: Boolean,
    private val static: Identity,
    prologue: ByteArray,
    private val ephemeralPrivate: ByteArray = Crypto.x25519Private()
) {
    constructor(initiator: Boolean, static: Identity) : this(initiator, static, PROLOGUE)

    private val sym = SymmetricState()
    private val ePublic = Crypto.x25519Public(ephemeralPrivate)
    private var re: ByteArray? = null

    /** The other phone's X25519 static key, known after message 2 (initiator) or 3 (responder). */
    var remoteStatic: ByteArray? = null
        private set
    private var step = 0

    init {
        sym.mixHash(prologue)
    }

    val isComplete: Boolean get() = step == 3

    /** Fixed once the handshake is complete; both phones hold the same value. */
    val handshakeHash: ByteArray get() = sym.h.copyOf()

    fun writeMessage(payload: ByteArray = ByteArray(0)): ByteArray {
        val out = when (step) {
            0 -> {
                require(initiator)
                sym.mixHash(ePublic)
                ePublic + sym.encryptAndHash(payload)
            }
            1 -> {
                require(!initiator)
                sym.mixHash(ePublic)
                sym.mixKey(Crypto.x25519(ephemeralPrivate, re!!))
                val s = sym.encryptAndHash(static.dhPublic)
                sym.mixKey(Crypto.x25519(static.dhPrivate, re!!))
                ePublic + s + sym.encryptAndHash(payload)
            }
            2 -> {
                require(initiator)
                val s = sym.encryptAndHash(static.dhPublic)
                sym.mixKey(Crypto.x25519(static.dhPrivate, re!!))
                s + sym.encryptAndHash(payload)
            }
            else -> error("handshake already complete")
        }
        step++
        return out
    }

    /** Returns the payload. Throws [SecurityException] for an altered or foreign message. */
    fun readMessage(message: ByteArray): ByteArray {
        val payload = try {
            when (step) {
                0 -> {
                    require(!initiator)
                    val e = message.take32(0)
                    re = e
                    sym.mixHash(e)
                    sym.decryptAndHash(message.copyOfRange(32, message.size))
                }
                1 -> {
                    require(initiator)
                    val e = message.take32(0)
                    re = e
                    sym.mixHash(e)
                    sym.mixKey(Crypto.x25519(ephemeralPrivate, e))
                    val rs = sym.decryptAndHash(message.copyOfRange(32, 32 + 48))
                    remoteStatic = rs
                    sym.mixKey(Crypto.x25519(ephemeralPrivate, rs))
                    sym.decryptAndHash(message.copyOfRange(80, message.size))
                }
                2 -> {
                    require(!initiator)
                    val rs = sym.decryptAndHash(message.copyOfRange(0, 48))
                    remoteStatic = rs
                    sym.mixKey(Crypto.x25519(ephemeralPrivate, rs))
                    sym.decryptAndHash(message.copyOfRange(48, message.size))
                }
                else -> error("handshake already complete")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SecurityException("short handshake message", e)
        } catch (e: IllegalStateException) {
            throw SecurityException(e.message, e)
        }
        step++
        return payload
    }

    /** After the handshake: (send, receive) for this side. */
    fun split(): Pair<CipherState, CipherState> {
        check(isComplete) { "handshake not complete" }
        val (k1, k2) = Crypto.hkdf2(sym.ck, ByteArray(0))
        val c1 = CipherState(k1)
        val c2 = CipherState(k2)
        return if (initiator) c1 to c2 else c2 to c1
    }

    private fun ByteArray.take32(at: Int): ByteArray {
        if (size < at + 32) throw SecurityException("short handshake message")
        return copyOfRange(at, at + 32)
    }

    companion object {
        val NAME = "Noise_XX_25519_ChaChaPoly_SHA256".toByteArray()
        val PROLOGUE = "due-north share v1".toByteArray()
    }
}

/** A Noise cipher state: one key and a counter nonce that never repeats. */
class CipherState internal constructor(private val key: ByteArray) {
    private var nonce = 0L

    fun encrypt(plaintext: ByteArray, ad: ByteArray = ByteArray(0)): ByteArray =
        Crypto.seal(key, nonce++, ad, plaintext)

    fun decrypt(ciphertext: ByteArray, ad: ByteArray = ByteArray(0)): ByteArray =
        Crypto.open(key, nonce++, ad, ciphertext)
}

private class SymmetricState {
    var h: ByteArray = if (NoiseXX.NAME.size <= 32) NoiseXX.NAME.copyOf(32) else Crypto.sha256(NoiseXX.NAME)
    var ck: ByteArray = h.copyOf()
    private var k: ByteArray? = null
    private var n = 0L

    fun mixHash(data: ByteArray) {
        h = Crypto.sha256(h, data)
    }

    fun mixKey(input: ByteArray) {
        val (newCk, temp) = Crypto.hkdf2(ck, input)
        ck = newCk
        k = temp
        n = 0
    }

    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val key = k
        val out = if (key == null) plaintext else Crypto.seal(key, n++, h, plaintext)
        mixHash(out)
        return out
    }

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val key = k
        val out = if (key == null) ciphertext else Crypto.open(key, n++, h, ciphertext)
        mixHash(ciphertext)
        return out
    }
}
