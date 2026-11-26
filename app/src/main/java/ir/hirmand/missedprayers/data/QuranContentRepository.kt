package ir.hirmand.missedprayers.data

import ir.hirmand.missedprayers.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.time.LocalDate
import java.io.ByteArrayOutputStream

private const val MAX_QURANENC_RESPONSE_BYTES = 2 * 1024 * 1024
private const val DAILY_VERSE_CACHE_DAYS = 7L

data class DailyVerseResult(
    val verse: DailyVerseEntity?,
    val fromCache: Boolean,
    val message: String?,
    val loading: Boolean = false,
    val stale: Boolean = false
)

data class RecitationAudio(val url: String, val reciterName: String, val source: String)

interface QuranEncContentProvider {
    fun listTranslations(): JSONArray
    fun listTafsirs(): JSONArray
    fun translationForSurah(key: String, surah: Int): JSONArray
    fun tafsirForAyah(key: String, surah: Int, ayah: Int): JSONArray
}

class HttpQuranEncContentProvider : QuranEncContentProvider {
    override fun listTranslations(): JSONArray = getJson("https://quranenc.com/api/v1/translations/list/fa?localization=fa").getJSONArray("translations")
    override fun listTafsirs(): JSONArray = getJson("https://quranenc.com/api/v1/tafsirs/list/fa?localization=fa").getJSONArray("tafsirs")
    override fun translationForSurah(key: String, surah: Int): JSONArray = getJson("https://quranenc.com/api/v1/translation/sura/$key/$surah").getJSONArray("result")
    override fun tafsirForAyah(key: String, surah: Int, ayah: Int): JSONArray = getJson("https://quranenc.com/api/v1/tafsir/aya/$key/$surah/$ayah").getJSONArray("result")

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MissedWorshipCalculator/0.1 Android")
            val status = connection.responseCode
            if (status !in 200..299) error("سرویس محتوا پاسخ $status برگرداند")
            require(connection.contentLengthLong <= MAX_QURANENC_RESPONSE_BYTES || connection.contentLengthLong < 0) {
                "پاسخ سرویس محتوا بیش از حد مجاز است"
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_QURANENC_RESPONSE_BYTES) { "پاسخ سرویس محتوا بیش از حد مجاز است" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}

interface QuranRecitationProvider {
    suspend fun resolve(globalAyahNumber: Int): RecitationAudio?
}

/** A Quran Foundation authenticated backend is required; no API secret is embedded in the Android client. */
object UnconfiguredQuranRecitationProvider : QuranRecitationProvider {
    override suspend fun resolve(globalAyahNumber: Int): RecitationAudio? = null
}

class QuranContentRepository(
    private val dao: DebtDao,
    private val arabicTextProvider: ArabicQuranTextProvider,
    private val recitationProvider: QuranRecitationProvider = UnconfiguredQuranRecitationProvider,
    private val contentProvider: QuranEncContentProvider = HttpQuranEncContentProvider(),
    private val allowUnverifiedMedia: Boolean = BuildConfig.DEBUG
) {
    suspend fun loadFor(date: LocalDate = LocalDate.now(), forceRefresh: Boolean = false): DailyVerseResult = withContext(Dispatchers.IO) {
        val key = date.toString()
        // Enforce the retention window on cache hits as well as successful network refreshes.
        // Keeping date.minusDays(6) and later yields exactly seven calendar dates including today.
        dao.pruneDailyVerses(date.minusDays(DAILY_VERSE_CACHE_DAYS - 1).toString())
        if (!forceRefresh) dao.getDailyVerse(key)?.let {
            return@withContext DailyVerseResult(it.withMediaPolicy(), true, null)
        }
        try {
            val fetched = fetchVerse(date)
            val cached = dao.getDailyVerse(key)
            val verse = fetched.copy(
                tafsirText = if (allowUnverifiedMedia) fetched.tafsirText ?: cached?.tafsirText else null,
                tafsirSource = if (allowUnverifiedMedia) fetched.tafsirSource ?: cached?.tafsirSource else null,
                recitationUrl = if (allowUnverifiedMedia) fetched.recitationUrl ?: cached?.recitationUrl else null,
                reciterName = if (allowUnverifiedMedia) fetched.reciterName ?: cached?.reciterName else null
            )
            dao.saveDailyVerse(verse)
            DailyVerseResult(verse, false, null)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val cached = dao.getLatestDailyVerse()
            DailyVerseResult(
                cached?.withMediaPolicy(),
                cached != null,
                error.message ?: "دریافت محتوای روز ممکن نشد",
                stale = cached != null && cached.dayKey != key
            )
        }
    }

    private fun DailyVerseEntity.withMediaPolicy(): DailyVerseEntity = if (allowUnverifiedMedia) this else copy(
        tafsirText = null, tafsirSource = null, recitationUrl = null, reciterName = null
    )

    private suspend fun fetchVerse(date: LocalDate): DailyVerseEntity {
        val catalogue = contentProvider.listTranslations()
        val translationMeta = findTranslation(catalogue)
            ?: error("ترجمه فارسی دارای مشخصات منبع در فهرست QuranEnc پیدا نشد")
        val translationKey = translationMeta.getString("key")
        val translationLanguage = translationMeta.optString("language_iso_code")
        // The canonical Persian Rowwad key and publisher title are authoritative
        // when older/test catalogue responses omit language_iso_code; if supplied,
        // the language must still be Persian.
        require(translationKey == "persian_ih" && (translationLanguage.isBlank() || translationLanguage == "fa")) {
            "شناسه یا زبان ترجمه فارسی از منبع معتبر نیست"
        }
        val translationTitle = translationMeta.getString("title")
        val translationVersion = translationMeta.getString("version")
        require(translationTitle.contains("rowwad", ignoreCase = true) || translationTitle.contains("رواد"))
        require(translationVersion.isNotBlank()) { "نسخه ترجمه فارسی از منبع رسمی مشخص نشده است" }

        val surah = ((date.dayOfYear - 1) % 114) + 1
        val verses = contentProvider.translationForSurah(translationKey, surah)
        require(verses.length() > 0) { "برای سوره انتخاب‌شده آیه‌ای دریافت نشد" }
        val index = Math.floorMod(date.toEpochDay(), verses.length().toLong()).toInt()
        val selected = verses.getJSONObject(index)
        val globalNumber = selected.optString("id").toIntOrNull()?.takeIf { it in 1..6236 }
            ?: error("شناسه سراسری آیه از منبع معتبر دریافت نشد")
        val surahNumber = selected.optString("sura").toIntOrNull()
            ?: error("شماره سوره از پاسخ رسمی QuranEnc دریافت نشد")
        val ayahNumber = selected.optString("aya").toIntOrNull()
            ?: error("شماره آیه از پاسخ رسمی QuranEnc دریافت نشد")
        require(surahNumber == surah && ayahNumber in 1..286) { "شماره سوره یا آیه با درخواست QuranEnc مطابقت ندارد" }
        val arabic = arabicTextProvider.getAyah(surahNumber, ayahNumber)
        val translation = selected.optString("translation")
        val footnotesValue = selected.opt("footnotes")
        val translationFootnotes = when (footnotesValue) {
            null, JSONObject.NULL -> null
            is String -> footnotesValue.takeIf(String::isNotBlank)
            else -> error("ساختار پانوشت‌های مترجم از QuranEnc معتبر نیست")
        }
        require(!arabic.isNullOrBlank() && translation.isNotBlank()) { "متن آیه از Tanzil یا ترجمه فارسی از QuranEnc ناقص است" }
        require(arabic.length <= 5_000 && translation.length <= 5_000 && (translationFootnotes == null || translationFootnotes.length <= 16_000)) {
            "متن آیه یا پانوشت‌های مترجم از حد مجاز طول بیشتر است"
        }

        // The published QuranEnc reuse terms describe translations but do not
        // separately grant republication rights for this Persian tafsir edition.
        // Keep unverified media behind a debug-only gate until source-specific terms are confirmed.
        val tafsirMeta = if (allowUnverifiedMedia) optionalContent { findTafsir(contentProvider.listTafsirs()) } else null
        val tafsir = tafsirMeta?.let { meta ->
            optionalContent {
                val tafsirKey = meta.getString("key")
                val blocks = contentProvider.tafsirForAyah(tafsirKey, surahNumber, ayahNumber)
                (0 until blocks.length()).map { blocks.getJSONObject(it) }.firstOrNull { block ->
                    block.optString("type").contains("تفسير آية") &&
                        block.optInt("from_aya", -1) == ayahNumber && block.optInt("to_aya", -1) == ayahNumber
                }?.optString("text")?.takeIf(String::isNotBlank)
            }
        }
        val audio = if (allowUnverifiedMedia) optionalContent { recitationProvider.resolve(globalNumber) } else null

        return DailyVerseEntity(
            dayKey = date.toString(), surah = surahNumber, ayah = ayahNumber, globalNumber = globalNumber,
            arabicText = arabic, persianTranslation = translation,
            translationSource = "$translationTitle · QuranEnc.com", translationVersion = translationVersion,
            translationFootnotes = translationFootnotes,
            tafsirText = tafsir,
            tafsirSource = if (tafsir != null) tafsirMeta?.let { "${it.optString("title")} · QuranEnc.com · نسخه ${it.optString("version", "نامشخص")}" } else null,
            recitationUrl = audio?.url?.takeIf(::isAllowedRecitationUrl), reciterName = audio?.reciterName, fetchedAt = System.currentTimeMillis()
        )
    }

    private fun isAllowedRecitationUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.host in setOf("download.quranicaudio.com", "audio.qurancdn.com")
    }.getOrDefault(false)

    private fun findTranslation(items: JSONArray): JSONObject? =
        (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull { item ->
            item.optString("key") == "persian_ih" &&
                (item.optString("title").contains("rowwad", ignoreCase = true) || item.optString("title").contains("رواد"))
        }

    private fun findTafsir(items: JSONArray): JSONObject? =
        (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull { item ->
            item.optString("key") == "persian_saadi" && item.optString("language_iso_code") == "fa"
        }

    private suspend fun <T> optionalContent(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

}
