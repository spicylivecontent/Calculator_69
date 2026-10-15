package com.calculator.app.ai.embedding

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/**
 * Real ONNX Runtime-backed sentence embedding engine. It runs entirely inside the existing
 * app process; the quantized model is installed into app-private storage on first use.
 *
 * The model is pinned to an immutable Hugging Face revision and checked against SHA-256.
 * If install/load fails, callers should retain the existing rule-based engine.
 */
class OnnxRuntimeEmbeddingEngine private constructor() : EmbeddingEngine, AutoCloseable {
    override val modelName: String = "all-MiniLM-L6-v2-int8"
    override val dimension: Int = 384

    @Volatile private var session: OrtSession? = null
    @Volatile private var vocabulary: Map<String, Int> = emptyMap()
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, FloatArray>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    override val isReady: Boolean get() = session != null && vocabulary.isNotEmpty()

    /** Load the installed model and WordPiece vocabulary; returns false for incomplete assets. */
    @Synchronized
    fun load(modelFile: File, vocabFile: File): Boolean {
        if (!modelFile.isFile || modelFile.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return false
        if (!vocabFile.isFile || vocabFile.length() !in 100_000L..1_000_000L) return false
        return runCatching {
            val loadedVocab = vocabFile.readLines(Charsets.UTF_8)
                .mapIndexedNotNull { index, token -> token.trim().takeIf { it.isNotEmpty() }?.let { it to index } }
                .toMap()
            if (loadedVocab.size < 20_000 || listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]").any { it !in loadedVocab }) {
                return false
            }
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            val loadedSession = try {
                env.createSession(modelFile.absolutePath, options)
            } finally {
                options.close()
            }
            session?.close()
            session = loadedSession
            vocabulary = loadedVocab
            clearCache()
            true
        }.getOrDefault(false)
    }

    /** Installs a pinned, quantized ARM-friendly model in private app storage if missing. */
    suspend fun installIfNeeded(context: Context): Boolean {
        if (isReady) return true
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists() && !dir.mkdirs()) return false
        val model = File(dir, MODEL_FILE)
        val vocab = File(dir, VOCAB_FILE)
        if (model.exists() && sha256(model) != MODEL_SHA256) model.delete()
        if (!model.exists() && !downloadVerified(MODEL_URL, model, MAX_MODEL_BYTES, MODEL_SHA256)) return false
        if (!vocab.exists() || vocab.length() !in 100_000L..1_000_000L) {
            vocab.delete()
            if (!downloadVerified(VOCAB_URL, vocab, MAX_VOCAB_BYTES, expectedSha256 = null)) return false
        }
        return load(model, vocab)
    }

    override fun embed(text: String): FloatArray {
        val activeSession = session ?: return FloatArray(dimension)
        val vocab = vocabulary
        if (text.isBlank() || vocab.isEmpty()) return FloatArray(dimension)
        val tokenIds = tokenize(text, vocab).take(MAX_SEQUENCE - 2).toMutableList()
        tokenIds.add(0, vocab.getValue("[CLS]"))
        tokenIds.add(vocab.getValue("[SEP]"))
        val attention = LongArray(tokenIds.size) { 1L }
        val ids = tokenIds.map { it.toLong() }.toLongArray()
        val inputTensors = linkedMapOf<String, OnnxTensor>()
        try {
            val shape = longArrayOf(1L, ids.size.toLong())
            for (name in activeSession.inputNames) {
                val values = when (name) {
                    "input_ids" -> ids
                    "attention_mask" -> attention
                    "token_type_ids" -> LongArray(ids.size)
                    else -> continue
                }
                inputTensors[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
            }
            activeSession.run(inputTensors).use { result ->
                val output = result[0] as? OnnxTensor ?: return FloatArray(dimension)
                val value = output.value
                val hidden = when (value) {
                    is Array<*> -> {
                        val batch = value.firstOrNull() as? Array<*> ?: return FloatArray(dimension)
                        batch.mapNotNull { it as? FloatArray }.toTypedArray()
                    }
                    else -> return FloatArray(dimension)
                }
                if (hidden.isEmpty()) return FloatArray(dimension)
                val pooled = FloatArray(dimension)
                var count = 0
                // Mean-pool content tokens, excluding [CLS] and [SEP], then L2-normalize.
                for (position in 1 until minOf(hidden.size - 1, tokenIds.size - 1)) {
                    val row = hidden[position]
                    for (i in 0 until minOf(dimension, row.size)) pooled[i] += row[i]
                    count++
                }
                if (count == 0) return FloatArray(dimension)
                for (i in pooled.indices) pooled[i] /= count.toFloat()
                normalize(pooled)
                return pooled
            }
        } catch (_: Exception) {
            return FloatArray(dimension)
        } finally {
            inputTensors.values.forEach { runCatching { it.close() } }
        }
    }

    override fun similarity(queryText: String, documentText: String, cacheKey: String?): Float {
        if (!isReady || queryText.isBlank() || documentText.isBlank()) return 0f
        val queryVector = embed(queryText)
        val documentVector = if (cacheKey == null) {
            embed(documentText)
        } else synchronized(cacheLock) {
            cache.getOrPut(cacheKey) { embed(documentText) }
        }
        return EmbeddingEngine.cosineSimilarity(queryVector, documentVector)
    }

    override fun clearCache() {
        synchronized(cacheLock) { cache.clear() }
    }

    @Synchronized
    override fun close() {
        runCatching { session?.close() }
        session = null
        vocabulary = emptyMap()
        clearCache()
    }

    private fun tokenize(text: String, vocab: Map<String, Int>): List<Int> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
        val words = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]").findAll(normalized).map { it.value }
        val ids = ArrayList<Int>()
        val unknown = vocab.getValue("[UNK]")
        for (word in words) {
            if (word.length > 100) {
                ids += unknown
                continue
            }
            var start = 0
            val pieces = ArrayList<Int>()
            while (start < word.length) {
                var end = word.length
                var found: Int? = null
                while (start < end) {
                    val raw = word.substring(start, end)
                    val candidate = if (start == 0) raw else "##$raw"
                    found = vocab[candidate]
                    if (found != null) break
                    end--
                }
                if (found == null) {
                    pieces.clear()
                    pieces += unknown
                    break
                }
                pieces += found
                start = end
            }
            ids += pieces
            if (ids.size >= MAX_SEQUENCE - 2) break
        }
        return ids
    }

    private fun normalize(vector: FloatArray) {
        var sum = 0f
        vector.forEach { sum += it * it }
        if (sum <= 1e-12f) return
        val scale = 1f / sqrt(sum)
        for (i in vector.indices) vector[i] *= scale
    }

    private fun downloadVerified(url: String, destination: File, maxBytes: Long, expectedSha256: String?): Boolean {
        val part = File(destination.parentFile, destination.name + ".part")
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Calculator-Android/1.0")
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) return false
                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) return false
                BufferedInputStream(connection.inputStream).use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) return false
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (!part.isFile || part.length() == 0L) return false
                if (expectedSha256 != null && sha256(part) != expectedSha256) return false
                if (destination.exists()) destination.delete()
                if (!part.renameTo(destination)) {
                    part.copyTo(destination, overwrite = true)
                    part.delete()
                }
                true
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            false
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        val Default = OnnxRuntimeEmbeddingEngine()
        private const val MODEL_DIR = "ai_models"
        private const val MODEL_FILE = "model_qint8_arm64.onnx"
        private const val VOCAB_FILE = "vocab.txt"
        private const val MODEL_SHA256 = "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"
        private const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
        private const val MODEL_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION/onnx/model_qint8_arm64.onnx"
        private const val VOCAB_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION/vocab.txt"
        private const val MIN_MODEL_BYTES = 10_000_000L
        private const val MAX_MODEL_BYTES = 30_000_000L
        private const val MAX_VOCAB_BYTES = 1_000_000L
        private const val MAX_SEQUENCE = 128
        private const val MAX_CACHE_ENTRIES = 512
    }
}
