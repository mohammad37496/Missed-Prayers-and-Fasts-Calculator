package ir.hirmand.missedprayers.data

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupCodec {
    private val magic = byteArrayOf(0x4D, 0x57, 0x42, 0x4B, 0x01)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 310_000
    const val MAX_FILE_BYTES = 12 * 1024 * 1024
    private const val MAX_PLAIN_BYTES = MAX_FILE_BYTES - 64

    fun encrypt(plain: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(password.size >= 12) { "گذرواژه نسخه پشتیبان باید دست‌کم ۱۲ نویسه باشد" }
        require(plain.size <= MAX_PLAIN_BYTES) { "حجم اطلاعات برای نسخه پشتیبان بیش از حد مجاز است" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = magic + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(magic)
        val encrypted = cipher.doFinal(plain)
        return header + encrypted
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        require(file.size in (magic.size + SALT_BYTES + IV_BYTES + 16)..MAX_FILE_BYTES) { "فایل نسخه پشتیبان ناقص یا بیش از حد بزرگ است" }
        require(file.copyOfRange(0, magic.size).contentEquals(magic)) { "قالب یا نسخه فایل پشتیبان پشتیبانی نمی‌شود" }
        val saltStart = magic.size
        val ivStart = saltStart + SALT_BYTES
        val ciphertextStart = ivStart + IV_BYTES
        val salt = file.copyOfRange(saltStart, ivStart)
        val iv = file.copyOfRange(ivStart, ciphertextStart)
        val ciphertext = file.copyOfRange(ciphertextStart, file.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(magic)
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("گذرواژه نادرست است یا فایل پشتیبان دست‌کاری شده است")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 12) { "گذرواژه نسخه پشتیبان باید دست‌کم ۱۲ نویسه باشد" }
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val encodedKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(encodedKey, "AES")
            } finally {
                encodedKey.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
