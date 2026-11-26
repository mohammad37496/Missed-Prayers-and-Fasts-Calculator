package ir.hirmand.missedprayers.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.time.LocalDate

data class DailyVerseResult(val verse: DailyVerseEntity?, val fromCache: Boolean, val message: String?, val loading: Boolean = false)

data class RecitationAudio(val url: String, val reciterName: String, val source: String)

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
    private val recitationProvider: QuranRecitationProvider = UnconfiguredQuranRecitationProvider
) {
    suspend fun loadFor(date: LocalDate = LocalDate.now(), forceRefresh: Boolean = false): DailyVerseResult = withContext(Dispatchers.IO) {
        val key = date.toString()
        if (!forceRefresh) dao.getDailyVerse(key)?.let { return@withContext DailyVerseResult(it, true, null) }
        try {
            val verse = fetchVerse(date)
            dao.saveDailyVerse(verse)
            dao.pruneDailyVerses(date.minusDays(7).toString())
            DailyVerseResult(verse, false, null)
        } catch (error: Exception) {
            val cached = dao.getLatestDailyVerse()
            DailyVerseResult(cached, cached != null, error.message ?: "دریافت محتوای روز ممکن نشد")
        }
    }

    private suspend fun fetchVerse(date: LocalDate): DailyVerseEntity {
        val catalogue = getJson("https://quranenc.com/api/v1/translations/list/fa?localization=fa").getJSONArray("translations")
        val translationMeta = findTranslation(catalogue, listOf("rowwad", "persian_ih"))
            ?: error("ترجمه فارسی دارای مشخصات منبع در فهرست QuranEnc پیدا نشد")
        val translationKey = translationMeta.getString("key")
        val translationTitle = translationMeta.optString("title", "QuranEnc")
        val translationVersion = translationMeta.optString("version", "نامشخص")

        val surah = ((date.dayOfYear - 1) % 114) + 1
        val surahResponse = getJson("https://quranenc.com/api/v1/translation/sura/$translationKey/$surah")
        val verses = surahResponse.getJSONArray("result")
        require(verses.length() > 0) { "برای سوره انتخاب‌شده آیه‌ای دریافت نشد" }
        val index = Math.floorMod(date.toEpochDay(), verses.length().toLong()).toInt()
        val selected = verses.getJSONObject(index)
        val globalNumber = selected.optString("id").toIntOrNull()?.takeIf { it in 1..6236 }
            ?: error("شناسه سراسری آیه از منبع معتبر دریافت نشد")
        val surahNumber = selected.optString("sura").toIntOrNull() ?: surah
        val ayahNumber = selected.optString("aya").toIntOrNull() ?: (index + 1)
        val arabic = arabicTextProvider.getAyah(surahNumber, ayahNumber)
        val translation = selected.optString("translation")
        require(!arabic.isNullOrBlank() && translation.isNotBlank()) { "متن آیه از Tanzil یا ترجمه فارسی از QuranEnc ناقص است" }

        val tafsirMeta = findTafsir(catalogue)
        val tafsir = tafsirMeta?.let { meta ->
            runCatching {
                val tafsirKey = meta.getString("key")
                val response = getJson("https://quranenc.com/api/v1/translation/aya/$tafsirKey/$surahNumber/$ayahNumber")
                response.getJSONObject("result").optString("translation").takeIf(String::isNotBlank)
            }.getOrNull()
        }
        val audio = runCatching { recitationProvider.resolve(globalNumber) }.getOrNull()

        return DailyVerseEntity(
            dayKey = date.toString(), surah = surahNumber, ayah = ayahNumber, globalNumber = globalNumber,
            arabicText = arabic, persianTranslation = translation,
            translationSource = "$translationTitle · QuranEnc.com", translationVersion = translationVersion,
            tafsirText = tafsir,
            tafsirSource = if (tafsir != null) tafsirMeta?.optString("title")?.plus(" · QuranEnc.com") else null,
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
            val title = item.optString("title").lowercase()
            title.contains("سعدی") || title.contains("السعدی") || title.contains("saadi")
        }

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
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
