package com.calculator.app.ai.embedding

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/** Lokal on-device embedding for hybrid ranking. Scat/atm/solo precision is primarily handled by SearchPlanner rare terms + ResultRanker penalties. */
class OnnxEmbeddingEngine(
    override val modelName: String = DEFAULT_MODEL_NAME,
    private val maxCacheEntries: Int = DEFAULT_CACHE_SIZE
) : EmbeddingEngine {

    override val dimension: Int = DIMENSION

    @Volatile
    override var isReady: Boolean = true
        private set

    @Volatile
    private var externalTokenVectors: Map<String, FloatArray> = emptyMap()

    private val cacheLock = Any()
    private val embeddingCache = object : LinkedHashMap<String, FloatArray>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > maxCacheEntries
    }

    fun loadModelFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 16L) return false
        return runCatching {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                val magic = input.readInt()
                val fileDim = input.readInt()
                val count = input.readInt()
                if (magic != MODEL_MAGIC || fileDim !in 16..512 || count !in 1..200_000) return false
                val loaded = HashMap<String, FloatArray>(count.coerceAtMost(50_000))
                repeat(count) {
                    val token = input.readUTF().lowercase(Locale.ROOT)
                    val vec = FloatArray(DIMENSION)
                    for (i in 0 until fileDim) {
                        val v = input.readByte().toFloat() / 127f
                        if (i < DIMENSION) vec[i] = v
                    }
                    loaded[token] = vec
                }
                externalTokenVectors = loaded
                synchronized(cacheLock) { embeddingCache.clear() }
                isReady = true
                true
            }
        }.getOrDefault(false)
    }

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMENSION)
        val normalized = normalize(text)
        if (normalized.isEmpty()) return vector
        val padded = " $normalized "
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return vector
        val tokenSet = tokens.toSet()
        val stems = tokens.map { stem(it) }.toSet()
        for (i in CONCEPT_CLUSTERS.indices) {
            val cluster = CONCEPT_CLUSTERS[i]
            var activation = 0f
            for (phrase in cluster.phrases) {
                val matched = if (' ' in phrase) padded.contains(" $phrase ") else phrase in tokenSet || stem(phrase) in stems
                if (matched) activation += cluster.weight
            }
            if (activation > 0f) vector[i] = minOf(activation, cluster.weight * 1.65f)
        }
        val ext = externalTokenVectors
        if (ext.isNotEmpty()) {
            for (token in tokens) {
                val weights = ext[token] ?: ext[stem(token)] ?: continue
                for (i in 0 until minOf(DIMENSION, weights.size)) vector[i] += weights[i]
            }
        }
        normalizeInPlace(vector)
        return vector
    }

    override fun similarity(queryText: String, documentText: String, cacheKey: String?): Float {
        if (!isReady || queryText.isBlank() || documentText.isBlank()) return 0f
        val queryVec = embed(queryText)
        val docVec = if (cacheKey != null) synchronized(cacheLock) { embeddingCache.getOrPut(cacheKey) { embed(documentText) } } else embed(documentText)
        return EmbeddingEngine.cosineSimilarity(queryVec, docVec)
    }

    override fun clearCache() { synchronized(cacheLock) { embeddingCache.clear() } }
    fun cachedCount(): Int = synchronized(cacheLock) { embeddingCache.size }

    private fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT).replace(NON_ALPHANUMERIC, " ").trim()

    private fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 4 && word.endsWith("es") -> word.dropLast(2)
        word.length > 4 && word.endsWith("s") -> word.dropLast(1)
        word.length > 5 && word.endsWith("arna") -> word.dropLast(4)
        word.length > 4 && word.endsWith("en") -> word.dropLast(2)
        word.length > 4 && word.endsWith("et") -> word.dropLast(2)
        else -> word
    }

    private fun normalizeInPlace(vec: FloatArray) {
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        if (sumSq <= 1e-12f) return
        val inv = 1f / sqrt(sumSq)
        for (i in vec.indices) vec[i] *= inv
    }

    private data class ConceptCluster(val id: String, val weight: Float, val phrases: List<String>)

    companion object {
        const val DIMENSION = 128
        const val DEFAULT_CACHE_SIZE = 512
        const val DEFAULT_MODEL_NAME = "bge-small-hybrid-ondevice"
        const val MODEL_MAGIC = 0x4F4E4E58
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        val Default: OnnxEmbeddingEngine = OnnxEmbeddingEngine()

        private val CONCEPT_CLUSTERS: List<ConceptCluster> = listOf(
            ConceptCluster("latina", 1.2f, listOf("latina", "latin", "hispanic", "mexican", "colombian", "brazilian", "spanish", "chica", "latinamerikansk", "latinas")),
            ConceptCluster("ebony", 1.2f, listOf("ebony", "black girl", "black woman", "african", "dark skin", "svart")),
            ConceptCluster("asian", 1.2f, listOf("asian", "japanese", "jav", "korean", "chinese", "thai", "filipina", "asiatisk", "asiatiska")),
            ConceptCluster("solo", 1.3f, listOf("solo", "solo girl", "solo female", "by herself", "all alone", "alone", "masturbation", "masturbating", "self pleasure", "fingers herself", "playing with herself", "onani", "ensam")),
            ConceptCluster("couple", 1.05f, listOf("couple", "real couple", "boyfriend", "girlfriend", "husband", "wife", "lovers", "par", "tva")),
            ConceptCluster("anal", 1.3f, listOf("anal", "anal sex", "backdoor", "assfuck", "ass fuck", "in the ass", "gape", "gaping")),
            ConceptCluster("atm", 1.45f, listOf("atm", "a2m", "ass to mouth", "ass 2 mouth", "from ass to mouth", "asstomouth", "taste her ass")),
            ConceptCluster("scat", 1.5f, listOf("scat", "scat play", "scatology", "toilet", "poop", "pooping", "dirty ass", "brown", "toilet slave", "scat porn")),
            ConceptCluster("dildo_toy", 1.3f, listOf("dildo", "toy", "toys", "sex toys", "vibrator", "wand", "hitachi", "fucking machine", "leksaker")),
            ConceptCluster("nonstop_intense", 1.35f, listOf("nonstop", "non stop", "continuous", "relentless", "endless", "intense", "hardcore", "pounding")),
            ConceptCluster("oral_bj", 1.2f, listOf("blowjob", "bj", "oral", "sucking", "deepthroat", "deep throat")),
            ConceptCluster("milf_mature", 1.15f, listOf("milf", "mature", "cougar", "housewife", "mogen", "hemmafru")),
            ConceptCluster("pov", 1.2f, listOf("pov", "point of view", "first person")),
            ConceptCluster("amateur_homemade", 1.15f, listOf("amateur", "homemade", "real", "amator")),
            ConceptCluster("squirt_orgasm", 1.2f, listOf("squirt", "squirting", "orgasm", "climax")),
            ConceptCluster("creampie", 1.2f, listOf("creampie", "cum inside", "breeding")),
            ConceptCluster("feet_fetish", 1.2f, listOf("feet", "foot", "footjob", "fotfetisch")),
            ConceptCluster("rough_bdsm", 1.2f, listOf("rough", "bdsm", "bondage", "dominant", "submissive"))
            ConceptCluster("outdoor_public", 1.1f, listOf("outdoor", "outdoors", "public", "utomhus", "skog", "skogen", "forest", "park", "nature", "naturskog")),
            ConceptCluster("redhead", 1.2f, listOf("redhead", "red hair", "rodharig", "rod har", "ginger", "rodfargad")),
            ConceptCluster("shower_bath", 1.1f, listOf("shower", "dusch", "bathroom", "badrum", "bathtub", "badkar")),
        )
    }
}
