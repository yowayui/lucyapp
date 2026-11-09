package app.lucy.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// ponytail: WebView-based chapter image resolver for mkissa.to (AllManga).
// Uses the same technique as keiyoushi/Tachiyomi: intercept JSON.parse to
// capture the chapterPages GraphQL response that requires AA_CRYPTO browser crypto.
// Upgrade path: if AllManga drops AA_CRYPTO, switch to direct GraphQL queries.
object MkissaSource {

    private const val BASE_URL = "https://mkissa.to"
    private const val API_URL = "https://api.mkissa.net/api"
    private const val TIMEOUT_SECONDS = 25L

    fun fetchChapterImages(
        context: Context,
        mangaId: String,
        chapterNum: String
    ): List<String> {
        val result = AtomicReference<List<String>>(emptyList())
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            runWebView(context, mangaId, chapterNum) { images ->
                result.set(images)
                latch.countDown()
            }
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return result.get()
    }

    // Search AllManga GraphQL for a manga ID by title (no AA_CRYPTO needed for search)
    fun findMangaId(title: String): String? {
        val query = """
            query(${'$'}search: SearchInput, ${'$'}limit: Int, ${'$'}page: Int, ${'$'}translationType: VaildTranslationTypeMangaEnumType, ${'$'}countryOrigin: VaildCountryOriginEnumType) {
                mangas(search: ${'$'}search, limit: ${'$'}limit, page: ${'$'}page, translationType: ${'$'}translationType, countryOrigin: ${'$'}countryOrigin) {
                    edges { _id name englishName nativeName }
                }
            }
        """.trimIndent()
        val variables = JSONObject().apply {
            put("search", JSONObject().put("query", title))
            put("limit", 10)
            put("page", 1)
            put("translationType", "sub")
            put("countryOrigin", "ALL")
        }
        val payload = JSONObject().apply {
            put("query", query)
            put("variables", variables)
        }

        val req = Request.Builder()
            .url(API_URL)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Referer", "$BASE_URL/")
            .build()

        val body = runCatching {
            NetworkClient.okHttp.newCall(req).execute().use { it.body?.string() }
        }.getOrNull() ?: return null

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val edges = json.optJSONObject("data")
            ?.optJSONObject("mangas")
            ?.optJSONArray("edges") ?: return null

        var bestId: String? = null
        var bestScore = 0

        for (i in 0 until edges.length()) {
            val edge = edges.getJSONObject(i)
            val id = edge.optString("_id")
            val name = edge.optString("name", "")
            val eng = edge.optString("englishName", "")
            val nat = edge.optString("nativeName", "")

            for (candidate in listOf(name, eng, nat)) {
                if (candidate.isBlank()) continue
                val score = TitleMatcher.matchScore(title, candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestId = id
                }
            }
        }

        return if (bestScore >= 40) bestId else null
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun runWebView(
        context: Context,
        mangaId: String,
        chapterNum: String,
        onResult: (List<String>) -> Unit
    ) {
        val interfaceName = (1..15).map { ('a'..'z').random() }.joinToString("")
        val mangaUrl = "$BASE_URL/manga/$mangaId?fromSearch=1"
        val chapterPath = "/manga/$mangaId/chapter-$chapterNum-sub"
        var resolved = false

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            settings.blockNetworkImage = true
        }

        val bridge = object {
            @JavascriptInterface
            fun postMessage(data: String) {
                if (resolved) return
                val images = parseChapterPages(data)
                if (images.isNotEmpty()) {
                    resolved = true
                    handler(wv) {
                        wv.stopLoading()
                        wv.destroy()
                    }
                    onResult(images)
                }
            }
        }

        wv.addJavascriptInterface(bridge, interfaceName)

        val hookScript = """
            (function() {
                const origJson = Response.prototype.json;
                Response.prototype.json = function() {
                    return origJson.call(this).then(function(data) {
                        if (data && data.data && data.data.chapterPages) {
                            window['$interfaceName'].postMessage(JSON.stringify(data));
                        }
                        return data;
                    });
                };
                const origParse = JSON.parse;
                JSON.parse = new Proxy(origParse, {
                    apply: function(target, thisArg, args) {
                        const result = Reflect.apply(target, thisArg, args);
                        if (result && result.data && result.data.chapterPages) {
                            window['$interfaceName'].postMessage(String(args[0]));
                        }
                        return result;
                    }
                });
                const hook = function(e) {
                    if (e.tagName && e.tagName.toUpperCase() === 'IFRAME') {
                        Object.defineProperty(e, 'contentWindow', {
                            get: function() { return null; },
                            configurable: false
                        });
                    }
                    return e;
                };
                var origCreate = Document.prototype.createElement;
                Document.prototype.createElement = function() {
                    return hook(origCreate.apply(this, arguments));
                };
            })();
        """.trimIndent()

        val navScript = """
            (function() {
                function triggerNav() {
                    var a = document.createElement('a');
                    a.href = a.dataset.href = '$chapterPath';
                    document.body.appendChild(a);
                    a.click();
                }
                var attempts = 0;
                function check() {
                    if (document.querySelector('[data-href]') || attempts > 150) {
                        triggerNav();
                    } else {
                        attempts++;
                        setTimeout(check, 100);
                    }
                }
                check();
            })();
        """.trimIndent()

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                view?.evaluateJavascript("localStorage.clear();sessionStorage.clear();", null)
                view?.evaluateJavascript(hookScript, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(navScript, null)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                // Block images, ads, tracking to speed things up
                if (url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".webp") ||
                    url.endsWith(".gif") || url.endsWith(".svg") ||
                    url.contains("google-analytics") || url.contains("googletag") ||
                    url.contains("disqus") || url.contains("facebook") ||
                    url.contains("/mreferer/")) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }

        wv.loadUrl(mangaUrl)

        // Timeout fallback
        handler(wv) {
            wv.postDelayed({
                if (!resolved) {
                    resolved = true
                    wv.stopLoading()
                    wv.destroy()
                    onResult(emptyList())
                }
            }, TIMEOUT_SECONDS * 1000)
        }
    }

    private fun handler(wv: WebView, action: () -> Unit) {
        wv.handler?.post(action) ?: Handler(Looper.getMainLooper()).post(action)
    }

    private fun parseChapterPages(jsonStr: String): List<String> {
        val json = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val edges = json.optJSONObject("data")
            ?.optJSONObject("chapterPages")
            ?.optJSONArray("edges") ?: return emptyList()

        for (i in 0 until edges.length()) {
            val server = edges.getJSONObject(i)
            val serverUrl = server.optString("pictureUrlHead", "")
            val pics = server.optJSONArray("pictureUrls") ?: continue
            if (pics.length() == 0) continue

            val domain = if (serverUrl.isNotBlank()) {
                val base = if (serverUrl.startsWith("http")) serverUrl else "https://$serverUrl"
                if (base.endsWith("/")) base else "$base/"
            } else {
                "https://wp.youtube-anime.com/"
            }

            val images = mutableListOf<String>()
            for (j in 0 until pics.length()) {
                val url = when (val item = pics.get(j)) {
                    is String -> item
                    is JSONObject -> item.optString("url", "")
                    else -> ""
                }
                if (url.isBlank()) continue

                val fullUrl = if (url.startsWith("http") || url.startsWith("//")) {
                    if (url.startsWith("//")) "https:$url" else url
                } else {
                    domain + url.removePrefix("/")
                }
                images.add(fullUrl)
            }
            if (images.isNotEmpty()) return images
        }

        return emptyList()
    }
}
