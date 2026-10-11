package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class SpankBangExtractor : SiteExtractor {

    override val siteName = "spankbang"

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://spankbang.com/s/$encoded/$page/"

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

        val items = doc.select("div.video-item, div.thumb, div.video")

        for (item in items) {
            try {
                val linkEl = item.selectFirst("a[href*=/video/], a[href*=/playlist/]")
                    ?: item.selectFirst("a") ?: continue
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
