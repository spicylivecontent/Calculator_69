package com.calculator.app.data.extractor

import com.calculator.app.data.model.VideoMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Kortvarig minnescache för skrapad metadata.
 *
 * Sparar batteri och nät när samma detaljsida berikas igen kort därefter –
 * till exempel när en träff rullar in och ut ur "Fortsätt titta"-kön eller när
 * sökresultatet verifieras på nytt. Cachen är ren logik och helt inställbar
 * (TTL, storlek och klocka) så att den kan enhetstestas deterministiskt.
 *
 * @param ttlMs hur länge en post får ligga kvar.
 * @param maxEntries högsta antal poster; när taket nås rensas den äldsta fjärdedelen.
 * @param clock tidskälla, injicerbar i tester.
 */
class MetadataCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class Entry(val metadata: VideoMetadata, val storedAt: Long)

    private val capacity = maxEntries.coerceAtLeast(1)
    private val map = ConcurrentHashMap<String, Entry>()

    /** Returnerar cachad metadata om den fortfarande är färsk, annars null. */
    fun get(detailUrl: String): VideoMetadata? {
        val entry = map[detailUrl] ?: return null
        if (clock() - entry.storedAt > ttlMs) {
            // Do not delete a fresh value concurrently written for the same URL.
            map.remove(detailUrl, entry)
            val replacement = map[detailUrl] ?: return null
            if (clock() - replacement.storedAt > ttlMs) {
                map.remove(detailUrl, replacement)
                return null
            }
            return replacement.metadata
        }
        return entry.metadata
    }

    /** Lägger in (eller ersätter) metadata för en detaljsida. */
    @Synchronized
    fun put(detailUrl: String, metadata: VideoMetadata) {
        // Replacing a key does not increase the size, so do not evict unrelated entries.
        if (!map.containsKey(detailUrl)) {
            while (map.size >= capacity) {
                // Evict at least one item even for tiny capacities (1–3).
                val removeCount = maxOf(1, capacity / 4)
                val oldest = map.entries
                    .sortedBy { it.value.storedAt }
                    .take(removeCount)
                    .map { it.key }
                if (oldest.isEmpty()) break
                oldest.forEach { map.remove(it) }
            }
        }
        map[detailUrl] = Entry(metadata, clock())
    }

    fun clear() = map.clear()

    fun size(): Int = map.size

    companion object {
        /** ~20 minuter: tillräckligt länge för en söksession, kort nog för färsk data. */
        const val DEFAULT_TTL_MS = 20 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 256

        /** Delad instans som [MetadataExtractor] använder. */
        val Default = MetadataCache()
    }
}
