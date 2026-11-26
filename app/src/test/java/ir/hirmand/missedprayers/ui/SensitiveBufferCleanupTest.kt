package ir.hirmand.missedprayers.ui

import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SensitiveBufferCleanupTest {
    @Test fun passwordIsWipedWhenJobIsCancelledBeforeWorkStarts() {
        val password = "one-shot-secret".toCharArray()
        val job = Job()

        job.wipeOnCompletion(password)
        job.cancel()

        assertArrayEquals(CharArray(password.size), password)
    }

    @Test fun backupBytesAreWipedWhenJobIsCancelledBeforeWorkStarts() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val job = Job()

        job.wipeOnCompletion(bytes)
        job.cancel()

        assertArrayEquals(ByteArray(bytes.size), bytes)
    }
}
