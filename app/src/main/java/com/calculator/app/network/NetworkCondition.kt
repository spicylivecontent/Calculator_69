package com.calculator.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Hur många nätverksanrop appen får köra samtidigt, och hur länge en enskild
 * sajt får ta innan den hoppas över.
 *
 * Logiken är ren (bara booleans in) så att den kan enhetstestas, och används för att
 * undvika CPU- och trafikspikar på mobildata medan Wi-Fi får köra fler anrop.
 */
data class NetworkCondition(
    val isMetered: Boolean,
    val isWifi: Boolean,
    val isOffline: Boolean = false
) {
    /** Antal samtidiga detaljsideshämtningar. */
    val enrichmentParallelism: Int
        get() = when {
            isOffline -> 1
            isMetered -> 3
            isWifi -> 8
            else -> 5
        }

    /** Antal samtidiga sajter i en sökning. */
    val siteParallelism: Int
        get() = if (isMetered) 4 else 10

    /** Hur länge en sajt får ta innan den avbryts. */
    val siteTimeoutMs: Long
        get() = if (isMetered) 9_000L else 6_000L

    val label: String
        get() = when {
            isOffline -> "Offline"
            isWifi -> "Wi-Fi"
            isMetered -> "Mobildata"
            else -> "Nätverk"
        }

    companion object {
        /** Standard när nätverksstatusen är okänd. */
        val Default = NetworkCondition(isMetered = false, isWifi = false)

        fun from(isMetered: Boolean, isWifi: Boolean, isOffline: Boolean = false): NetworkCondition =
            NetworkCondition(isMetered = isMetered, isWifi = isWifi, isOffline = isOffline)

        /** Läser av enhetens aktuella nätverk. */
        fun current(context: Context): NetworkCondition {
            val manager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Default
            val network = manager.activeNetwork ?: return NetworkCondition(false, false, isOffline = true)
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return NetworkCondition(false, false, isOffline = true)
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isMetered = !isWifi || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return NetworkCondition(isMetered = isMetered, isWifi = isWifi, isOffline = !hasInternet)
        }
    }
}
