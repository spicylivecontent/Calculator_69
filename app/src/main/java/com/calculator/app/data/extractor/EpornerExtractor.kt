package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class EpornerExtractor : SiteExtractor {

    override val siteName = "eporner"

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.eporner.com/search/$encoded/$page/"

        val request = Request.Builder().url(url).build()

        try {
            NetworkClient.client.newCall(request).execute().use { response ->
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
}
