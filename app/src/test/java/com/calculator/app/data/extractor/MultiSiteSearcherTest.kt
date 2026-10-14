package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchQuery
import com.calculator.app.data.model.SearchResult
import com.calculator.app.network.NetworkCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MultiSiteSearcherTest {
    @Test
    fun partialSiteFailureIsReportedEvenWhenAnotherQueryReturnsResults() = runBlocking {
        val extractor = object : SiteExtractor {
            override val siteName = "example"
            override val baseUrl = "https://example.com"

            override suspend fun search(query: String, page: Int): List<SearchResult> {
                if (query == "second") throw IOException("variant failed")
                return listOf(
                    SearchResult(
                        id = "example-1",
                        title = "First result",
                        thumbnailUrl = null,
                        duration = null,
                        detailUrl = "https://example.com/video/1",
                        sourceSite = siteName
                    )
                )
            }
        }

        val outcome = MultiSiteSearcher(
            extractors = listOf(extractor),
            network = NetworkCondition.Default
        ).search(
            queries = listOf(
                SearchQuery("first", listOf("first"), isPrimary = true),
                SearchQuery("second", listOf("second"))
            )
        )

        assertEquals(1, outcome.results.size)
        val report = outcome.reports.single()
        assertEquals(1, report.count)
        assertFalse(report.ok)
        assertTrue(report.message.orEmpty().contains("variant failed"))
    }

    @Test
    fun offlineSearchReturnsImmediatelyWithoutCallingSites() = runBlocking {
        val extractor = object : SiteExtractor {
            override val siteName = "offline-test"
            override val baseUrl = "https://example.com"

            override suspend fun search(query: String, page: Int): List<SearchResult> {
                throw AssertionError("Extractor must not be called while offline")
            }
        }

        val outcome = MultiSiteSearcher(
            extractors = listOf(extractor),
            network = NetworkCondition(isMetered = false, isWifi = false, isOffline = true)
        ).search(queries = listOf(SearchQuery("query", listOf("query"))))

        assertTrue(outcome.results.isEmpty())
        val report = outcome.reports.single()
        assertEquals(0, report.count)
        assertFalse(report.ok)
        assertEquals("Ingen nätverksanslutning", report.message)
    }
}
