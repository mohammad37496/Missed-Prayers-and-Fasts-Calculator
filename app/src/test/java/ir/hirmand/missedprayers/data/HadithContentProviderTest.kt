package ir.hirmand.missedprayers.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HadithContentProviderTest {
    @Test fun unconfiguredProviderNeverInventsDailyHadith() = runBlocking {
        val result = UnconfiguredHadithContentProvider.loadFor(LocalDate.parse("2026-11-26"))
        assertTrue(result is DailyHadithResult.Unavailable)
    }
}
