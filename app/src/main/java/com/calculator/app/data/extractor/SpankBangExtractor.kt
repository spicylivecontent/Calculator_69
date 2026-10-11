package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class SpankBangExtractor : SiteExtractor {

    override val siteName = "spankbang"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://spankbang.com/s/$encoded/$page/"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", EpornerExtractor.USER_AGENT)
            .header("Accept", "text/html")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: return@withContext emptyList()
                parse(html)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parse(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // SpankBang video cards
        val items = doc.select("div.video-item, div.thumb, div.video")

        for (item in items) {
            try {
                val linkEl = item.selectFirst("a[href*=/video/], a[href*=/playlist/]") ?: item.selectFirst("a") ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank() || href.contains("javascript")) continue

                val detailUrl = when {
                    href.startsWith("http") -> href
                    href.startsWith("//") -> "https:$href"
                    else -> "https://spankbang.com$href"
                }

                val idPart = href.trim('/').substringAfterLast('/')
                if (idPart.isBlank()) continue

                val title = item.selectFirst("span.name, .name, .title, p")?.text()?.trim()
                    ?: linkEl.attr("title").trim().ifBlank { "Untitled" }

                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("data-original") }.ifBlank { it.attr("src") }
                }

                val duration = item.selectFirst("span.length, .l, .duration")?.text()?.trim()

                results.add(
                    SearchResult(
                        id = "spankbang_$idPart",
                        title = title,
                        thumbnailUrl = thumb,
                        duration = duration,
                        detailUrl = detailUrl,
                        sourceSite = siteName
                    )
                )
            } catch (_: Exception) {}
        }
        return results.distinctBy { it.id }.take(40)
    }
}
