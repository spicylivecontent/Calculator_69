package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class PornTrexExtractor : SiteExtractor {
    override val siteName = "porntrex"

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.porntrex.com/search/$encoded/?mode=async&function=get_block&block_id=list_videos_videos_list_search_result&q=$encoded&from_videos=$page&from_block=$page"

        // Fallback enklare URL
        val simpleUrl = "https://www.porntrex.com/search/$encoded/"

        try {
            val request = Request.Builder().url(simpleUrl).build()
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
        for (item in doc.select("div.thumb, div.video-item, div.item")) {
            try {
                val a = item.selectFirst("a[href*=/video/]") ?: item.selectFirst("a") ?: continue
                val href = a.attr("href")
                if (href.isBlank()) continue
                val detailUrl = if (href.startsWith("http")) href else "https://www.porntrex.com$href"
                val id = href.substringAfter("/video/").substringBefore("/").ifBlank {
                    href.hashCode().toString()
                }
                val title = a.attr("title").ifBlank {
                    item.selectFirst(".title, strong")?.text()?.trim() ?: "Untitled"
                }
                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-original").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") }
                }
                val duration = item.selectFirst(".duration, .time")?.text()?.trim()
                results.add(
                    SearchResult(
                        id = "porntrex_$id",
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
