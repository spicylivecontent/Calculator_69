package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCacheTest {
    private var now = 1_000L

    private fun stream(label: String) = ResolvedStream(
        url = "https://cdn.example/$label.mp4",
        referer = "https://site.example/video/$label",
        isHls = false,
        maxHeight = 720,
        bandwidth = null,
        qualityLabel = label
    )

    @Test
    fun cacheNeverExceedsConfiguredCapacityAndEvictsLeastRecentlyUsed() {
        val cache = StreamCache(ttlMs = 10_000L, maxEntries = 2, clock = { now })
        val first = stream("first")
        val second = stream("second")
        val third = stream("third")

        cache.put("a", first)
        cache.put("b", second)
        assertSame(first, cache.get("a")) // a is now most recently used
        cache.put("c", third)

        assertEquals(2, cache.size())
        assertSame(first, cache.get("a"))
        assertNull(cache.get("b"))
        assertSame(third, cache.get("c"))
    }

    @Test
    fun expiredEntryIsRemovedOnRead() {
        val cache = StreamCache(ttlMs = 100L, maxEntries = 5, clock = { now })
        cache.put("a", stream("a"))
        now += 100L

        assertNull(cache.get("a"))
        assertEquals(0, cache.size())
    }

    @Test
    fun nonPositiveCapacityIsClampedToOne() {
        val cache = StreamCache(ttlMs = 10_000L, maxEntries = 0, clock = { now })
        cache.put("a", stream("a"))
        cache.put("b", stream("b"))

        assertEquals(1, cache.size())
        assertNull(cache.get("a"))
        assertTrue(cache.get("b") != null)
    }

    @Test
    fun replacingKeyDoesNotIncreaseCacheSize() {
        val cache = StreamCache(ttlMs = 10_000L, maxEntries = 2, clock = { now })
        cache.put("a", stream("old"))
        cache.put("b", stream("b"))
        val replacement = stream("new")
        cache.put("a", replacement)

        assertEquals(2, cache.size())
        assertSame(replacement, cache.get("a"))
        assertTrue(cache.get("b") != null)
    }

    @Test
    fun staleProbeCannotRemoveConcurrentReplacement() {
        val cache = StreamCache(ttlMs = 10_000L, maxEntries = 2, clock = { now })
        val old = stream("old")
        val replacement = stream("replacement")
        cache.put("a", old)
        cache.put("a", replacement)

        cache.remove("a", old)

        assertSame(replacement, cache.get("a"))
    }
}
