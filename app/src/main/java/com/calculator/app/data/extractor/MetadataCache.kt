package com.calculator.app.data.extractor

import com.calculator.app.data.model.VideoMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Kortvarig minnescache för skrapad metadata (TTL ~20 min).
 * Sparar batteri och nät när samma detaljsida berikas igen.
 */
object MetadataCache {

    private const val TTL_MS = 20 * 60 * 1000L
    private const val MAX_ENTRIES = 256

    private data class Entry(val metadata: VideoMetadata, val storedAt: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun get(detailUrl: String): VideoMetadata? {
        val entry = map[detailUrl] ?: return null
        if (System.currentTimeMillis() - entry.storedAt > TTL_MS) {
            map.remove(detailUrl)
            return null
        }
        return entry.metadata
    }

    fun put(detailUrl: String, metadata: VideoMetadata) {
        if (map.size >= MAX_ENTRIES) {
            val sorted = map.entries.sortedBy { it.value.storedAt }
            sorted.take(MAX_ENTRIES / 4).forEach { map.remove(it.key) }
        }
        map[detailUrl] = Entry(metadata, System.currentTimeMillis())
    }

    fun clear() = map.clear()

    fun size(): Int = map.size
}
