package app.lucy.data.source

import android.content.Context
import app.lucy.data.model.Chapter
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val STATIC_HOSTS = mapOf(
        "mangapill.com" to listOf(InetAddress.getByName("104.21.5.54"), InetAddress.getByName("172.67.133.1")),
        "cdn.readdetectiveconan.com" to listOf(InetAddress.getByName("104.21.64.158"), InetAddress.getByName("172.67.186.172")),
        "weebcentral.com" to listOf(InetAddress.getByName("104.21.32.126"), InetAddress.getByName("172.67.151.248")),
        "scans.lastation.us" to listOf(InetAddress.getByName("104.21.94.250"), InetAddress.getByName("172.67.142.40")),
        "hot.planeptune.us" to listOf(InetAddress.getByName("172.67.198.66"), InetAddress.getByName("104.21.36.180")),
        "temp.compsci88.com" to listOf(InetAddress.getByName("172.67.183.54"), InetAddress.getByName("104.21.51.175")),
        "manhwatop.com" to listOf(InetAddress.getByName("172.67.202.193"), InetAddress.getByName("104.21.77.2")),
        "c1.manhwatop.com" to listOf(InetAddress.getByName("172.67.202.193"), InetAddress.getByName("104.21.77.2")),
        "c2.manhwatop.com" to listOf(InetAddress.getByName("172.67.202.193"), InetAddress.getByName("104.21.77.2")),
        "c3.manhwatop.com" to listOf(InetAddress.getByName("172.67.202.193"), InetAddress.getByName("104.21.77.2")),
        "c4.manhwatop.com" to listOf(InetAddress.getByName("104.21.77.2"), InetAddress.getByName("172.67.202.193")),
        "c5.manhwatop.com" to listOf(InetAddress.getByName("172.67.202.193"), InetAddress.getByName("104.21.77.2")),
        "api.mangadex.org" to listOf(InetAddress.getByName("45.129.229.1")),
        "uploads.mangadex.org" to listOf(InetAddress.getByName("45.129.229.2")),
        "dojing.net" to listOf(InetAddress.getByName("172.67.177.176"), InetAddress.getByName("104.21.40.59")),
        "shirodoujin.com" to listOf(InetAddress.getByName("172.67.193.133"), InetAddress.getByName("104.21.65.224")),
        "mkissa.to" to listOf(InetAddress.getByName("172.67.187.90"), InetAddress.getByName("104.21.56.175")),
        "api.mkissa.net" to listOf(InetAddress.getByName("172.67.187.90"), InetAddress.getByName("104.21.56.175")),
        "cdn.mkissa.net" to listOf(InetAddress.getByName("172.67.187.90"), InetAddress.getByName("104.21.56.175")),
        "wp.youtube-anime.com" to listOf(InetAddress.getByName("104.21.86.174"), InetAddress.getByName("172.67.223.73")),
        "ytimgf.youtube-anime.com" to listOf(InetAddress.getByName("104.21.86.174"), InetAddress.getByName("172.67.223.73")),
        "s4.anilist.co" to listOf(InetAddress.getByName("104.26.14.71"), InetAddress.getByName("104.26.15.71"), InetAddress.getByName("172.67.71.232")),
        "cdn.myanimelist.net" to listOf(InetAddress.getByName("23.203.165.123"), InetAddress.getByName("23.203.165.128")),
        "cdn.mangaupdates.com" to listOf(InetAddress.getByName("209.38.117.245")),
        "thumb.mghcdn.com" to listOf(InetAddress.getByName("104.26.12.244"), InetAddress.getByName("104.26.13.244"), InetAddress.getByName("172.67.72.150"))
    )

    private val BLOCKED_IPS = setOf("202.3.218.139", "180.131.144.144", "180.131.144.145", "36.86.63.181", "36.86.63.182")
    private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lower = hostname.lowercase()
            dnsCache[lower]?.let { return it }

            // Keep the static IPs first to bypass ISP DNS poisoning, then append
            // the system result as a fallback when a hardcoded edge has rotated.
            val systemIps = runCatching { Dns.SYSTEM.lookup(hostname) }
                .getOrNull()
                .orEmpty()
                .filter { ip -> ip.hostAddress?.let { addr -> !BLOCKED_IPS.contains(addr) } == true }

            val combined = (STATIC_HOSTS[lower].orEmpty() + systemIps)
                .distinctBy { it.hostAddress }

            if (combined.isNotEmpty()) {
                dnsCache[lower] = combined
                return combined
            }

            return runCatching { Dns.SYSTEM.lookup(hostname) }.getOrElse { emptyList() }
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
            } else if (host.contains("weebcentral") || host.contains("lastation") || host.contains("planeptune") || host.contains("compsci88")) {
                builder.header("Referer", "https://weebcentral.com/")
            } else if (host.contains("manhwatop")) {
                builder.header("Referer", "https://manhwatop.com/")
            } else if (host.contains("mangadex")) {
                builder.header("Referer", "https://mangadex.org/")
            } else if (host.contains("dojing")) {
                builder.header("Referer", "https://dojing.net/")
            } else if (host.contains("shirodoujin")) {
                builder.header("Referer", "https://shirodoujin.com/")
            } else if (host.contains("mkissa") || host.contains("youtube-anime")) {
                builder.header("Referer", "https://mkissa.to/")
            }
            chain.proceed(builder.build())
        }
        .build()
}

object TitleMatcher {
    fun cleanSearchQuery(title: String): String {
        return title.replace(Regex("[/\\\\:_~-]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun slugify(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.lowercase()
            .replace(Regex("['’`]"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    fun matchScore(query: String, candidateTitle: String, candidateSlug: String = ""): Int {
        val q = slugify(query)
        val cTitle = slugify(candidateTitle)
        val cSlug = slugify(candidateSlug)
        if (q.isBlank()) return 0

        if (q == cTitle || (cSlug.isNotBlank() && q == cSlug)) return 100

        val qParts = q.split("-").filter { it.isNotBlank() }
        val cTitleParts = cTitle.split("-").filter { it.isNotBlank() }

        if (cTitle.startsWith("$q-") || (cSlug.isNotBlank() && cSlug.startsWith("$q-"))) return 75
        if (q.startsWith("$cTitle-") || (cSlug.isNotBlank() && q.startsWith("$cSlug-"))) return 65

        if (qParts.size >= 2 && (cTitle.contains(q) || (cSlug.isNotBlank() && cSlug.contains(q)))) return 50
        if (cTitleParts.size >= 2 && q.contains(cTitle)) return 40

        return 0
    }

    fun titlesMatch(query: String, candidate: String): Boolean {
        return matchScore(query, candidate, candidate) > 0
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

        val candidates = mutableListOf<Triple<String, String, Int>>()
        val mangaLinks = doc.select("a[href^='/manga/'], a[href*='/manga/']")
        for (link in mangaLinks) {
            val candidateTitle = link.text().trim()
            val href = link.absUrl("href")
            val slugFromHref = link.attr("href").substringAfterLast("/")
            val score = TitleMatcher.matchScore(seriesTitle, candidateTitle, slugFromHref)
            if (score > 0 && href.isNotBlank()) {
                candidates.add(Triple(href, candidateTitle, score))
            }
        }

        val sortedCandidates = candidates.distinctBy { it.first }.sortedByDescending { it.third }
        val targetNum = chapterNum.trim()

        for (cand in sortedCandidates.take(3)) {
            val mangaUrl = cand.first
            val mangaReq = Request.Builder().url(mangaUrl).build()
            val mangaHtml = runCatching {
                NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull() ?: continue

            val mangaDoc = Jsoup.parse(mangaHtml, mangaUrl)
            var targetChapterUrl: String? = null
            val chapterLinks = mangaDoc.select("#chapters a, div.chapters-container a, a[href*='/chapters/']")

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

            if (!targetChapterUrl.isNullOrBlank()) {
                val chapterReq = Request.Builder().url(targetChapterUrl).build()
                val chapterHtml = runCatching {
                    NetworkClient.okHttp.newCall(chapterReq).execute().use { it.body?.string().orEmpty() }
                }.getOrNull() ?: continue

                val chapterDoc = Jsoup.parse(chapterHtml, targetChapterUrl)
                val images = mutableListOf<String>()
                val imgElements = chapterDoc.select("img.js-page, picture img, div[data-controller='viewer'] img, #viewer img, img[data-src]")
                for (img in imgElements) {
                    val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
                    if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo") && !src.contains("favicon")) {
                        images.add(src)
                    }
                }
                if (images.isNotEmpty()) {
                    return images
                }
            }
        }

        return emptyList()
    }

    fun fetchLiveChapters(seriesTitle: String, seriesSlug: String): List<Chapter> {
        val queryText = TitleMatcher.cleanSearchQuery(seriesTitle)
        val encodedQuery = URLEncoder.encode(queryText.ifBlank { seriesTitle }, "UTF-8")
        val searchUrl = "https://mangapill.com/search?q=$encodedQuery"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)
        val candidates = mutableListOf<Pair<String, Int>>()

        for (link in doc.select("a[href^='/manga/'], a[href*='/manga/']")) {
            val candidateTitle = link.text().trim()
            val href = link.absUrl("href")
            val slugFromHref = link.attr("href").substringAfterLast("/")
            val score = TitleMatcher.matchScore(seriesTitle, candidateTitle, slugFromHref)
            if (score > 0 && href.isNotBlank()) {
                candidates.add(Pair(href, score))
            }
        }

        val sorted = candidates.distinctBy { it.first }.sortedByDescending { it.second }
        for (cand in sorted.take(3)) {
            val mangaUrl = cand.first
            val mangaReq = Request.Builder().url(mangaUrl).build()
            val mangaHtml = runCatching {
                NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull() ?: continue

            val mangaDoc = Jsoup.parse(mangaHtml, mangaUrl)
            val chapters = mutableListOf<Chapter>()
            val chapterLinks = mangaDoc.select("#chapters a, div.chapters-container a, a[href*='/chapters/']")
            val seen = mutableSetOf<String>()
            for (link in chapterLinks) {
                val absHref = link.absUrl("href")
                if (!absHref.contains("/chapters/")) continue
                val text = link.text()
                val match = Regex("chapter[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $absHref")
                if (match != null) {
                    val num = match.groupValues[1]
                    if (seen.add(num)) {
                        chapters.add(
                            Chapter(
                                title = "Chapter $num",
                                slug = "$seriesSlug-chapter-$num",
                                number = num
                            )
                        )
                    }
                }
            }
            if (chapters.isNotEmpty()) return chapters
        }

        return emptyList()
    }
}

object WeebCentralSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String): List<String> {
        val queryText = TitleMatcher.cleanSearchQuery(seriesTitle)
        val encodedQuery = URLEncoder.encode(queryText.ifBlank { seriesTitle }, "UTF-8")
        val searchUrl = "https://weebcentral.com/search/data?author=&text=$encodedQuery&sort=Best+Match&order=Ascending&official=Any&display_mode=Full+Display"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)
        val candidates = mutableListOf<Triple<String, String, Int>>()

        for (link in doc.select("a[href*='/series/']")) {
            val href = link.attr("href")
            val seriesId = href.substringAfter("/series/").substringBefore("/")
            val seriesSlug = href.substringAfter("/series/").substringAfter("/")
            val titleText = link.text().trim()
            if (seriesId.isNotBlank() && seriesId != href) {
                val score = TitleMatcher.matchScore(seriesTitle, titleText, seriesSlug)
                if (score > 0) {
                    candidates.add(Triple(seriesId, titleText, score))
                }
            }
        }

        val sortedCandidates = candidates.distinctBy { it.first }.sortedByDescending { it.third }
        val targetNum = chapterNum.trim()

        for (cand in sortedCandidates.take(3)) {
            val seriesId = cand.first
            val chListUrl = "https://weebcentral.com/series/$seriesId/full-chapter-list"
            val chListReq = Request.Builder().url(chListUrl).build()
            val chListHtml = runCatching {
                NetworkClient.okHttp.newCall(chListReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull() ?: continue

            val chDoc = Jsoup.parse(chListHtml, chListUrl)
            var targetChapterId: String? = null

            for (link in chDoc.select("a[href*='/chapters/']")) {
                val text = link.text()
                val href = link.attr("href")
                val match = Regex("(?:chapter|ch|episode|ep)[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $href")
                if (match != null) {
                    val foundNum = match.groupValues[1]
                    if (foundNum == targetNum || foundNum.toFloatOrNull() == targetNum.toFloatOrNull()) {
                        targetChapterId = href.substringAfter("/chapters/").substringBefore("/")
                        break
                    }
                }
            }

            if (!targetChapterId.isNullOrBlank()) {
                val imgPageUrl = "https://weebcentral.com/chapters/$targetChapterId/images?is_prev=False&current_page=1&reading_style=long_strip"
                val imgReq = Request.Builder().url(imgPageUrl).build()
                val imgHtml = runCatching {
                    NetworkClient.okHttp.newCall(imgReq).execute().use { it.body?.string().orEmpty() }
                }.getOrNull() ?: continue

                val imgDoc = Jsoup.parse(imgHtml, imgPageUrl)
                val images = mutableListOf<String>()
                for (img in imgDoc.select("img[src], img[data-src]")) {
                    val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
                    if (src.isNotBlank() && !src.contains("placeholder") && !src.contains("logo") && !src.contains("favicon")) {
                        images.add(src)
                    }
                }
                if (images.isNotEmpty()) {
                    return images
                }
            }
        }

        return emptyList()
    }

    fun fetchLiveChapters(seriesTitle: String, seriesSlug: String): List<Chapter> {
        val queryText = TitleMatcher.cleanSearchQuery(seriesTitle)
        val encodedQuery = URLEncoder.encode(queryText.ifBlank { seriesTitle }, "UTF-8")
        val searchUrl = "https://weebcentral.com/search/data?author=&text=$encodedQuery&sort=Best+Match&order=Ascending&official=Any&display_mode=Full+Display"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)
        val candidates = mutableListOf<Triple<String, String, Int>>()

        for (link in doc.select("a[href*='/series/']")) {
            val href = link.attr("href")
            val seriesId = href.substringAfter("/series/").substringBefore("/")
            val seriesSlugHref = href.substringAfter("/series/").substringAfter("/")
            val titleText = link.text().trim()
            if (seriesId.isNotBlank() && seriesId != href) {
                val score = TitleMatcher.matchScore(seriesTitle, titleText, seriesSlugHref)
                if (score > 0) {
                    candidates.add(Triple(seriesId, titleText, score))
                }
            }
        }

        val sorted = candidates.distinctBy { it.first }.sortedByDescending { it.third }
        for (cand in sorted.take(3)) {
            val bestSeriesId = cand.first
            val chListUrl = "https://weebcentral.com/series/$bestSeriesId/full-chapter-list"
            val chListReq = Request.Builder().url(chListUrl).build()
            val chListHtml = runCatching {
                NetworkClient.okHttp.newCall(chListReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull() ?: continue

            val chDoc = Jsoup.parse(chListHtml, chListUrl)
            val chapters = mutableListOf<Chapter>()

            for (link in chDoc.select("a[href*='/chapters/']")) {
                val text = link.text()
                val href = link.attr("href")
                val match = Regex("(?:chapter|ch|episode|ep)[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $href")
                if (match != null) {
                    val numStr = match.groupValues[1]
                    val chId = href.substringAfter("/chapters/").substringBefore("/")
                    chapters.add(
                        Chapter(
                            title = "Chapter $numStr",
                            slug = "$seriesSlug-ch-$chId",
                            number = numStr
                        )
                    )
                }
            }
            if (chapters.isNotEmpty()) {
                return chapters.distinctBy { it.number }.sortedWith { a, b ->
                    val aNum = a.number.toFloatOrNull() ?: 0f
                    val bNum = b.number.toFloatOrNull() ?: 0f
                    aNum.compareTo(bNum)
                }
            }
        }

        return emptyList()
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
        val candidates = mutableListOf<Triple<String, String, Int>>()

        for (link in mangaLinks) {
            val href = link.absUrl("href").trimEnd('/')
            if (href.contains("/chapter")) continue
            val candidateTitle = link.text().trim()
            val slugFromHref = href.substringAfterLast("/")
            val score = TitleMatcher.matchScore(seriesTitle, candidateTitle, slugFromHref)
            if (score > 0) {
                candidates.add(Triple(href, candidateTitle, score))
            }
        }

        val sortedCandidates = candidates.distinctBy { it.first }.sortedByDescending { it.third }
        if (sortedCandidates.isEmpty()) return emptyList()

        val cleanChNum = chapterNum.trim()

        for (cand in sortedCandidates.take(3)) {
            val matchedMangaUrl = cand.first
            val targetChapterUrl = "$matchedMangaUrl/chapter-$cleanChNum/"

            var chapterReq = Request.Builder().url(targetChapterUrl).build()
            var chapterHtml = runCatching {
                NetworkClient.okHttp.newCall(chapterReq).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
            }.getOrNull().orEmpty()

            // Fallback to manga page to extract chapter link if direct URL didn't resolve
            if (chapterHtml.isBlank()) {
                val baseMangaUrl = matchedMangaUrl.trimEnd('/')
                val ajaxReq = Request.Builder()
                    .url("$baseMangaUrl/ajax/chapters/")
                    .post(ByteArray(0).toRequestBody())
                    .header("Referer", "$baseMangaUrl/")
                    .build()
                var ajaxHtml = runCatching {
                    NetworkClient.okHttp.newCall(ajaxReq).execute().use { it.body?.string().orEmpty() }
                }.getOrNull().orEmpty()

                if (ajaxHtml.isBlank()) {
                    val mangaReq = Request.Builder().url(baseMangaUrl).build()
                    ajaxHtml = runCatching {
                        NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
                    }.getOrNull().orEmpty()
                }

                if (ajaxHtml.isNotBlank()) {
                    val mangaDoc = Jsoup.parse(ajaxHtml, baseMangaUrl)
                    val chLinks = mangaDoc.select(".wp-manga-chapter a, #manga-chapters-holder a, li.sub-chap a")
                    for (link in chLinks) {
                        val absHref = link.absUrl("href").trimEnd('/')
                        if (!absHref.startsWith(baseMangaUrl, ignoreCase = true)) continue
                        val text = link.text()
                        val match = Regex("chapter[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $absHref")
                        if (match != null) {
                            val num = match.groupValues[1]
                            if (num == cleanChNum || num.toFloatOrNull() == cleanChNum.toFloatOrNull()) {
                                chapterReq = Request.Builder().url(absHref).build()
                                chapterHtml = runCatching {
                                    NetworkClient.okHttp.newCall(chapterReq).execute().use { it.body?.string().orEmpty() }
                                }.getOrNull().orEmpty()
                                break
                            }
                        }
                    }
                }
            }

            if (chapterHtml.isNotBlank()) {
                val chapterDoc = Jsoup.parse(chapterHtml, targetChapterUrl)
                val images = mutableListOf<String>()
                val imgElements = chapterDoc.select(".reading-content img, #readerarea img, .entry-content img, img[src*='manga_']")
                for (img in imgElements) {
                    val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
                    if (src.isNotBlank() && !src.contains("LOGO") && !src.contains("wp-content/uploads") && !src.contains("placeholder")) {
                        images.add(src)
                    }
                }
                if (images.isNotEmpty()) {
                    return images
                }
            }
        }

        return emptyList()
    }

    fun fetchLiveChapters(seriesTitle: String, seriesSlug: String): List<Chapter> {
        val queryText = TitleMatcher.cleanSearchQuery(seriesTitle)
        val encodedQuery = URLEncoder.encode(queryText.ifBlank { seriesTitle }, "UTF-8")
        val searchUrl = "https://manhwatop.com/?s=$encodedQuery&post_type=wp-manga"

        val searchReq = Request.Builder().url(searchUrl).build()
        val searchHtml = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(searchHtml, searchUrl)
        val mangaLinks = doc.select(".c-tabs-item__content a[href*='/manga/'], .post-title a[href*='/manga/'], a[href*='/manga/']")
        val candidates = mutableListOf<Pair<String, Int>>()

        for (link in mangaLinks) {
            val href = link.absUrl("href").trimEnd('/')
            if (href.contains("/chapter")) continue
            val candidateTitle = link.text().trim()
            val slugFromHref = href.substringAfterLast("/")
            val score = TitleMatcher.matchScore(seriesTitle, candidateTitle, slugFromHref)
            if (score > 0 && href.isNotBlank()) {
                candidates.add(Pair(href, score))
            }
        }

        val sorted = candidates.distinctBy { it.first }.sortedByDescending { it.second }
        for (cand in sorted.take(3)) {
            val baseMangaUrl = cand.first.trimEnd('/')
            val ajaxReq = Request.Builder()
                .url("$baseMangaUrl/ajax/chapters/")
                .post(ByteArray(0).toRequestBody())
                .header("Referer", "$baseMangaUrl/")
                .build()
            var chaptersHtml = runCatching {
                NetworkClient.okHttp.newCall(ajaxReq).execute().use { it.body?.string().orEmpty() }
            }.getOrNull().orEmpty()

            if (chaptersHtml.isBlank()) {
                val mangaReq = Request.Builder().url(baseMangaUrl).build()
                chaptersHtml = runCatching {
                    NetworkClient.okHttp.newCall(mangaReq).execute().use { it.body?.string().orEmpty() }
                }.getOrNull().orEmpty()
            }

            if (chaptersHtml.isBlank()) continue

            val mangaDoc = Jsoup.parse(chaptersHtml, baseMangaUrl)
            val chLinks = mangaDoc.select(".wp-manga-chapter a, #manga-chapters-holder a, li.sub-chap a")
            val chapters = mutableListOf<Chapter>()
            val seen = mutableSetOf<String>()
            for (link in chLinks) {
                val absHref = link.absUrl("href").trimEnd('/')
                if (!absHref.startsWith(baseMangaUrl, ignoreCase = true)) continue

                val text = link.text()
                val match = Regex("chapter[- ]+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find("$text $absHref")
                if (match != null) {
                    val num = match.groupValues[1]
                    if (seen.add(num)) {
                        chapters.add(
                            Chapter(
                                title = "Chapter $num",
                                slug = "$seriesSlug-chapter-$num",
                                number = num
                            )
                        )
                    }
                }
            }
            if (chapters.isNotEmpty()) return chapters
        }

        return emptyList()
    }
}

object MangaDexSource {
    fun fetchChapterImages(seriesTitle: String, chapterNum: String, dataSaver: Boolean = true): List<String> {
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

        val files = if (dataSaver) (dataSaverFiles ?: regularFiles) else (regularFiles ?: dataSaverFiles)
        if (files == null) return emptyList()
        val subfolder = if (files === dataSaverFiles) "data-saver" else "data"

        val images = mutableListOf<String>()
        for (i in 0 until files.length()) {
            val filename = files.getString(i)
            images.add("$baseUrl/$subfolder/$hash/$filename")
        }
        return images
    }

    fun fetchLiveChapters(seriesTitle: String, seriesSlug: String): List<Chapter> {
        val encodedTitle = URLEncoder.encode(seriesTitle, "UTF-8")
        val searchUrl = "https://api.mangadex.org/manga?title=$encodedTitle&limit=5&order[relevance]=desc"
        val searchReq = Request.Builder().url(searchUrl).build()
        val searchJsonStr = runCatching {
            NetworkClient.okHttp.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return emptyList()

        val searchJson = runCatching { JSONObject(searchJsonStr) }.getOrNull() ?: return emptyList()
        val dataArray = searchJson.optJSONArray("data") ?: return emptyList()
        var matchedMangaId: String? = null
        for (i in 0 until dataArray.length()) {
            val mangaObj = dataArray.getJSONObject(i)
            val attrs = mangaObj.getJSONObject("attributes")
            val titles = attrs.getJSONObject("title")
            val candidates = mutableListOf<String>()
            val keys = titles.keys()
            while (keys.hasNext()) candidates.add(titles.getString(keys.next()))
            val altTitles = attrs.optJSONArray("altTitles")
            if (altTitles != null) {
                for (j in 0 until altTitles.length()) {
                    val altObj = altTitles.getJSONObject(j)
                    val altKeys = altObj.keys()
                    while (altKeys.hasNext()) candidates.add(altObj.getString(altKeys.next()))
                }
            }
            if (candidates.any { TitleMatcher.titlesMatch(seriesTitle, it) }) {
                matchedMangaId = mangaObj.getString("id")
                break
            }
        }
        if (matchedMangaId.isNullOrBlank()) return emptyList()

        val feedUrl = "https://api.mangadex.org/manga/$matchedMangaId/feed?limit=500&order[chapter]=desc&translatedLanguage[]=en"
        val feedReq = Request.Builder().url(feedUrl).build()
        val feedStr = runCatching {
            NetworkClient.okHttp.newCall(feedReq).execute().use { it.body?.string().orEmpty() }
        }.getOrNull().orEmpty()
        if (feedStr.isBlank()) return emptyList()

        val feedJson = runCatching { JSONObject(feedStr) }.getOrNull() ?: return emptyList()
        val feedData = feedJson.optJSONArray("data") ?: return emptyList()
        val chapters = mutableListOf<Chapter>()
        val seenNums = mutableSetOf<String>()
        for (i in 0 until feedData.length()) {
            val chObj = feedData.getJSONObject(i)
            val chAttrs = chObj.getJSONObject("attributes")
            val chNum = chAttrs.optString("chapter").trim()
            val pages = chAttrs.optInt("pages", 0)
            if (chNum.isNotBlank() && pages > 0 && seenNums.add(chNum)) {
                chapters.add(
                    Chapter(
                        title = "Chapter $chNum",
                        slug = "$seriesSlug-chapter-$chNum",
                        number = chNum
                    )
                )
            }
        }
        return chapters
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
        context: Context?,
        group: String,
        type: String,
        seriesTitle: String,
        englishName: String?,
        nativeName: String?,
        chapterNum: String,
        chapterSlug: String,
        dataSaver: Boolean = true
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
        val isManhwaOrManhua = type.equals("Manhwa", ignoreCase = true) || type.equals("Manhua", ignoreCase = true)

        if (isManhwaOrManhua) {
            for (title in titlesToTry) {
                val wcImages = runCatching { WeebCentralSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!wcImages.isNullOrEmpty()) return wcImages
            }
            for (title in titlesToTry) {
                val mwImages = runCatching { ManhwaSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mwImages.isNullOrEmpty()) return mwImages
            }
            for (title in titlesToTry) {
                val mpImages = runCatching { MangaPillSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mpImages.isNullOrEmpty()) return mpImages
            }
            for (title in titlesToTry) {
                val mdxImages = runCatching { MangaDexSource.fetchChapterImages(title, chapterNum, dataSaver) }.getOrNull()
                if (!mdxImages.isNullOrEmpty()) return mdxImages
            }
        } else {
            for (title in titlesToTry) {
                val mpImages = runCatching { MangaPillSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mpImages.isNullOrEmpty()) return mpImages
            }
            for (title in titlesToTry) {
                val wcImages = runCatching { WeebCentralSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!wcImages.isNullOrEmpty()) return wcImages
            }
            for (title in titlesToTry) {
                val mwImages = runCatching { ManhwaSource.fetchChapterImages(title, chapterNum) }.getOrNull()
                if (!mwImages.isNullOrEmpty()) return mwImages
            }
            for (title in titlesToTry) {
                val mdxImages = runCatching { MangaDexSource.fetchChapterImages(title, chapterNum, dataSaver) }.getOrNull()
                if (!mdxImages.isNullOrEmpty()) return mdxImages
            }
        }

        // Fallback: mkissa.to (AllManga) via WebView for AllManga-sourced series
        if (context != null && group.equals("AllManga", ignoreCase = true)) {
            val mkImages = runCatching {
                val mangaId = MkissaSource.findMangaId(seriesTitle)
                if (mangaId != null) {
                    MkissaSource.fetchChapterImages(context, mangaId, chapterNum)
                } else null
            }.getOrNull()
            if (!mkImages.isNullOrEmpty()) return mkImages
        }

        return emptyList()
    }

    fun fetchLiveChapters(
        group: String,
        type: String,
        seriesSlug: String,
        seriesTitle: String,
        englishName: String?,
        nativeName: String?,
        currentMaxChapter: Float = 0f
    ): List<Chapter> {
        val titlesToTry = listOfNotNull(
            seriesTitle,
            englishName,
            TitleMatcher.cleanSearchQuery(seriesTitle),
            nativeName
        ).distinct()
        val isManhwaOrManhua = type.equals("Manhwa", ignoreCase = true) || type.equals("Manhua", ignoreCase = true)

        val sources: List<(String, String) -> List<Chapter>> = if (isManhwaOrManhua) {
            listOf(
                { t, s -> WeebCentralSource.fetchLiveChapters(t, s) },
                { t, s -> ManhwaSource.fetchLiveChapters(t, s) },
                { t, s -> MangaPillSource.fetchLiveChapters(t, s) },
                { t, s -> MangaDexSource.fetchLiveChapters(t, s) }
            )
        } else {
            listOf(
                { t, s -> WeebCentralSource.fetchLiveChapters(t, s) },
                { t, s -> MangaPillSource.fetchLiveChapters(t, s) },
                { t, s -> MangaDexSource.fetchLiveChapters(t, s) },
                { t, s -> ManhwaSource.fetchLiveChapters(t, s) }
            )
        }

        var fallbackChapters = emptyList<Chapter>()
        var fallbackMax = 0f

        for (source in sources) {
            for (title in titlesToTry) {
                val chs = runCatching { source(title, seriesSlug) }.getOrNull()
                if (!chs.isNullOrEmpty()) {
                    val maxNum = chs.mapNotNull { it.number.toFloatOrNull() }.maxOrNull() ?: 0f
                    if (maxNum > currentMaxChapter) {
                        return chs
                    }
                    if (maxNum > fallbackMax || fallbackChapters.isEmpty()) {
                        fallbackMax = maxNum
                        fallbackChapters = chs
                    }
                }
            }
        }

        return fallbackChapters
    }
}
