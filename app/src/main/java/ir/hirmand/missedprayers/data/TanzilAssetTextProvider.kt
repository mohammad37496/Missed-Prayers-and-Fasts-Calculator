package ir.hirmand.missedprayers.data

import android.content.res.AssetManager

interface ArabicQuranTextProvider {
    fun getAyah(surah: Int, ayah: Int): String?
}

/** Reads the unmodified Tanzil Uthmani v1.1 text bundled under its verbatim-copy terms. */
class TanzilAssetTextProvider(private val assets: AssetManager) : ArabicQuranTextProvider {
    private val verses: Map<Long, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val result = LinkedHashMap<Long, String>(6236)
        val surahs = HashSet<Int>(114)
        assets.open("quran/tanzil_uthmani_v1.1.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val first = line.indexOf('|')
                val second = if (first >= 0) line.indexOf('|', first + 1) else -1
                require(first > 0 && second > first + 1) { "ساختار فایل متن قرآن Tanzil معتبر نیست" }
                val surah = line.substring(0, first).toInt()
                val ayah = line.substring(first + 1, second).toInt()
                val text = line.substring(second + 1)
                require(surah in 1..114 && ayah in 1..286 && text.isNotEmpty()) { "رکورد متن قرآن Tanzil معتبر نیست" }
                surahs.add(surah)
                require(result.put(surah * 1000L + ayah, text) == null) { "شناسه تکراری آیه در فایل Tanzil" }
            }
        }
        require(result.size == 6236) { "فایل متن قرآن باید دقیقاً ۶۲۳۶ آیه داشته باشد" }
        require(surahs.size == 114) { "فایل متن قرآن باید همه ۱۱۴ سوره را داشته باشد" }
        result
    }

    override fun getAyah(surah: Int, ayah: Int): String? = verses[surah * 1000L + ayah]
}
