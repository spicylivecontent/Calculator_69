package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class BeegExtractor : SiteExtractor {
    override val siteName = "beeg"

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://beeg.com/search?q=$encoded"

        try {
            val request = Request.Builder().url(url).build()
            NetworkClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: return@withContext emptyList()
                parse(html)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()
        for (item in doc.select("a[href^=/], div.thumb, article")) {
            try {
                val href = if (item.tagName() == "a") item.attr("href") else item.selectFirst("a")?.attr("href") ?: continue
                if (href.isBlank() || href.length < 3) continue
                // Beeg video links are often /number
                if (!href.matches(Regex("/\\d+.*")) && !href.contains("video")) continue
                val detailUrl = if (href.startsWith("http")) href else "https://beeg.com$href"
                val id = href.trim('/').substringBefore("/").ifBlank { href.hashCode().toString() }
                val title = item.attr("title").ifBlank {
                    item.selectFirst(".title, span")?.text()?.trim() ?: "Untitled"
                }
                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
                results.add(
                    SearchResult(
                        id = "beeg_$id",
                        title = title,
                        thumbnailUrl = thumb,
                        duration = null,
                        detailUrl = detailUrl,
                        sourceSite = siteName
                    )
                )
            } catch (_: Exception) {}
        }
        return results.distinctBy { it.id }.take(30)
    }
}
