package app.lucy.data.source

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val MDX_IP = InetAddress.getByName("45.129.229.1")

    // Custom DNS that resolves MangaDex hosts directly to their legitimate IP
    // bypassing ISP DNS hijacking/blocking
    val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname.equals("api.mangadex.org", ignoreCase = true) ||
                hostname.equals("uploads.mangadex.org", ignoreCase = true)
            ) {
                listOf(MDX_IP)
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .dns(customDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val host = req.url.host
            val builder = req.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

            if (host.contains("mangapill")) {
                builder.header("Referer", "https://mangapill.com/")
            } else if (host.contains("mangadex")) {
                builder.header("Referer", "https://mangadex.org/")
            } else if (host.contains("dojing")) {
                builder.header("Referer", "https://dojing.net/")
            } else if (host.contains("shirodoujin")) {
                builder.header("Referer", "https://shirodoujin.com/")
            }
            chain.proceed(builder.build())
        }
        .build()
}

object TitleMatcher {
    fun slugify(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.lowercase()
            .replace(Regex("['’`]"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    // Strict matching rule: prevents serving completely wrong manga
    fun titlesMatch(query: String, candidate: String): Boolean {
        val qSlug = slugify(query)
        val cSlug = slugify(candidate)
        if (qSlug.isBlank() || cSlug.isBlank()) return false
        if (qSlug == cSlug) return true

        val qParts = qSlug.split("-")
        val cParts = cSlug.split("-")
        if (qSlug.startsWith("$cSlug-") && cParts.size >= 3) return true
        if (cSlug.startsWith("$qSlug-") && qParts.size >= 3) return true

        return false
    }
}

object MangaPillSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String): List<String> {
        val encodedQuery = URLEncoder.encode(seriesTitle, "UTF-8")
        val searchUrl = "https://mangapill.com/search?q=$encodedQuery"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(searchHtml, searchUrl)

        var matchedMangaUrl: String? = null
        val items = doc.select(".grid > div, .my-3.border-b")
        for (item in items) {
            val titleElem = item.select("a.line-clamp-2, a.font-bold, a.text-secondary").first() ?: continue
            val candidateTitle = titleElem.text()
            if (TitleMatcher.titlesMatch(seriesTitle, candidateTitle)) {
                matchedMangaUrl = titleElem.absUrl("href")
                break
            }
        }

        if (matchedMangaUrl.isNullOrBlank()) return emptyList()

        // Fetch manga page to find chapters
        val mangaReq = Request.Builder().url(matchedMangaUrl).build()
        val mangaHtml = NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
        val mangaDoc = Jsoup.parse(mangaHtml, matchedMangaUrl)

        var targetChapterUrl: String? = null
        val chapterLinks = mangaDoc.select("#chapters a, div.chapters-container a, a[href*='/chapters/']")
        val targetNum = chapterNum.trim()

        for (link in chapterLinks) {
            val text = link.text()
            val match = Regex("Chapter\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(text)
            if (match != null && match.groupValues[1] == targetNum) {
                targetChapterUrl = link.absUrl("href")
                break
            }
        }

        if (targetChapterUrl.isNullOrBlank()) return emptyList()

        // Fetch chapter reader page
        val chapterReq = Request.Builder().url(targetChapterUrl).build()
        val chapterHtml = NetworkClient.okHttp.newCall(chapterReq).execute().use { it.body?.string().orEmpty() }
        val chapterDoc = Jsoup.parse(chapterHtml, targetChapterUrl)

        val images = mutableListOf<String>()
        val imgElements = chapterDoc.select("picture img, div[data-controller='viewer'] img, img.js-page")
        for (img in imgElements) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo")) {
                images.add(src)
            }
        }
        return images
    }
}

object MangaDexSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String): List<String> {
        val encodedTitle = URLEncoder.encode(seriesTitle, "UTF-8")
        val searchUrl = "https://api.mangadex.org/manga?title=$encodedTitle&limit=10&order[relevance]=desc"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchJsonStr = NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        if (searchJsonStr.isBlank()) return emptyList()

        val searchJson = JSONObject(searchJsonStr)
        val dataArray = searchJson.optJSONArray("data") ?: return emptyList()

        var matchedMangaId: String? = null
        for (i in 0 until dataArray.length()) {
            val mangaObj = dataArray.getJSONObject(i)
            val attrs = mangaObj.getJSONObject("attributes")
            val titles = attrs.getJSONObject("title")

            val candidates = mutableListOf<String>()
            val keys = titles.keys()
            while (keys.hasNext()) {
                candidates.add(titles.getString(keys.next()))
            }
            val altTitles = attrs.optJSONArray("altTitles")
            if (altTitles != null) {
                for (j in 0 until altTitles.length()) {
                    val altObj = altTitles.getJSONObject(j)
                    val altKeys = altObj.keys()
                    while (altKeys.hasNext()) {
                        candidates.add(altObj.getString(altKeys.next()))
                    }
                }
            }

            if (candidates.any { TitleMatcher.titlesMatch(seriesTitle, it) }) {
                matchedMangaId = mangaObj.getString("id")
                break
            }
        }

        if (matchedMangaId.isNullOrBlank()) return emptyList()

        // Fast path: check aggregate endpoint for the chapter UUID
        val aggUrl = "https://api.mangadex.org/manga/$matchedMangaId/aggregate?translatedLanguage[]=en"
        val aggReq = Request.Builder().url(aggUrl).build()
        val aggJsonStr = NetworkClient.okHttp.newCall(aggReq).execute().use { it.body?.string().orEmpty() }
        var chapterUuid: String? = null

        if (aggJsonStr.isNotBlank()) {
            val aggJson = JSONObject(aggJsonStr)
            val vols = aggJson.optJSONObject("volumes")
            if (vols != null) {
                val volKeys = vols.keys()
                while (volKeys.hasNext()) {
                    val volObj = vols.getJSONObject(volKeys.next())
                    val chaptersObj = volObj.optJSONObject("chapters") ?: continue
                    if (chaptersObj.has(chapterNum)) {
                        chapterUuid = chaptersObj.getJSONObject(chapterNum).optString("id")
                        if (!chapterUuid.isNullOrBlank()) break
                    }
                }
            }
        }

        if (chapterUuid.isNullOrBlank()) return emptyList()

        // Fetch /at-home/server/{id}
        val atHomeUrl = "https://api.mangadex.org/at-home/server/$chapterUuid"
        val atHomeReq = Request.Builder().url(atHomeUrl).build()
        val atHomeStr = NetworkClient.okHttp.newCall(atHomeReq).execute().use { it.body?.string().orEmpty() }
        if (atHomeStr.isBlank()) return emptyList()

        val atHomeJson = JSONObject(atHomeStr)
        val baseUrl = atHomeJson.optString("baseUrl")
        val chapterData = atHomeJson.optJSONObject("chapter") ?: return emptyList()
        val hash = chapterData.optString("hash")

        // Prefer data-saver files for quick mobile rendering
        val dataSaverFiles = chapterData.optJSONArray("dataSaver")
        val regularFiles = chapterData.optJSONArray("data")

        val files = dataSaverFiles ?: regularFiles ?: return emptyList()
        val subfolder = if (dataSaverFiles != null) "data-saver" else "data"

        val images = mutableListOf<String>()
        for (i in 0 until files.length()) {
            val filename = files.getString(i)
            images.add("$baseUrl/$subfolder/$hash/$filename")
        }
        return images
    }
}

object DojingSource {
    fun fetchChapterImages(slug: String): List<String> {
        val url = "https://dojing.net/$slug/"
        val req = Request.Builder().url(url).build()
        val html = NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, url)
        val list = mutableListOf<String>()
        for (img in doc.select("#readerarea img, .entry-content img")) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo")) {
                list.add(src)
            }
        }
        return list
    }
}

object ShiroSource {
    fun fetchChapterImages(slug: String): List<String> {
        val url = "https://shirodoujin.com/$slug/"
        val req = Request.Builder().url(url).build()
        val html = NetworkClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, url)
        val list = mutableListOf<String>()
        for (img in doc.select("#readerarea img, .reading-content img")) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo")) {
                list.add(src)
            }
        }
        return list
    }
}

object ChapterLoader {
    fun loadChapter(
        group: String,
        seriesTitle: String,
        englishName: String?,
        chapterNum: String,
        chapterSlug: String
    ): List<String> {
        // 1. Dojing
        if (group.equals("Dojing", ignoreCase = true)) {
            val imgs = runCatching { DojingSource.fetchChapterImages(chapterSlug) }.getOrNull()
            if (!imgs.isNullOrEmpty()) return imgs
        }

        // 2. ShiroDoujin
        if (group.equals("ShiroDoujin", ignoreCase = true)) {
            val imgs = runCatching { ShiroSource.fetchChapterImages(chapterSlug) }.getOrNull()
            if (!imgs.isNullOrEmpty()) return imgs
        }

        // 3. MangaPill (try main title, then english name)
        val titlesToTry = listOfNotNull(seriesTitle, englishName).distinct()
        for (title in titlesToTry) {
            val mpImages = runCatching { MangaPillSource.fetchChapterImages(title, chapterNum) }.getOrNull()
            if (!mpImages.isNullOrEmpty()) return mpImages
        }

        // 4. MangaDex fallback with strict title match
        for (title in titlesToTry) {
            val mdxImages = runCatching { MangaDexSource.fetchChapterImages(title, chapterNum) }.getOrNull()
            if (!mdxImages.isNullOrEmpty()) return mdxImages
        }

        return emptyList()
    }
}
