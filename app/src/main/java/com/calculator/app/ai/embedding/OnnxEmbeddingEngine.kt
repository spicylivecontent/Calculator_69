package com.calculator.app.ai.embedding

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/**
 * Lokal (on-device) embedding-motor för hybrid semantisk + regelbaserad ranking.
 *
 * Motorn kombinerar två lägen utan att spränga APK-storleksgränsen:
 *  1. Ett inbyggt 128-dimensionellt semantiskt koncept- och frasnätverk (engelska + svenska
 *     synonymer, slang och förkortningar som "atm" ↔ "ass to mouth", "nonstop" ↔ "continuous",
 *     "latina" ↔ "hispanic", "solo" ↔ "by herself" osv.) som fungerar direkt utan nedladdning.
 *  2. Stöd för att asynkront ladda kvantiserade modellvikter från lokal lagring
 *     (`filesDir/ai_models/`) när en extern modellfil har laddats ner.
 *
 * Alla beräknade dokument-embeddings mellanlagras i en trådsäker LRU-minnescache
 * så att redan inlästa sökträffar inte behöver beräknas om vid omrankning eller sortering.
 */
class OnnxEmbeddingEngine(
    override val modelName: String = DEFAULT_MODEL_NAME,
    private val maxCacheEntries: Int = DEFAULT_CACHE_SIZE
) : EmbeddingEngine {

    override val dimension: Int = DIMENSION

    @Volatile
    override var isReady: Boolean = true
        private set

    /** Valfria externa ordvikter laddade från lokal modellfil på enheten. */
    @Volatile
    private var externalTokenVectors: Map<String, FloatArray> = emptyMap()

    private val cacheLock = Any()
    private val embeddingCache = object : LinkedHashMap<String, FloatArray>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > maxCacheEntries
    }

    /**
     * Laddar kvantiserade ord-/subword-vektorer från en lokal binärfil om den finns.
     * Format: [int32 magic=0x4F4E4E58 ("ONNX")][int32 dim][int32 vocabSize] följt av
     * [utf-8 token][int8 * dim kvantiserade vikter].
     */
    fun loadModelFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 16L) return false
        return runCatching {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                val magic = input.readInt()
                val fileDim = input.readInt()
                val count = input.readInt()
                if (magic != MODEL_MAGIC || fileDim !in 16..512 || count !in 1..200_000) {
                    return false
                }
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

        // 1. Semantiska konceptkluster (fraser, synonymer, slang, förkortningar, SV + EN)
        for (i in CONCEPT_CLUSTERS.indices) {
            val cluster = CONCEPT_CLUSTERS[i]
            var activation = 0f
            for (phrase in cluster.phrases) {
                val matched = if (' ' in phrase) {
                    padded.contains(" $phrase ")
                } else {
                    phrase in tokenSet || stem(phrase) in stems
                }
                if (matched) {
                    activation += cluster.weight
                }
            }
            if (activation > 0f) {
                // Mjuk mättnad så att upprepade synonymer stärker men inte dominerar helt.
                vector[i] = minOf(activation, cluster.weight * 1.65f)
            }
        }

        // 2. Externa modellvikter (om en nedladdad modellfil har lästs in)
        val ext = externalTokenVectors
        if (ext.isNotEmpty()) {
            for (token in tokens) {
                val weights = ext[token] ?: ext[stem(token)] ?: continue
                for (i in 0 until minOf(DIMENSION, weights.size)) {
                    vector[i] += weights[i]
                }
            }
        }

        normalizeInPlace(vector)
        return vector
    }

    override fun similarity(queryText: String, documentText: String, cacheKey: String?): Float {
        if (!isReady || queryText.isBlank() || documentText.isBlank()) return 0f
        val queryVec = embed(queryText)
        val docVec = if (cacheKey != null) {
            synchronized(cacheLock) {
                embeddingCache.getOrPut(cacheKey) { embed(documentText) }
            }
        } else {
            embed(documentText)
        }
        return EmbeddingEngine.cosineSimilarity(queryVec, docVec)
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            embeddingCache.clear()
        }
    }

    fun cachedCount(): Int = synchronized(cacheLock) { embeddingCache.size }

    private fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .trim()

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

    private data class ConceptCluster(
        val id: String,
        val weight: Float,
        val phrases: List<String>
    )

    companion object {
        const val DIMENSION = 128
        const val DEFAULT_CACHE_SIZE = 512
        const val DEFAULT_MODEL_NAME = "bge-small-hybrid-ondevice"
        const val MODEL_MAGIC = 0x4F4E4E58

        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

        /** Delad standardinstans som används av ResultRanker och SearchViewModel. */
        val Default: OnnxEmbeddingEngine = OnnxEmbeddingEngine()

        /**
         * Semantiska konceptkluster (upp till 128 dimensioner) som fångar synonymer,
         * flerspråkiga uttryck (svenska/engelska), slang och förkortningar.
         */
        private val CONCEPT_CLUSTERS: List<ConceptCluster> = listOf(
            // Etnicitet & ursprung
            ConceptCluster("latina", 1.2f, listOf("latina", "latin", "hispanic", "mexican", "colombian", "brazilian", "spanish", "chica", "latinamerikansk", "latinas")),
            ConceptCluster("ebony", 1.2f, listOf("ebony", "black girl", "black woman", "african", "dark skin", "svart")),
            ConceptCluster("asian", 1.2f, listOf("asian", "japanese", "jav", "korean", "chinese", "thai", "filipina", "asiatisk", "asiatiska")),
            ConceptCluster("european", 1.1f, listOf("european", "scandinavian", "swedish", "nordic", "german", "french", "czech", "russian", "svensk", "skandinavisk", "europeisk")),
            ConceptCluster("indian_arab", 1.1f, listOf("indian", "desi", "arab", "middle eastern", "persian", "indisk", "arabisk")),

            // Hårfärg & utseende
            ConceptCluster("blonde", 1.0f, listOf("blonde", "blond", "fair hair", "golden hair", "ljusharig")),
            ConceptCluster("brunette", 1.0f, listOf("brunette", "brown hair", "dark hair", "brunett")),
            ConceptCluster("redhead", 1.1f, listOf("redhead", "red hair", "ginger", "rodharig", "rott har")),
            ConceptCluster("black_hair", 1.0f, listOf("black hair", "raven hair", "svartharig")),
            ConceptCluster("natural_look", 1.0f, listOf("natural", "no makeup", "girl next door", "freckles", "naturlig", "fracknar")),
            ConceptCluster("tattoo_alt", 1.0f, listOf("tattoo", "tattooed", "inked", "piercing", "pierced", "goth", "alt girl", "tatueringar")),
            ConceptCluster("glamour_makeup", 1.0f, listOf("glamour", "makeup", "model", "fashion", "smink")),

            // Kroppsform & attribut
            ConceptCluster("curvy_pawg", 1.15f, listOf("curvy", "thick", "pawg", "voluptuous", "chubby", "bbw", "plump", "juicy", "kurvig", "kurvigare")),
            ConceptCluster("slim_petite", 1.1f, listOf("slim", "petite", "skinny", "slender", "tiny", "small", "smal", "slimmad", "kort")),
            ConceptCluster("fit_athletic", 1.1f, listOf("fit", "fit body", "athletic", "toned", "muscular", "abs", "gym body", "yoga", "tranad", "valtranad")),
            ConceptCluster("busty", 1.1f, listOf("busty", "big tits", "big boobs", "huge tits", "large breasts", "natural tits", "cleavage", "stor byst", "stora brost")),
            ConceptCluster("big_ass", 1.15f, listOf("big ass", "big booty", "bubble butt", "phat ass", "twerk", "twerking", "booty", "rumpa", "stor rumpa")),
            ConceptCluster("long_legs", 1.0f, listOf("tall", "long legs", "leggy", "lang", "langa ben")),

            // Ålder/roll (vuxna)
            ConceptCluster("milf_mature", 1.15f, listOf("milf", "mature", "cougar", "older woman", "housewife", "experienced", "mogen", "hemmafru")),
            ConceptCluster("young_adult", 1.0f, listOf("18+", "college", "university", "coed", "student", "cheerleader")),

            // Antal & konstellation
            ConceptCluster("solo", 1.25f, listOf("solo", "solo girl", "solo female", "by herself", "all alone", "alone", "masturbation", "masturbating", "self pleasure", "fingers herself", "playing with herself", "onani", "ensam")),
            ConceptCluster("couple", 1.05f, listOf("couple", "real couple", "boyfriend", "girlfriend", "husband", "wife", "lovers", "par", "tva")),
            ConceptCluster("threesome", 1.2f, listOf("threesome", "3some", "fmf", "ffm", "mmf", "two girls one guy", "two guys one girl", "trekant")),
            ConceptCluster("group_orgy", 1.2f, listOf("group", "group sex", "orgy", "gangbang", "foursome", "swinger", "swingers", "party", "grupp", "orgie")),
            ConceptCluster("lesbian", 1.2f, listOf("lesbian", "lesbians", "girl on girl", "two girls", "scissoring", "tribbing", "strapon", "strap on", "lesbisk", "lesbiska")),

            // Aktiviteter & praktiker (inkl. förkortningar som atm, dp, bj)
            ConceptCluster("anal", 1.3f, listOf("anal", "anal sex", "backdoor", "assfuck", "ass fuck", "in the ass", "butt sex", "sodomy", "gape", "gaping", "anal gape")),
            ConceptCluster("atm", 1.4f, listOf("atm", "a2m", "ass to mouth", "ass 2 mouth", "from ass to mouth", "asstomouth", "taste her ass")),
            ConceptCluster("dp_double", 1.35f, listOf("dp", "double penetration", "double", "spitroast", "spit roast", "two cocks", "dvp", "dap")),
            ConceptCluster("oral_bj", 1.2f, listOf("blowjob", "blow job", "bj", "oral", "sucking", "cocksucking", "sloppy", "giving head", "head", "fellatio", "avsugning")),
            ConceptCluster("deepthroat", 1.25f, listOf("deepthroat", "deep throat", "dt", "gagging", "facefuck", "face fuck", "throatfuck", "throat", "sloppy deepthroat")),
            ConceptCluster("handjob_titjob", 1.15f, listOf("handjob", "hand job", "hj", "stroking", "jerking", "titjob", "tittyfuck", "paizuri", "boobjob")),
            ConceptCluster("cunnilingus_rimming", 1.2f, listOf("cunnilingus", "pussy licking", "eating pussy", "licking pussy", "facesitting", "face sitting", "rimming", "rimjob", "anilingus", "ass licking")),
            ConceptCluster("cowgirl", 1.15f, listOf("cowgirl", "reverse cowgirl", "riding", "girl on top", "on top", "bouncing", "ridande")),
            ConceptCluster("doggystyle", 1.15f, listOf("doggystyle", "doggy style", "doggy", "from behind", "bent over", "on all fours", "pronebone", "prone bone", "bakifran")),
            ConceptCluster("missionary", 1.1f, listOf("missionary", "legs up", "face to face", "eye contact", "classic sex", "klassisk")),
            ConceptCluster("squirt_orgasm", 1.2f, listOf("squirt", "squirting", "female orgasm", "real orgasm", "orgasm", "climax", "multiple orgasms", "gushing", "shaking orgasm")),
            ConceptCluster("creampie", 1.2f, listOf("creampie", "cream pie", "internal cumshot", "cum inside", "breeding", "filled", "dripping")),
            ConceptCluster("cumshot_facial", 1.15f, listOf("cumshot", "cum", "facial", "cum on face", "swallow", "cum swallow", "cum in mouth", "bukkake", "avslut", "avslutning")),

            // Tempo, intensitet & "nonstop"
            ConceptCluster("nonstop_intense", 1.35f, listOf("nonstop", "non stop", "non-stop", "continuous", "continuously", "marathon", "relentless", "endless", "no break", "without stopping", "over and over", "intense", "hardcore", "pounding", "intensiv", "knulla")),
            ConceptCluster("rough_bdsm", 1.2f, listOf("rough", "hard", "bdsm", "bondage", "spanking", "choking", "dominant", "submissive", "femdom", "domination", "slave", "tied up", "collar", "binda", "underdanig")),
            ConceptCluster("gentle_sensual", 1.15f, listOf("gentle", "slow", "sensual", "romantic", "passionate", "tender", "soft", "intimate", "making love", "romantisk", "passionerad", "langsam", "mjuk")),
            ConceptCluster("massage_oil", 1.2f, listOf("massage", "oiled", "oil", "baby oil", "shiny", "lotion", "nuru", "spa", "rubdown", "masseur")),

            // Kamera & produktionsstil
            ConceptCluster("pov", 1.2f, listOf("pov", "point of view", "first person", "eye view")),
            ConceptCluster("amateur_homemade", 1.15f, listOf("amateur", "homemade", "home made", "real", "unedited", "raw", "hidden cam", "phone video", "selfie", "amator", "amatorkamera", "handhallen")),
            ConceptCluster("closeup", 1.1f, listOf("closeup", "close up", "close-up", "zoom", "spread", "narbild")),
            ConceptCluster("hd_4k", 0.9f, listOf("4k", "uhd", "2160p", "1080p", "60fps", "full hd", "high definition")),
            ConceptCluster("full_movie_vintage", 1.0f, listOf("full movie", "full length", "story", "plot", "vintage", "retro", "classic", "langfilm")),

            // Miljö & plats
            ConceptCluster("kitchen", 1.2f, listOf("kitchen", "cooking", "counter", "kitchen table", "fridge", "kok")),
            ConceptCluster("bedroom", 1.1f, listOf("bedroom", "bed", "morning", "waking up", "sheets", "pillow", "sovrum", "sang")),
            ConceptCluster("living_room", 1.1f, listOf("living room", "couch", "sofa", "tv", "carpet", "floor", "vardagsrum", "soffa")),
            ConceptCluster("bathroom_shower", 1.2f, listOf("bathroom", "shower", "bathtub", "bath", "wet", "sauna", "jacuzzi", "hot tub", "badrum", "dusch", "bastu")),
            ConceptCluster("outdoor_public", 1.2f, listOf("outdoor", "outdoors", "public", "beach", "forest", "woods", "park", "garden", "balcony", "nature", "camping", "utomhus", "strand", "skog")),
            ConceptCluster("car_vehicle", 1.2f, listOf("car", "backseat", "roadtrip", "parking", "van", "truck", "boat", "yacht", "bil")),
            ConceptCluster("office_work", 1.2f, listOf("office", "desk", "secretary", "boss", "coworker", "interview", "casting", "audition", "meeting", "kontor", "chef", "sekreterare")),
            ConceptCluster("gym_sport", 1.15f, listOf("gym", "workout", "fitness", "yoga pants", "leggings", "trainer", "locker room", "stretching")),
            ConceptCluster("hotel", 1.1f, listOf("hotel", "hotel room", "vacation", "holiday", "resort", "airbnb", "hotell")),

            // Klädsel, rollspel & fetisch
            ConceptCluster("lingerie_stockings", 1.15f, listOf("lingerie", "stockings", "pantyhose", "nylons", "fishnet", "fishnets", "garter", "heels", "high heels", "corset", "panties", "thong", "bikini", "skirt", "dress", "underklader", "strumpbyxor")),
            ConceptCluster("roleplay_uniform", 1.15f, listOf("roleplay", "role play", "uniform", "nurse", "doctor", "hospital", "police", "cop", "maid", "french maid", "teacher", "cosplay", "bride", "sjukskoterska", "polis", "brud")),
            ConceptCluster("leather_latex", 1.15f, listOf("leather", "latex", "shiny", "pvc", "catsuit", "boots", "lader")),
            ConceptCluster("feet_fetish", 1.2f, listOf("feet", "foot", "footjob", "foot fetish", "feet fetish", "toes", "soles", "shoe", "socks", "fotfetisch", "fotter")),
            ConceptCluster("toys_dildo", 1.2f, listOf("toy", "toys", "sex toys", "dildo", "vibrator", "wand", "hitachi", "magic wand", "buttplug", "butt plug", "anal plug", "lovense", "machine", "fucking machine", "leksaker"))
        )
    }
}
