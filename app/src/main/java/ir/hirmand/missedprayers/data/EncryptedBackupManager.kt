package ir.hirmand.missedprayers.data

import ir.hirmand.missedprayers.BuildConfig
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class EncryptedBackupManager(
    private val database: AppDatabase,
    private val includeUnverifiedMedia: Boolean = BuildConfig.DEBUG
) {
    suspend fun createBackup(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val payload = database.withTransaction {
        val dao = database.debtDao()
        val payload = JSONObject().put("schemaVersion", 6).put("createdAt", System.currentTimeMillis())
        payload.put("prayers", JSONArray().also { array -> dao.getAllPrayers().forEach { array.put(JSONObject().put("type", it.prayerType).put("total", it.total).put("completed", it.completed).put("updatedAt", it.updatedAt)) } })
        payload.put("fasting", dao.getFastingSnapshot()?.let { JSONObject().put("total", it.total).put("completed", it.completed).put("updatedAt", it.updatedAt) } ?: JSONObject.NULL)
        payload.put("records", JSONArray().also { array -> dao.getAllRecords().forEach { array.put(JSONObject().put("id", it.id).put("kind", it.kind).put("reference", it.reference).put("amount", it.amount).put("createdAt", it.createdAt).put("beforeCompleted", it.beforeCompleted).put("afterCompleted", it.afterCompleted)) } })
        payload.put("profile", dao.getProfileSnapshot()?.let { JSONObject().put("displayName", it.displayName ?: JSONObject.NULL).put("gender", it.gender).put("taklifStartDate", it.taklifStartDate ?: JSONObject.NULL).put("taklifEndDate", it.taklifEndDate ?: JSONObject.NULL).put("timezoneId", it.timezoneId).put("calendarPreference", it.calendarPreference).put("marjaId", it.marjaId ?: JSONObject.NULL).put("updatedAt", it.updatedAt) } ?: JSONObject.NULL)
        payload.put("periods", JSONArray().also { array -> dao.getAllExcusePeriods().forEach { array.put(JSONObject().put("id", it.id).put("kind", it.kind).put("startDate", it.startDate).put("endDate", it.endDate).put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)) } })
        payload.put("planner", dao.getPlannerSettingsSnapshot()?.let { JSONObject().put("dailyPrayerTarget", it.dailyPrayerTarget).put("dailyFastingTarget", it.dailyFastingTarget).put("activeWeekdaysMask", it.activeWeekdaysMask).put("reminderEnabled", it.reminderEnabled).put("reminderHour", it.reminderHour).put("reminderMinute", it.reminderMinute).put("updatedAt", it.updatedAt) } ?: JSONObject.NULL)
        payload.put("fiqh", dao.getFiqhPreferencesSnapshot()?.let { JSONObject().put("marjaId", it.marjaId ?: JSONObject.NULL).put("rulesetVersion", it.rulesetVersion ?: JSONObject.NULL).put("acceptedSourceUrl", it.acceptedSourceUrl ?: JSONObject.NULL).put("updatedAt", it.updatedAt) } ?: JSONObject.NULL)
        payload.put("dailyVerses", JSONArray().also { array -> dao.getAllDailyVerses().forEach { array.put(JSONObject().put("dayKey", it.dayKey).put("surah", it.surah).put("ayah", it.ayah).put("globalNumber", it.globalNumber).put("arabicText", it.arabicText).put("persianTranslation", it.persianTranslation).put("translationSource", it.translationSource).put("translationVersion", it.translationVersion).put("translationFootnotes", it.translationFootnotes ?: JSONObject.NULL).put("tafsirText", if (includeUnverifiedMedia) it.tafsirText ?: JSONObject.NULL else JSONObject.NULL).put("tafsirSource", if (includeUnverifiedMedia) it.tafsirSource ?: JSONObject.NULL else JSONObject.NULL).put("recitationUrl", if (includeUnverifiedMedia) it.recitationUrl ?: JSONObject.NULL else JSONObject.NULL).put("reciterName", if (includeUnverifiedMedia) it.reciterName ?: JSONObject.NULL else JSONObject.NULL).put("fetchedAt", it.fetchedAt)) } })
        payload
        }
        val plain = payload.toString().toByteArray(Charsets.UTF_8)
        try {
            EncryptedBackupCodec.encrypt(plain, password)
        } finally {
            plain.fill(0)
        }
    }

    suspend fun restoreBackup(file: ByteArray, password: CharArray) = withContext(Dispatchers.IO) {
        require(file.size <= EncryptedBackupCodec.MAX_FILE_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
        val plain = EncryptedBackupCodec.decrypt(file, password)
        try {
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        val backupVersion = root.getInt("schemaVersion")
        require(backupVersion in 4..6) { "نسخه ساختار فایل پشتیبان پشتیبانی نمی‌شود" }
        val prayers = root.getJSONArray("prayers").toEntities { item ->
            val type = item.getString("type")
            require(type in setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA"))
            val total = item.getInt("total"); val completed = item.getInt("completed")
            require(total >= 0 && completed in 0..total)
            PrayerDebtEntity(type, total, completed, item.getLong("updatedAt"))
        }
        require(prayers.map { it.prayerType }.distinct().size == prayers.size && prayers.size <= 5)
        val fastingObject = root.optionalObject("fasting")
        val fasting = fastingObject?.let {
            val total = it.getInt("total"); val completed = it.getInt("completed")
            require(total >= 0 && completed in 0..total)
            FastingDebtEntity(total = total, completed = completed, updatedAt = it.getLong("updatedAt"))
        }
        val records = root.getJSONArray("records").toEntities { item ->
            val kind = item.getString("kind"); val reference = item.getString("reference"); val amount = item.getInt("amount")
            require(amount > 0 && ((kind in setOf("PRAYER", "PRAYER_ADJUSTMENT", "PRAYER_TOTAL_ADJUSTMENT") && reference in setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")) || (kind in setOf("FASTING", "FASTING_ADJUSTMENT", "FASTING_TOTAL_ADJUSTMENT") && reference == "FASTING")))
            val id = item.getLong("id")
            require(id > 0) { "شناسه رکورد تاریخچه معتبر نیست" }
            val before = if (backupVersion >= 5) item.getInt("beforeCompleted") else 0
            val after = if (backupVersion >= 5) item.getInt("afterCompleted") else 0
            require(before >= 0 && after >= 0)
            val debtTotal = if (reference == "FASTING") fasting?.total else prayers.firstOrNull { it.prayerType == reference }?.total
            require(debtTotal != null) { "رکورد تاریخچه به شمارنده بدهی موجودی اشاره نمی‌کند" }
            if (backupVersion >= 5) {
                require(if (kind.endsWith("ADJUSTMENT")) kotlin.math.abs(after.toLong() - before.toLong()) == amount.toLong() else after.toLong() - before.toLong() == amount.toLong())
            }
            DebtRecordEntity(id, kind, reference, amount, item.getLong("createdAt"), before, after)
        }
        require(records.size <= 100_000 && records.map { it.id }.distinct().size == records.size)
        // A valid historical completion may exceed today's total when a later
        // adjustment corrected that completion or explicitly reduced the debt ceiling.
        // Use recorded adjustment values as the historical upper bound while still
        // rejecting out-of-range counters when no such correction exists.
        val currentDebtTotals = buildMap {
            prayers.forEach { put(it.prayerType, it.total) }
            fasting?.let { put("FASTING", it.total) }
        }
        val historicalAdjustmentCeilings = records.filter { it.kind.endsWith("ADJUSTMENT") }
            .groupBy { it.reference }
            .mapValues { (_, history) -> history.maxOf { maxOf(it.beforeCompleted, it.afterCompleted) } }
        records.forEach { record ->
            val currentTotal = currentDebtTotals[record.reference]
                ?: error("رکورد تاریخچه به شمارنده بدهی موجودی اشاره نمی‌کند")
            val historicalCeiling = maxOf(currentTotal, historicalAdjustmentCeilings[record.reference] ?: 0)
            require(record.beforeCompleted in 0..historicalCeiling && record.afterCompleted in 0..historicalCeiling) {
                "مقادیر پیش و پس از ثبت تاریخچه از سقف بدهی و اصلاحات ثبت‌شده بیشتر هستند"
            }
        }
        val profile = root.optionalObject("profile")?.let {
            val start = it.optNullableString("taklifStartDate")?.let(LocalDate::parse)
            val end = it.optNullableString("taklifEndDate")?.let(LocalDate::parse)
            require(start == null || end == null || !end.isBefore(start))
            val gender = it.getString("gender"); val calendar = it.getString("calendarPreference")
            require(gender in setOf("UNSPECIFIED", "WOMAN", "MAN", "PREFER_NOT_TO_SAY"))
            require(calendar in setOf("PERSIAN", "GREGORIAN", "ISLAMIC_CIVIL"))
            val zone = it.getString("timezoneId")
            val today = LocalDate.now(ZoneId.of(zone))
            require(start == null || !start.isAfter(today)) { "تاریخ شروع برآورد در فایل پشتیبان در آینده است" }
            require(end == null || !end.isAfter(today)) { "تاریخ پایان برآورد در فایل پشتیبان در آینده است" }
            val marja = it.optNullableString("marjaId")
            require(marja == null || marja.matches(Regex("[a-z0-9_-]{2,48}")))
            UserProfileEntity(displayName = it.optNullableString("displayName")?.take(60), gender = gender, taklifStartDate = start?.toString(), taklifEndDate = end?.toString(), timezoneId = zone, calendarPreference = calendar, marjaId = marja, updatedAt = it.getLong("updatedAt"))
        }
        val periods = root.getJSONArray("periods").toEntities { item ->
            val kind = item.getString("kind"); val start = LocalDate.parse(item.getString("startDate")); val end = LocalDate.parse(item.getString("endDate"))
            require(kind in setOf("MENSTRUATION", "POSTPARTUM", "ILLNESS", "OTHER_USER_RECORDED") && !end.isBefore(start))
            require(java.time.temporal.ChronoUnit.DAYS.between(start, end) < 4000) {
                "مدت بازه عذر در فایل پشتیبان از حد مجاز بیشتر است"
            }
            val id = item.getLong("id")
            require(id > 0) { "شناسه بازه عذر معتبر نیست" }
            ExcusePeriodEntity(id, kind, start.toString(), end.toString(), item.optNullableString("note"), item.getLong("createdAt"))
        }
        require(periods.size <= 10_000 && periods.map { it.id }.distinct().size == periods.size)
        val planner = root.optionalObject("planner")?.let {
            val target = it.getInt("dailyPrayerTarget"); val fastTarget = it.getInt("dailyFastingTarget"); val mask = it.getInt("activeWeekdaysMask")
            val hour = it.getInt("reminderHour"); val minute = it.getInt("reminderMinute")
            require(target in 1..100 && fastTarget in 1..30 && mask in 1..127 && hour in 0..23 && minute in 0..59)
            PlannerSettingsEntity(dailyPrayerTarget = target, dailyFastingTarget = fastTarget, activeWeekdaysMask = mask, reminderEnabled = it.getBoolean("reminderEnabled"), reminderHour = hour, reminderMinute = minute, updatedAt = it.getLong("updatedAt"))
        }
        val fiqh = root.optionalObject("fiqh")?.let {
            val url = it.optNullableString("acceptedSourceUrl")
            require(url == null || url.startsWith("https://"))
            val marja = it.optNullableString("marjaId")
            require(marja == null || marja.matches(Regex("[a-z0-9_-]{2,48}")))
            FiqhPreferencesEntity(marjaId = marja, rulesetVersion = it.optNullableString("rulesetVersion")?.take(80), acceptedSourceUrl = url, updatedAt = it.getLong("updatedAt"))
        }
        val cacheToday = LocalDate.now(ZoneId.of(profile?.timezoneId ?: ZoneId.systemDefault().id))
        val verses = root.getJSONArray("dailyVerses").toEntities { item ->
            val dayKey = LocalDate.parse(item.getString("dayKey"))
            require(!dayKey.isAfter(cacheToday)) { "تاریخ آیه ذخیره‌شده در آینده است" }
            val surah = item.getInt("surah"); val ayah = item.getInt("ayah"); val global = item.getInt("globalNumber")
            require(surah in 1..114 && ayah in 1..286 && global in 1..6236)
            if (backupVersion >= 6) require(item.has("translationFootnotes"))
            val arabicText = item.getString("arabicText")
            val translation = item.getString("persianTranslation")
            val translationSource = item.getString("translationSource")
            val translationVersion = item.getString("translationVersion")
            val footnotes = item.optNullableString("translationFootnotes")
            val tafsirText = if (includeUnverifiedMedia) item.optNullableString("tafsirText") else null
            val tafsirSource = if (includeUnverifiedMedia) item.optNullableString("tafsirSource") else null
            val reciterName = if (includeUnverifiedMedia) item.optNullableString("reciterName") else null
            require(arabicText.isNotBlank() && arabicText.length <= 5_000)
            require(translation.isNotBlank() && translation.length <= 5_000)
            require(translationSource.isNotBlank() && translationSource.length <= 240)
            require(translationVersion.isNotBlank() && translationVersion.length <= 80)
            require(footnotes == null || footnotes.length <= 16_000)
            require(tafsirText == null || tafsirText.length <= 8_000)
            require(tafsirSource == null || tafsirSource.length <= 240)
            require(reciterName == null || reciterName.length <= 120)
            val audio = if (includeUnverifiedMedia) item.optNullableString("recitationUrl") else null
            require(audio == null || runCatching { java.net.URI(audio).let { it.scheme == "https" && it.userInfo == null && it.port in listOf(-1, 443) && it.host in setOf("download.quranicaudio.com", "audio.qurancdn.com") } }.getOrDefault(false))
            DailyVerseEntity(item.getString("dayKey"), surah, ayah, global, arabicText, translation, translationSource, translationVersion, tafsirText, tafsirSource, audio, reciterName, item.getLong("fetchedAt"), footnotes)
        }
        require(verses.size <= 60 && verses.map { it.dayKey }.distinct().size == verses.size)
        // Daily verses are a disposable cache, not historical user data. Keep only
        // the same seven-calendar-day window enforced on ordinary cache reads.
        val retainedVerses = verses.filter { !LocalDate.parse(it.dayKey).isBefore(cacheToday.minusDays(6)) }

        val dao = database.debtDao()
        database.withTransaction {
            dao.clearPrayers(); dao.clearFasting(); dao.clearRecords(); dao.clearProfile(); dao.clearExcusePeriods()
            dao.clearPlannerSettings(); dao.clearFiqhPreferences(); dao.clearDailyVerses()
            if (prayers.isNotEmpty()) dao.saveAllPrayers(prayers)
            if (fasting != null) dao.saveAllFasting(fasting)
            if (records.isNotEmpty()) dao.saveAllRecords(records)
            if (profile != null) dao.saveAllProfiles(profile)
            if (periods.isNotEmpty()) dao.saveAllExcusePeriods(periods)
            if (planner != null) dao.saveAllPlannerSettings(planner)
            if (fiqh != null) dao.saveAllFiqhPreferences(fiqh)
            if (retainedVerses.isNotEmpty()) dao.saveAllDailyVerses(retainedVerses)
        }
        } finally {
            plain.fill(0)
        }
    }

    private fun <T> JSONArray.toEntities(mapper: (JSONObject) -> T): List<T> {
        require(length() <= 100_000) { "تعداد رکوردهای فایل پشتیبان بیش از حد مجاز است" }
        return (0 until length()).map { mapper(getJSONObject(it)) }
    }

    private fun JSONObject.optionalObject(key: String): JSONObject? {
        require(has(key)) { "فیلد $key در فایل پشتیبان وجود ندارد" }
        val value = opt(key)
        require(value == JSONObject.NULL || value is JSONObject) { "نوع فیلد $key در فایل پشتیبان معتبر نیست" }
        return value as? JSONObject
    }

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
}
