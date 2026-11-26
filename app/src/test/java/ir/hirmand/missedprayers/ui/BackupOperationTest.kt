package ir.hirmand.missedprayers.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupOperationTest {
    @Test
    fun successfulOperationClearsPassword() = runBlocking {
        val password = "one-shot-secret".toCharArray()

        val result = runBackupOperation(password) { "backup" }

        assertEquals("backup", result.getOrThrow())
        assertArrayEquals(CharArray(password.size), password)
    }

    @Test
    fun failedOperationReturnsFailureAndClearsPassword() = runBlocking {
        val password = "one-shot-secret".toCharArray()

        val result = runBackupOperation<String>(password) { error("backup failed") }

        assertEquals("backup failed", result.exceptionOrNull()?.message)
        assertArrayEquals(CharArray(password.size), password)
    }

    @Test
    fun cancellationPropagatesAndStillClearsPassword() {
        val password = "one-shot-secret".toCharArray()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runBackupOperation(password) { throw CancellationException("cancelled") }
            }
        }

        assertArrayEquals(CharArray(password.size), password)
    }
}
