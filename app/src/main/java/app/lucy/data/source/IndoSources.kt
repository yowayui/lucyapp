package app.lucy.data.source

import okhttp3.Request
import org.jsoup.Jsoup

// Indonesian source chapter-image resolvers. The catalog stores chapter page
// URLs (not numbers) for "-ID" source groups so these loaders can fetch the
// exact page without searching by title.
object KomikuSource {
    private const val BASE = "https://komiku.org"

    fun fetchChapterImages(chapterPath: String): List<String> {
        val url = if (chapterPath.startsWith("http")) chapterPath else BASE + chapterPath
        val req = Request.Builder().url(url).build()
        val html = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html, url)
        return doc.select("#Baca_Komik img.klazy, #Baca_Komik img.ww")
            .mapNotNull { el ->
                val src = el.absUrl("src").ifBlank { el.attr("src") }
                if (src.isBlank()) null else src
            }
            .filter { it.contains("komiku", ignoreCase = true) && !it.contains("promosi", ignoreCase = true) }
            .distinct()
    }
}

object KomikindoSource {
    fun fetchChapterImages(chapterPath: String): List<String> {
        val req = Request.Builder().url(chapterPath).build()
        val html = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html, chapterPath)
        return doc.select("#chimg-auh img")
            .mapNotNull { el ->
                val src = el.absUrl("src").ifBlank { el.attr("src") }
                if (src.isBlank()) null else src
            }
            .distinct()
    }
}

object KiryuuSource {
    private const val BASE = "https://v7.kiryuu.to"

    fun fetchChapterImages(chapterPath: String): List<String> {
        val url = if (chapterPath.startsWith("http")) chapterPath else BASE + chapterPath
        val req = Request.Builder().url(url).build()
        val html = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html, url)
        return doc.select("section[data-image-data] img")
            .mapNotNull { el ->
                val src = el.absUrl("src").ifBlank { el.attr("src") }
                if (src.isBlank()) null else src
            }
            .distinct()
    }
}

object IkiruSource {
    private const val BASE = "https://10.ikiru.wtf"

    fun fetchChapterImages(chapterPath: String): List<String> {
        // Ikiru chapter paths are stored relative because the public host
        // rotates between 09/10/... subdomains.
        val url = if (chapterPath.startsWith("http")) chapterPath else BASE + chapterPath
        val req = Request.Builder().url(url).build()
        val html = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html, url)
        return doc.select("#reader-scroll img, .rpage img")
            .mapNotNull { el ->
                val src = el.absUrl("src").ifBlank { el.attr("src") }
                if (src.isBlank() || src.startsWith("data:")) null else src
            }
            .distinct()
    }
}

object BacaKomikSource {
    private const val BASE = "https://bacakomik.my"

    fun fetchChapterImages(chapterPath: String): List<String> {
        val url = if (chapterPath.startsWith("http")) chapterPath else BASE + chapterPath
        val req = Request.Builder().url(url).build()
        val html = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html, url)
        return doc.select("#chimg-auh img")
            .mapNotNull { el ->
                val lazy = el.absUrl("data-lazy-src").ifBlank { el.attr("data-lazy-src") }
                val src = lazy.ifBlank {
                    el.absUrl("src").ifBlank { el.attr("src") }
                }
                if (src.isBlank() || src.startsWith("data:")) null else src
            }
            .distinct()
    }
}
