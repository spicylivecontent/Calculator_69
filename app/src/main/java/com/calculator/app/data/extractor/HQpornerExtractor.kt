package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class HQpornerExtractor : SiteExtractor {

    override val siteName = "hqporner"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // HQporner sök
        val url = "https://hqporner.com/?q=$encoded"

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

        val items = doc.select("div.box, div.thumb, article, div.video")

        for (item in items) {
            try {
                val linkEl = item.selectFirst("a[href]") ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val detailUrl = when {
                    href.startsWith("http") -> href
                    href.startsWith("//") -> "https:$href"
                    else -> "https://hqporner.com$href"
                }

                val id = href.trim('/').substringAfterLast('/').ifBlank { href.hashCode().toString() }

                val title = item.selectFirst("h3, .title, .meta, p")?.text()?.trim()
                    ?: linkEl.attr("title").trim().ifBlank { "Untitled" }

                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }

                val duration = item.selectFirst(".duration, .time, span")?.text()?.trim()

                results.add(
                    SearchResult(
                        id = "hqporner_$id",
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
