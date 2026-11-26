package ir.hirmand.missedprayers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebtCalculatorTest {
    @Test fun remainingAndProgressAreCalculated() {
        val debt = DebtCalculator.fromUserEstimate(total = 12, alreadyCompleted = 3)
        assertEquals(9, debt.remaining)
        assertEquals(0.25f, debt.progress)
    }

    @Test fun recordingCannotExceedTotal() {
        val debt = DebtProgress(total = 4, completed = 3).record(amount = 8)
        assertEquals(4, debt.completed)
        assertEquals(0, debt.remaining)
    }

    @Test fun invalidInitialCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { DebtCalculator.fromUserEstimate(2, 3) }
    }

    @Test fun recordingRequiresPositiveAmount() {
        assertThrows(IllegalArgumentException::class.java) { DebtProgress(total = 3, completed = 1).record(0) }
    }

    @Test fun recordingDoesNotOverflowLargeCounts() {
        val debt = DebtProgress(total = Int.MAX_VALUE, completed = Int.MAX_VALUE - 1).record(10)
        assertEquals(Int.MAX_VALUE, debt.completed)
        assertEquals(0, debt.remaining)
    }
}
