package com.calculator.app.data.extractor

import com.calculator.app.data.model.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Ren logik med injicerad klocka: TTL, ersättning och rensning testas
 * deterministiskt utan att vänta ut riktiga tidsgränser.
 */
class MetadataCacheTest {

    private var now = 1_000L
    private val cache = MetadataCache(ttlMs = 20_000L, maxEntries = 8, clock = { now })

    private fun metadata(url: String, title: String = "Titel") =
        VideoMetadata(detailUrl = url, title = title)

    @Test
    fun freshEntryIsReturnedAndExpiredEntryIsDropped() {
        cache.put("https://site.example/a", metadata("https://site.example/a"))
        now += 19_999
        assertNotNull("färsk post ska finnas", cache.get("https://site.example/a"))

        now += 2
        assertNull("utgången post ska rensas", cache.get("https://site.example/a"))
        assertEquals(0, cache.size())
    }

    @Test
    fun latestWriteWinsForSameUrl() {
        cache.put("https://site.example/a", metadata("https://site.example/a", "första"))
        cache.put("https://site.example/a", metadata("https://site.example/a", "andra"))

        assertEquals("andra", cache.get("https://site.example/a")?.title)
        assertEquals(1, cache.size())
    }

    @Test
    fun expiredEntryIsDroppedWhileANewerOneRemains() {
        cache.put("https://site.example/a", metadata("https://site.example/a"))
        now += 25_000
        cache.put("https://site.example/b", metadata("https://site.example/b"))

        assertNull(cache.get("https://site.example/a"))
        assertNotNull(cache.get("https://site.example/b"))
    }

    @Test
    fun evictsOldestQuarterWhenFull() {
        repeat(8) { i ->
            cache.put("https://site.example/$i", metadata("https://site.example/$i"))
            now += 10
        }
        assertEquals(8, cache.size())

        cache.put("https://site.example/ny", metadata("https://site.example/ny"))

        // Taket är 8: den äldsta fjärdedelen (2 poster) rensas före insättningen.
        assertEquals(7, cache.size())
        assertNull("äldsta posten rensas först", cache.get("https://site.example/0"))
        assertNull("näst äldsta posten rensas också", cache.get("https://site.example/1"))
        assertNotNull(cache.get("https://site.example/2"))
        assertNotNull(cache.get("https://site.example/ny"))
    }

    @Test
    fun clearEmptiesTheCache() {
        cache.put("https://site.example/a", metadata("https://site.example/a"))
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("https://site.example/a"))
    }
}
