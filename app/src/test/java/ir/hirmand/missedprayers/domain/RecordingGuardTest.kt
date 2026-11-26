package ir.hirmand.missedprayers.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingGuardTest {
    @Test fun sameActionCannotBeSubmittedConcurrentlyOrByRapidDoubleTap() {
        val guard = RecordingGuard(debounceMillis = 600L)

        assertTrue(guard.begin("prayer:FAJR", 1_000L))
        assertFalse(guard.begin("prayer:FAJR", 1_001L))
        guard.finish("prayer:FAJR", successful = true)
        assertFalse(guard.begin("prayer:FAJR", 1_500L))
        assertTrue(guard.begin("prayer:FAJR", 1_601L))
        assertTrue(guard.begin("prayer:ISHA", 1_601L))
    }

    @Test fun failedSubmissionCanBeRetriedImmediately() {
        val guard = RecordingGuard(debounceMillis = 600L)
        assertTrue(guard.begin("fasting", 2_000L))
        guard.finish("fasting", successful = false)
        assertTrue(guard.begin("fasting", 2_001L))
    }
}
