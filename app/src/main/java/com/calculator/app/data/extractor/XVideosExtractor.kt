package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class XVideosExtractor : SiteExtractor {
    override val siteName = "xvideos"

    override suspend fun search(query: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = if (page <= 1) "https://www.xvideos.com/?k=$encoded"
        else "https://www.xvideos.com/?k=$encoded&p=${page - 1}"

        try {
            val request = Request.Builder().url(url).build()
            NetworkClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: return@withContext emptyList()
                parse(html)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()
        for (item in doc.select("div.thumb-block, div.mozaique > div")) {
            try {
                val a = item.selectFirst("a[href*=/video]") ?: continue
                val href = a.attr("href")
                if (href.isBlank()) continue
                val detailUrl = if (href.startsWith("http")) href else "https://www.xvideos.com$href"
                val id = href.substringAfter("/video").substringBefore("/").trim('/')
                    .ifBlank { href.hashCode().toString() }
                val title = a.attr("title").ifBlank {
                    item.selectFirst("p.title, .title")?.text()?.trim() ?: "Untitled"
                }
                val thumb = item.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
                val duration = item.selectFirst("span.duration")?.text()?.trim()
                results.add(
                    SearchResult(
                        id = "xvideos_$id",
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
