package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Enkel extractor för Eporner sökresultat.
 * Hämtar titel, thumbnail, längd och länk till detaljsidan.
 */
class EpornerExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(query: String, page: Int = 1): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.eporner.com/search/$encodedQuery/$page/"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val html = response.body?.string() ?: return@withContext emptyList()
                parseSearchResults(html)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseSearchResults(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // Eporner använder .mb för video-kort
        val items = doc.select("div.mb")

        for (item in items) {
            try {
                val linkEl = item.selectFirst("a") ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val detailUrl = if (href.startsWith("http")) href else "https://www.eporner.com$href"
                val id = href.trim('/').split('/').lastOrNull { it.isNotBlank() } ?: continue

                val title = item.selectFirst("p.mbtit")?.text()?.trim()
                    ?: item.selectFirst("a")?.attr("title")?.trim()
                    ?: "Untitled"

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
                        sourceSite = "eporner"
                    )
                )
            } catch (e: Exception) {
                // Hoppa över trasiga kort
            }
        }

        return results
    }
}
