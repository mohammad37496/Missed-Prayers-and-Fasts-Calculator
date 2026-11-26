package ir.hirmand.missedprayers.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupCodecTest {
    @Test fun encryptedBackupRoundTrips() {
        val original = "sensitive profile and worship data".toByteArray()
        val encrypted = EncryptedBackupCodec.encrypt(original, "a-long-backup-password".toCharArray())
        assertArrayEquals(original, EncryptedBackupCodec.decrypt(encrypted, "a-long-backup-password".toCharArray()))
    }

    @Test fun wrongPasswordIsRejected() {
        val encrypted = EncryptedBackupCodec.encrypt(byteArrayOf(1, 2, 3), "correct-password".toCharArray())
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.decrypt(encrypted, "incorrect-password".toCharArray()) }
    }

    @Test fun modifiedCiphertextIsRejected() {
        val encrypted = EncryptedBackupCodec.encrypt(byteArrayOf(1, 2, 3), "correct-password".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.decrypt(encrypted, "correct-password".toCharArray()) }
    }

    @Test fun malformedAndTruncatedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.decrypt(ByteArray(4), "correct-password".toCharArray()) }
        val encrypted = EncryptedBackupCodec.encrypt(byteArrayOf(1), "correct-password".toCharArray())
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.decrypt(encrypted.copyOf(20), "correct-password".toCharArray()) }
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.encrypt(byteArrayOf(1), "short".toCharArray()) }
    }
}
