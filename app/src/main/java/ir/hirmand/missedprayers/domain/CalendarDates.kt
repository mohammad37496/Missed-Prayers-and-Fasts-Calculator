package ir.hirmand.missedprayers.domain

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

data class CalendarDate(val year: Int, val month: Int, val day: Int) {
    fun iso(): String = "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
}

/** Calendar conversion is date-only and anchored to UTC to avoid DST midnight drift. */
object CalendarDates {
    private val utc = TimeZone.getTimeZone("UTC")

    fun toPersian(date: LocalDate): CalendarDate = fromIcu(Calendar.getInstance(utc, ULocale("fa_IR@calendar=persian")), date)

    fun fromPersian(date: CalendarDate): LocalDate = toGregorian(Calendar.getInstance(utc, ULocale("fa_IR@calendar=persian")), date)

    fun toIslamicCivil(date: LocalDate): CalendarDate = fromIcu(Calendar.getInstance(utc, ULocale("en@calendar=islamic-civil")), date)

    fun fromIslamicCivil(date: CalendarDate): LocalDate = toGregorian(Calendar.getInstance(utc, ULocale("en@calendar=islamic-civil")), date)

    fun toPreferred(date: LocalDate, preference: String): CalendarDate = when (preference) {
        "PERSIAN" -> toPersian(date)
        "ISLAMIC_CIVIL" -> toIslamicCivil(date)
        "GREGORIAN" -> CalendarDate(date.year, date.monthValue, date.dayOfMonth)
        else -> throw IllegalArgumentException("تقویم پیش‌فرض پشتیبانی نمی‌شود")
    }

    fun fromPreferred(date: CalendarDate, preference: String): LocalDate = when (preference) {
        "PERSIAN" -> fromPersian(date)
        "ISLAMIC_CIVIL" -> fromIslamicCivil(date)
        "GREGORIAN" -> LocalDate.of(date.year, date.month, date.day)
        else -> throw IllegalArgumentException("تقویم پیش‌فرض پشتیبانی نمی‌شود")
    }

    /** Accepts Latin, Persian and Arabic-Indic digits in the date fields used by the Persian UI. */
    fun parsePreferredInput(value: String, preference: String): LocalDate {
        val normalized = buildString(value.length) {
            value.trim().forEach { character ->
                val digit = character.digitToIntOrNull()
                append(if (digit == null) character else ('0'.code + digit).toChar())
            }
        }
        val parts = normalized.split(Regex("[-/]"))
        require(parts.size == 3) { "تاریخ باید با قالب سال-ماه-روز وارد شود" }
        val date = CalendarDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        return fromPreferred(date, preference)
    }

    fun today(zoneId: String = ZoneId.systemDefault().id): LocalDate = Instant.now().atZone(ZoneId.of(zoneId)).toLocalDate()

    fun display(date: LocalDate, locale: Locale = Locale.forLanguageTag("fa-IR")): String {
        val p = toPersian(date)
        return "%04d/%02d/%02d".format(locale, p.year, p.month, p.day)
    }

    fun displayPreferred(
        date: LocalDate,
        preference: String,
        locale: Locale = Locale.forLanguageTag("fa-IR")
    ): String {
        val preferred = toPreferred(date, preference)
        return "%04d/%02d/%02d".format(locale, preferred.year, preferred.month, preferred.day)
    }

    private fun fromIcu(calendar: Calendar, date: LocalDate): CalendarDate {
        calendar.timeInMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DATE))
    }

    private fun toGregorian(calendar: Calendar, date: CalendarDate): LocalDate {
        require(date.month in 1..12 && date.day in 1..31) { "تاریخ واردشده معتبر نیست" }
        calendar.clear()
        calendar.set(Calendar.YEAR, date.year)
        calendar.set(Calendar.MONTH, date.month - 1)
        calendar.set(Calendar.DATE, date.day)
        val result = Instant.ofEpochMilli(calendar.timeInMillis).atZone(ZoneOffset.UTC).toLocalDate()
        require(fromIcu(calendar, result) == date) { "روز واردشده در این ماه وجود ندارد" }
        return result
    }
}
