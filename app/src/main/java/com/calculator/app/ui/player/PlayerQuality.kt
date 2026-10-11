package com.calculator.app.ui.player

import java.util.Locale

/** Ett kvalitetsval i spelarens kvalitetsmeny. */
sealed interface QualityChoice {
    val label: String

    data object Auto : QualityChoice {
        override val label: String get() = "Automatisk (bäst för anslutningen)"
    }

    data object Highest : QualityChoice {
        override val label: String get() = "Högsta möjliga"
    }

    data object DataSaver : QualityChoice {
        override val label: String get() = "Spara data (max 480p)"
    }

    data class Exact(val height: Int, val bitrate: Int) : QualityChoice {
        override val label: String get() = describeQuality(height, bitrate)
    }
}

/** En videokvalitet som strömmen faktiskt erbjuder. */
data class TrackQuality(val height: Int, val bitrate: Int)

/** Ren logik för kvalitetslistan – enhetstestad. */
object PlayerQuality {

    /** Bygger menyns kvalitetsval från de spår spelaren hittade, högsta först. */
    fun choicesFrom(tracks: List<TrackQuality>): List<QualityChoice> {
        val bestBitratePerHeight = LinkedHashMap<Int, Int>()
        for (track in tracks) {
            if (track.height <= 0) continue
            bestBitratePerHeight[track.height] = maxOf(bestBitratePerHeight[track.height] ?: 0, track.bitrate)
        }
        return bestBitratePerHeight.entries
            .sortedByDescending { it.key }
            .map { QualityChoice.Exact(height = it.key, bitrate = it.value) }
    }

    fun describeQuality(height: Int, bitrate: Int): String {
        val heightLabel = when {
            height >= 2160 -> "4K"
            height <= 0 -> "Okänd"
            else -> "${height}p"
        }
        val bitrateLabel = formatBitrate(bitrate)
        return if (bitrateLabel.isEmpty()) heightLabel else "$heightLabel · $bitrateLabel"
    }

    fun formatBitrate(bitrate: Int): String = when {
        bitrate <= 0 -> ""
        bitrate >= 1_000_000 -> String.format(Locale.ROOT, "%.1f Mbps", bitrate / 1_000_000f)
        else -> "${bitrate / 1_000} kbps"
    }

    /** Tak för bitrate när en exakt höjd väljs, så valet hamnar nära den valda kvaliteten. */
    fun bitrateCeiling(bitrate: Int): Int =
        if (bitrate <= 0) Int.MAX_VALUE else (bitrate * 1.2f).toInt().coerceAtLeast(1)
}
