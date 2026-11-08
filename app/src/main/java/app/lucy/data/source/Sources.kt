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
    private val MP_IPS = listOf(
        InetAddress.getByName("104.21.5.54"),
        InetAddress.getByName("172.67.133.1")
    )

    // Custom DNS that resolves MangaDex & MangaPill hosts directly to legitimate IPs
    // bypassing ISP DNS hijacking/blocking
    val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname.equals("api.mangadex.org", ignoreCase = true) ||
                hostname.equals("uploads.mangadex.org", ignoreCase = true)
            ) {
                listOf(MDX_IP)
            } else if (hostname.equals("mangapill.com", ignoreCase = true)) {
                MP_IPS
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

            if (host.contains("mangapill") || host.contains("readdetectiveconan") || host.contains("mangap")) {
                builder.header("Referer", "https://mangapill.com/")
            } else if (host.contains("manhwatop")) {
                builder.header("Referer", "https://manhwatop.com/")
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

    fun titlesMatch(query: String, candidate: String): Boolean {
        val qSlug = slugify(query)
        val cSlug = slugify(candidate)
        if (qSlug.isBlank() || cSlug.isBlank()) return false
        if (qSlug == cSlug) return true

        val qParts = qSlug.split("-").filter { it.isNotBlank() }
        val cParts = cSlug.split("-").filter { it.isNotBlank() }

        if (qParts.size >= 2 && cSlug.contains(qSlug)) return true
        if (cParts.size >= 2 && qSlug.contains(cSlug)) return true

        return false
    }
}

object MangaPillSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String): List<String> {
        val encodedQuery = URLEncoder.encode(seriesTitle, "UTF-8")
        val searchUrl = "https://mangapill.com/search?q=$encodedQuery"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)

        var matchedMangaUrl: String? = null
        val mangaLinks = doc.select("a[href^='/manga/'], a[href*='/manga/']")
        for (link in mangaLinks) {
            val candidateTitle = link.text()
            val slugFromHref = link.attr("href").substringAfterLast("/")
            if (candidateTitle.isNotBlank() && (TitleMatcher.titlesMatch(seriesTitle, candidateTitle) || TitleMatcher.titlesMatch(seriesTitle, slugFromHref))) {
                matchedMangaUrl = link.absUrl("href")
                break
            }
        }

        if (matchedMangaUrl.isNullOrBlank()) return emptyList()

        val mangaReq = Request.Builder().url(matchedMangaUrl).build()
        val mangaHtml = runCatching {
            NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val mangaDoc = Jsoup.parse(mangaHtml, matchedMangaUrl)

        var targetChapterUrl: String? = null
        val chapterLinks = mangaDoc.select("#chapters a, div.chapters-container a, a[href*='/chapters/']")
        val targetNum = chapterNum.trim()

        for (link in chapterLinks) {
            val text = link.text()
            val href = link.attr("href")
            val match = Regex("chapter[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $href")
            if (match != null) {
                val foundNum = match.groupValues[1]
                if (foundNum == targetNum || foundNum.toFloatOrNull() == targetNum.toFloatOrNull()) {
                    targetChapterUrl = link.absUrl("href")
                    break
                }
            }
        }

        if (targetChapterUrl.isNullOrBlank()) return emptyList()

        val chapterReq = Request.Builder().url(targetChapterUrl).build()
        val chapterHtml = runCatching {
            NetworkClient.okHttp.newCall(chapterReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val chapterDoc = Jsoup.parse(chapterHtml, targetChapterUrl)
        val images = mutableListOf<String>()
        val imgElements = chapterDoc.select("img.js-page, picture img, div[data-controller='viewer'] img, #viewer img, img[data-src]")
        for (img in imgElements) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
            if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo") && !src.contains("favicon")) {
                images.add(src)
            }
        }
        return images
    }
}

object ManhwaSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String): List<String> {
        val encodedQuery = URLEncoder.encode(seriesTitle, "UTF-8")
        val searchUrl = "https://manhwatop.com/?s=$encodedQuery&post_type=wp-manga"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)
        val mangaLinks = doc.select(".c-tabs-item__content a[href*='/manga/'], .post-title a[href*='/manga/'], a[href*='/manga/']")
        var matchedMangaUrl: String? = null

        for (link in mangaLinks) {
            val href = link.absUrl("href").trimEnd('/')
            if (href.contains("/chapter")) continue
            val candidateTitle = link.text()
            val slugFromHref = href.substringAfterLast("/")
            if (candidateTitle.isNotBlank() && (TitleMatcher.titlesMatch(seriesTitle, candidateTitle) || TitleMatcher.titlesMatch(seriesTitle, slugFromHref))) {
                matchedMangaUrl = href
                break
            }
        }

        // Fallback to first non-chapter manga link
        if (matchedMangaUrl.isNullOrBlank()) {
            for (link in mangaLinks) {
                val href = link.absUrl("href").trimEnd('/')
                if (!href.contains("/chapter") && href.contains("/manga/")) {
                    matchedMangaUrl = href
                    break
                }
            }
        }

        if (matchedMangaUrl.isNullOrBlank()) return emptyList()

        val cleanChNum = chapterNum.trim()
        val targetChapterUrl = "$matchedMangaUrl/chapter-$cleanChNum/"

        var chapterReq = Request.Builder().url(targetChapterUrl).build()
        var chapterHtml = runCatching {
            NetworkClient.okHttp.newCall(chapterReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }
        }.getOrNull().orEmpty()

        // Fallback to manga page to extract chapter link if direct URL didn't resolve
        if (chapterHtml.isBlank()) {
            val mangaReq = Request.Builder().url(matchedMangaUrl).build()
            val mangaHtml = runCatching {
                NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull().orEmpty()

            if (mangaHtml.isNotBlank()) {
                val mangaDoc = Jsoup.parse(mangaHtml, matchedMangaUrl)
                val chLinks = mangaDoc.select(".wp-manga-chapter a, a[href*='/chapter-']")
                for (link in chLinks) {
                    val text = link.text()
                    val href = link.attr("href")
                    val match = Regex("chapter[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $href")
                    if (match != null) {
                        val num = match.groupValues[1]
                        if (num == cleanChNum || num.toFloatOrNull() == cleanChNum.toFloatOrNull()) {
                            val resolvedUrl = link.absUrl("href")
                            chapterReq = Request.Builder().url(resolvedUrl).build()
                            chapterHtml = runCatching {
                                NetworkClient.okHttp.newCall(chapterReq).execute().use { it.body?.string().orEmpty() }
                            }.getOrNull().orEmpty()
                            break
                        }
                    }
                }
            }
        }

        if (chapterHtml.isBlank()) return emptyList()

        val chapterDoc = Jsoup.parse(chapterHtml, targetChapterUrl)
        val images = mutableListOf<String>()
        val imgElements = chapterDoc.select(".reading-content img, #readerarea img, .entry-content img, img[src*='manga_']")
        for (img in imgElements) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
            if (src.isNotBlank() && !src.contains("LOGO") && !src.contains("wp-content/uploads") && !src.contains("placeholder")) {
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
        val searchJsonStr = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        if (searchJsonStr.isBlank()) return emptyList()

        val searchJson = runCatching { JSONObject(searchJsonStr) }.getOrNull() ?: return emptyList()
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

        // 1. Try aggregate endpoint
        val aggUrl = "https://api.mangadex.org/manga/$matchedMangaId/aggregate"
        val aggReq = Request.Builder().url(aggUrl).build()
        val aggJsonStr = runCatching {
            NetworkClient.okHttp.newCall(aggReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull().orEmpty()

        var chapterUuid: String? = null

        if (aggJsonStr.isNotBlank()) {
            val aggJson = runCatching { JSONObject(aggJsonStr) }.getOrNull()
            val vols = aggJson?.optJSONObject("volumes")
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

        // 2. Fallback to feed search
        if (chapterUuid.isNullOrBlank()) {
            val feedUrl = "https://api.mangadex.org/manga/$matchedMangaId/feed?limit=100&order[chapter]=desc"
            val feedReq = Request.Builder().url(feedUrl).build()
            val feedStr = runCatching {
                NetworkClient.okHttp.newCall(feedReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull().orEmpty()

            if (feedStr.isNotBlank()) {
                val feedJson = runCatching { JSONObject(feedStr) }.getOrNull()
                val feedData = feedJson?.optJSONArray("data")
                if (feedData != null) {
                    for (i in 0 until feedData.length()) {
                        val chObj = feedData.getJSONObject(i)
                        val chAttrs = chObj.getJSONObject("attributes")
                        val chNum = chAttrs.optString("chapter")
                        val pages = chAttrs.optInt("pages", 0)
                        if (chNum == chapterNum && pages > 0) {
                            chapterUuid = chObj.getString("id")
                            break
                        }
                    }
                }
            }
        }

        if (chapterUuid.isNullOrBlank()) return emptyList()

        val atHomeUrl = "https://api.mangadex.org/at-home/server/$chapterUuid"
        val atHomeReq = Request.Builder().url(atHomeUrl).build()
        val atHomeStr = runCatching {
            NetworkClient.okHttp.newCall(atHomeReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull().orEmpty()
        if (atHomeStr.isBlank()) return emptyList()

        val atHomeJson = runCatching { JSONObject(atHomeStr) }.getOrNull() ?: return emptyList()
        val baseUrl = atHomeJson.optString("baseUrl")
        val chapterData = atHomeJson.optJSONObject("chapter") ?: return emptyList()
        val hash = chapterData.optString("hash")

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
        type: String,
        seriesTitle: String,
        englishName: String?,
        nativeName: String?,
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

        val titlesToTry = listOfNotNull(englishName, seriesTitle, nativeName).distinct()
        val isManhwa = type.equals("Manhwa", ignoreCase = true)

        if (isManhwa) {
            for (title in titlesToTry) {
                val mwImages = runCatching { ManhwaSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mwImages.isNullOrEmpty()) return mwImages
            }
            for (title in titlesToTry) {
                val mdxImages = runCatching { MangaDexSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mdxImages.isNullOrEmpty()) return mdxImages
            }
            for (title in titlesToTry) {
                val mpImages = runCatching { MangaPillSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mpImages.isNullOrEmpty()) return mpImages
            }
        } else {
            for (title in titlesToTry) {
                val mpImages = runCatching { MangaPillSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mpImages.isNullOrEmpty()) return mpImages
            }
            for (title in titlesToTry) {
                val mdxImages = runCatching { MangaDexSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mdxImages.isNullOrEmpty()) return mdxImages
            }
            for (title in titlesToTry) {
                val mwImages = runCatching { ManhwaSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mwImages.isNullOrEmpty()) return mwImages
            }
        }

        return emptyList()
    }
}
