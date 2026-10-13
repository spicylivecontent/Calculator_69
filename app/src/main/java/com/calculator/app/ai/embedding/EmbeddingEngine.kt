package com.calculator.app.ai.embedding

import kotlin.math.sqrt

/**
 * Gränssnitt för lokal (on-device) text-embedding och semantisk likhetsberäkning.
 *
 * Gör att [com.calculator.app.data.model.ResultRanker] kan kombinera sin regelbaserade
 * poäng med en semantisk poäng (cosine similarity) direkt på telefonen.
 */
interface EmbeddingEngine {

    /** Namn/identitet på aktiv embedding-modell. */
    val modelName: String

    /** Antal dimensioner i embedding-vektorn. */
    val dimension: Int

    /** Sant när modellen är laddad och redo att beräkna embeddings. */
    val isReady: Boolean

    /** Skapar en L2-normaliserad embedding-vektor för given text. */
    fun embed(text: String): FloatArray

    /**
     * Beräknar cosine similarity (0f..1f) mellan en sökfråga och en videos rika text.
     * Om [cacheKey] anges återanvänds cachad embedding för dokumentet.
     */
    fun similarity(queryText: String, documentText: String, cacheKey: String? = null): Float

    /** Tömmer minnescachen (anropas bl.a. vid panik/lås). */
    fun clearCache()

    companion object {
        /** Beräknar cosine similarity (0f..1f) mellan två vektorer. */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            val size = minOf(a.size, b.size)
            if (size == 0) return 0f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in 0 until size) {
                val x = a[i]
                val y = b[i]
                dot += x * y
                normA += x * x
                normB += y * y
            }
            if (normA <= 1e-12f || normB <= 1e-12f) return 0f
            val denom = sqrt(normA * normB)
            return if (denom <= 1e-12f) 0f else (dot / denom).coerceIn(0f, 1f)
        }
    }
}
