package ir.hirmand.missedprayers.data

import java.time.LocalDate

/** A hadith may be shown only when every provenance field has been verified. */
data class VerifiedHadith(
    val arabicText: String,
    val persianTranslation: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val authenticityStatus: String,
    val licenseReference: String,
    val version: String
)

sealed interface DailyHadithResult {
    data class Available(val hadith: VerifiedHadith) : DailyHadithResult
    data class Unavailable(val reason: String) : DailyHadithResult
}

interface HadithContentProvider {
    suspend fun loadFor(date: LocalDate): DailyHadithResult
}

/** Deliberately returns no content until a Persian source, translation license and API access are approved. */
object UnconfiguredHadithContentProvider : HadithContentProvider {
    override suspend fun loadFor(date: LocalDate): DailyHadithResult = DailyHadithResult.Unavailable(
        "منبع فارسی حدیث با ترجمه معتبر، وضعیت سند قابل بررسی و مجوز بازنشر هنوز پیکربندی نشده است."
    )
}
