package app.duenorth.tasks.share

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's share-by-tap identity (spec 011 FR-801). The two private keys are kept in a file
 * outside backups, encrypted with an AES key that lives in the Android Keystore and never leaves
 * it. A new phone (or a reinstall) gets a new identity and taps in again.
 */
@Singleton
class ShareIdentity @Inject constructor(@ApplicationContext private val context: Context) {
    private val file = File(context.noBackupFilesDir, "share_identity.bin")
    private val prefs = context.getSharedPreferences("share", Context.MODE_PRIVATE)

    @Volatile private var cached: Identity? = null

    fun get(): Identity = cached ?: synchronized(this) {
        cached ?: (load() ?: create()).also { cached = it }
    }

    /** The name other members see. Asked for the first time someone shares or accepts. */
    var name: String?
        get() = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_NAME, value?.trim()?.take(MAX_NAME)).apply()

    /** What to call this phone before its person picks a name. */
    fun nameOrDevice(): String = name ?: Build.MODEL

    private fun load(): Identity? = runCatching {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV))
        Identity.decode(cipher.doFinal(bytes, IV, bytes.size - IV))
    }.getOrNull()

    private fun create(): Identity {
        val identity = Identity.generate()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(identity.encode())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        tmp.renameTo(file)
        return identity
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "due-north-share-identity"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV = 12
        const val KEY_NAME = "name"
    }
}
