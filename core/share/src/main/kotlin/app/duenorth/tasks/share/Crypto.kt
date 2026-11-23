package app.duenorth.tasks.share

import java.security.SecureRandom
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519
import org.bouncycastle.math.ec.rfc8032.Ed25519

/** The primitives share by tap uses (spec 011 FR-801, FR-802), all from BouncyCastle's pure-Java code. */
internal object Crypto {
    const val KEY = 32
    const val TAG = 16

    val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val d = SHA256Digest()
        parts.forEach { d.update(it, 0, it.size) }
        return ByteArray(32).also { d.doFinal(it, 0) }
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        parts.forEach { mac.update(it, 0, it.size) }
        return ByteArray(32).also { mac.doFinal(it, 0) }
    }

    /** Noise's HKDF with two outputs. */
    fun hkdf2(chainingKey: ByteArray, input: ByteArray): Pair<ByteArray, ByteArray> {
        val temp = hmac(chainingKey, input)
        val out1 = hmac(temp, byteArrayOf(1))
        val out2 = hmac(temp, out1, byteArrayOf(2))
        return out1 to out2
    }

    fun x25519Private(): ByteArray = ByteArray(X25519.SCALAR_SIZE).also { X25519.generatePrivateKey(random, it) }

    fun x25519Public(private: ByteArray): ByteArray =
        ByteArray(X25519.POINT_SIZE).also { X25519.generatePublicKey(private, 0, it, 0) }

    fun x25519(private: ByteArray, public: ByteArray): ByteArray {
        val out = ByteArray(X25519.POINT_SIZE)
        // calculateAgreement refuses low-order points (all-zero output), which Noise allows us to reject.
        check(X25519.calculateAgreement(private, 0, public, 0, out, 0)) { "bad public key" }
        return out
    }

    fun ed25519Private(): ByteArray = ByteArray(Ed25519.SECRET_KEY_SIZE).also { Ed25519.generatePrivateKey(random, it) }

    fun ed25519Public(private: ByteArray): ByteArray =
        ByteArray(Ed25519.PUBLIC_KEY_SIZE).also { Ed25519.generatePublicKey(private, 0, it, 0) }

    fun sign(private: ByteArray, message: ByteArray): ByteArray =
        ByteArray(Ed25519.SIGNATURE_SIZE).also { Ed25519.sign(private, 0, message, 0, message.size, it, 0) }

    fun verify(public: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        signature.size == Ed25519.SIGNATURE_SIZE && public.size == Ed25519.PUBLIC_KEY_SIZE &&
            runCatching { Ed25519.verify(signature, 0, public, 0, message, 0, message.size) }.getOrDefault(false)

    /** ChaCha20-Poly1305 with Noise's nonce layout: four zero bytes, then the counter little-endian. */
    fun seal(key: ByteArray, nonce: Long, ad: ByteArray, plaintext: ByteArray): ByteArray =
        aead(true, key, nonce, ad, plaintext)

    /** Throws [SecurityException] when the ciphertext or its associated data was changed. */
    fun open(key: ByteArray, nonce: Long, ad: ByteArray, ciphertext: ByteArray): ByteArray = try {
        aead(false, key, nonce, ad, ciphertext)
    } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
        throw SecurityException("message was altered", e)
    }

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: Long, ad: ByteArray, input: ByteArray): ByteArray {
        val n = ByteArray(12)
        for (i in 0 until 8) n[4 + i] = (nonce ushr (8 * i)).toByte()
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG * 8, n, ad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        val len = cipher.processBytes(input, 0, input.size, out, 0)
        val total = len + cipher.doFinal(out, len)
        return if (total == out.size) out else out.copyOf(total)
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

internal fun String.unhex(): ByteArray {
    require(length % 2 == 0) { "odd hex" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * One phone's long-term identity (FR-801): an Ed25519 key that signs changes and roster entries,
 * and an X25519 key used as the Noise static key. [id] (the signing key, hex) names the phone.
 */
class Identity(internal val signPrivate: ByteArray, internal val dhPrivate: ByteArray) {
    val signPublic: ByteArray = Crypto.ed25519Public(signPrivate)
    val dhPublic: ByteArray = Crypto.x25519Public(dhPrivate)
    val id: String = signPublic.hex()

    fun sign(message: ByteArray): ByteArray = Crypto.sign(signPrivate, message)

    /** Proves that [dhPublic] belongs to [signPublic]; sent inside the handshake. */
    fun binding(): ByteArray = sign(BIND + dhPublic)

    /** 64 bytes: the two private keys. The caller keeps them encrypted at rest (Android Keystore). */
    fun encode(): ByteArray = signPrivate + dhPrivate

    companion object {
        private val BIND = "due-north bind v1".toByteArray()

        fun generate(): Identity = Identity(Crypto.ed25519Private(), Crypto.x25519Private())

        fun decode(bytes: ByteArray): Identity {
            require(bytes.size == 64) { "identity must be 64 bytes" }
            return Identity(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 64))
        }

        fun verifyBinding(signPublic: ByteArray, dhPublic: ByteArray, binding: ByteArray): Boolean =
            Crypto.verify(signPublic, BIND + dhPublic, binding)

        fun verify(id: String, message: ByteArray, signature: ByteArray): Boolean =
            runCatching { Crypto.verify(id.unhex(), message, signature) }.getOrDefault(false)
    }
}
