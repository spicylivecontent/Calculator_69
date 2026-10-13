package com.calculator.app.ai.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnnxEmbeddingEngineTest {

    @Test
    fun matchesComplexSemanticQueryWithoutExactKeywords() {
        val engine = OnnxEmbeddingEngine()
        val query = "Latina solo anal nonstop atm"
        val semanticMatch = "Hispanic girl by herself in a continuous ass to mouth and backdoor scene"
        val unrelated = "Blonde couple cooking breakfast in the kitchen"

        val matchSim = engine.similarity(query, semanticMatch, cacheKey = "video-1")
        val unrelatedSim = engine.similarity(query, unrelated, cacheKey = "video-2")

        assertTrue("Expected high semantic similarity ($matchSim)", matchSim > 0.75f)
        assertEquals(0f, unrelatedSim, 0.001f)
    }

    @Test
    fun cachesDocumentEmbeddingsAndClearsOnPanic() {
        val engine = OnnxEmbeddingEngine()
        engine.clearCache()
        assertEquals(0, engine.cachedCount())

        val sim1 = engine.similarity("massage oil", "Sensual oiled spa rubdown", cacheKey = "doc-a")
        assertEquals(1, engine.cachedCount())
        val sim2 = engine.similarity("massage oil", "Sensual oiled spa rubdown", cacheKey = "doc-a")
        assertEquals(sim1, sim2, 0.0001f)

        engine.clearCache()
        assertEquals(0, engine.cachedCount())
    }

    @Test
    fun bridgesSwedishAndEnglishConcepts() {
        val engine = OnnxEmbeddingEngine()
        val sim = engine.similarity("rödhårig i duschen", "Redhead taking a wet shower in bathroom")
        assertTrue("Expected cross-lingual similarity ($sim)", sim > 0.7f)
    }
}
