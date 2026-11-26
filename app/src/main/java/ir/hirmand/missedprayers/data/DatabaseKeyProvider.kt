package ir.hirmand.missedprayers.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects the SQLCipher passphrase with a non-exportable Android Keystore key. */
internal class DatabaseKeyProvider(context: Context) {
    private val preferences = context.getSharedPreferences("database_key_v1", Context.MODE_PRIVATE)

    @Suppress("UseKtx")
    fun getOrCreatePassphrase(): ByteArray {
        val key = getOrCreateKeystoreKey()
        val saved = preferences.getString(ENCRYPTED_KEY, null)
        if (saved != null) {
            val packed = Base64.decode(saved, Base64.NO_WRAP)
            require(packed.size > GCM_IV_LENGTH) { "کلید رمزنگاری ذخیره‌شده معتبر نیست" }
            val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
            return Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(ciphertext)
            }
        }

        val passphrase = ByteArray(PASSPHRASE_LENGTH).also(java.security.SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(passphrase)
        check(preferences.edit().putString(ENCRYPTED_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "ذخیره امن کلید دیتابیس انجام نشد"
        }
        return passphrase
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "missed_worship_database_key_v1"
        const val ENCRYPTED_KEY = "encrypted_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_LENGTH = 32
    }
}
