package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class EpornerExtractor : SiteExtractor {

    override val siteName = "eporner"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.eporner.com/search/$encoded/$page/"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
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

        for (item in doc.select("div.mb")) {
            try {
                val linkEl = item.selectFirst("a") ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val detailUrl = if (href.startsWith("http")) href else "https://www.eporner.com$href"
                val id = href.trim('/').split('/').lastOrNull { it.isNotBlank() } ?: continue

                val title = item.selectFirst("p.mbtit")?.text()?.trim()
                    ?: linkEl.attr("title").trim().ifBlank { "Untitled" }

                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }

                val duration = item.selectFirst("span.mbtim")?.text()?.trim()

                results.add(
                    SearchResult(
                        id = "eporner_$id",
                        title = title,
                        thumbnailUrl = thumb,
                        duration = duration,
                        detailUrl = detailUrl,
                        sourceSite = siteName
                    )
                )
            } catch (_: Exception) {}
        }
        return results
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
