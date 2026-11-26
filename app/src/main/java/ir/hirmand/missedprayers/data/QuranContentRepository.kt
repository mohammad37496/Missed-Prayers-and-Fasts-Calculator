package ir.hirmand.missedprayers.data

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
    private val contentProvider: QuranEncContentProvider = HttpQuranEncContentProvider()
) {
    suspend fun loadFor(date: LocalDate = LocalDate.now(), forceRefresh: Boolean = false): DailyVerseResult = withContext(Dispatchers.IO) {
        val key = date.toString()
        if (!forceRefresh) dao.getDailyVerse(key)?.let { return@withContext DailyVerseResult(it, true, null) }
        try {
            val fetched = fetchVerse(date)
            val cached = dao.getDailyVerse(key)
            val verse = fetched.copy(
                tafsirText = fetched.tafsirText ?: cached?.tafsirText,
                tafsirSource = fetched.tafsirSource ?: cached?.tafsirSource,
                recitationUrl = fetched.recitationUrl ?: cached?.recitationUrl,
                reciterName = fetched.reciterName ?: cached?.reciterName
            )
            dao.saveDailyVerse(verse)
            dao.pruneDailyVerses(date.minusDays(7).toString())
            DailyVerseResult(verse, false, null)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val cached = dao.getLatestDailyVerse()
            DailyVerseResult(
                cached,
                cached != null,
                error.message ?: "دریافت محتوای روز ممکن نشد",
                stale = cached != null && cached.dayKey != key
            )
        }
    }

    private suspend fun fetchVerse(date: LocalDate): DailyVerseEntity {
        val catalogue = contentProvider.listTranslations()
        val translationMeta = findTranslation(catalogue, listOf("rowwad", "persian_ih"))
            ?: error("ترجمه فارسی دارای مشخصات منبع در فهرست QuranEnc پیدا نشد")
        val translationKey = translationMeta.getString("key")
        val translationLanguage = translationMeta.optString("language_iso_code")
        require(translationKey.matches(Regex("[a-z0-9_]{1,80}")) && (translationLanguage.isBlank() || translationLanguage == "fa")) {
            "شناسه یا زبان ترجمه فارسی از منبع معتبر نیست"
        }
        val translationTitle = translationMeta.optString("title", "QuranEnc")
        val translationVersion = translationMeta.optString("version", "نامشخص")

        val surah = ((date.dayOfYear - 1) % 114) + 1
        val verses = contentProvider.translationForSurah(translationKey, surah)
        require(verses.length() > 0) { "برای سوره انتخاب‌شده آیه‌ای دریافت نشد" }
        val index = Math.floorMod(date.toEpochDay(), verses.length().toLong()).toInt()
        val selected = verses.getJSONObject(index)
        val globalNumber = selected.optString("id").toIntOrNull()?.takeIf { it in 1..6236 }
            ?: error("شناسه سراسری آیه از منبع معتبر دریافت نشد")
        val surahNumber = selected.optString("sura").toIntOrNull() ?: surah
        val ayahNumber = selected.optString("aya").toIntOrNull() ?: (index + 1)
        require(surahNumber in 1..114 && ayahNumber in 1..286) { "شماره سوره یا آیه از منبع معتبر نیست" }
        val arabic = arabicTextProvider.getAyah(surahNumber, ayahNumber)
        val translation = selected.optString("translation")
        require(!arabic.isNullOrBlank() && translation.isNotBlank()) { "متن آیه از Tanzil یا ترجمه فارسی از QuranEnc ناقص است" }

        val tafsirMeta = optionalContent { findTafsir(contentProvider.listTafsirs()) }
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
        val audio = optionalContent { recitationProvider.resolve(globalNumber) }

        return DailyVerseEntity(
            dayKey = date.toString(), surah = surahNumber, ayah = ayahNumber, globalNumber = globalNumber,
            arabicText = arabic, persianTranslation = translation,
            translationSource = "$translationTitle · QuranEnc.com", translationVersion = translationVersion,
            tafsirText = tafsir,
            tafsirSource = if (tafsir != null) tafsirMeta?.let { "${it.optString("title")} · QuranEnc.com · نسخه ${it.optString("version", "نامشخص")}" } else null,
            recitationUrl = audio?.url?.takeIf(::isAllowedRecitationUrl), reciterName = audio?.reciterName, fetchedAt = System.currentTimeMillis()
        )
    }

    private fun isAllowedRecitationUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.host in setOf("download.quranicaudio.com", "audio.qurancdn.com")
    }.getOrDefault(false)

    private fun findTranslation(items: JSONArray, terms: List<String>): JSONObject? =
        (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull { item ->
            val key = item.optString("key").lowercase()
            val title = item.optString("title").lowercase()
            terms.any { key == it || title.contains(it) }
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
