package com.calculator.app.data.extractor

/**
 * Small, thread-safe LRU cache for resolved media streams.
 *
 * Expiration and eviction are performed under the same lock so a stale read cannot
 * remove a newer replacement for the same detail URL. Capacity is always positive.
 */
internal class StreamCache(
    private val ttlMs: Long,
    maxEntries: Int,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val stream: ResolvedStream, val expiresAt: Long)

    private val capacity = maxEntries.coerceAtLeast(1)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): ResolvedStream? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= clock()) {
            entries.remove(key)
            return null
        }
        return entry.stream
    }

    @Synchronized
    fun put(key: String, stream: ResolvedStream) {
        entries.remove(key)
        entries[key] = Entry(stream, clock() + ttlMs.coerceAtLeast(0L))
        while (entries.size > capacity) {
            val oldestKey = entries.entries.iterator().let { iterator ->
                if (iterator.hasNext()) iterator.next().key else null
            } ?: break
            entries.remove(oldestKey)
        }
    }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    /** Removes only the value observed by the caller, not a concurrent replacement. */
    @Synchronized
    fun remove(key: String, expected: ResolvedStream) {
        if (entries[key]?.stream == expected) entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
