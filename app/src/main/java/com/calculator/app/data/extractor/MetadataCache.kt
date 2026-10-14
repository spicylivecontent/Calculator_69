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

    private val map = ConcurrentHashMap<String, Entry>()

    /** Returnerar cachad metadata om den fortfarande är färsk, annars null. */
    fun get(detailUrl: String): VideoMetadata? {
        val entry = map[detailUrl] ?: return null
        if (clock() - entry.storedAt > ttlMs) {
            map.remove(detailUrl)
            return null
        }
        return entry.metadata
    }

    /** Lägger in (eller ersätter) metadata för en detaljsida. */
    fun put(detailUrl: String, metadata: VideoMetadata) {
        if (map.size >= maxEntries) {
            // Rensa den äldsta fjärdedelen i stället för en enda post, så att taket
            // inte nås igen direkt efter varje ny insättning.
            map.entries
                .sortedBy { it.value.storedAt }
                .take(maxEntries / 4)
                .forEach { map.remove(it.key) }
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
