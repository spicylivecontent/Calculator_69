package com.calculator.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

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
    val isOffline: Boolean = false,
    /** Låg batterinivå utan laddare – då halveras antalet samtidiga anrop. */
    val isLowBattery: Boolean = false
) {
    /** Antal samtidiga detaljsideshämtningar. */
    val enrichmentParallelism: Int
        get() = halveIfLowBattery(
            when {
                isOffline -> 1
                isMetered -> 3
                isWifi -> 8
                else -> 5
            }
        )

    /** Antal samtidiga sajter i en sökning. */
    val siteParallelism: Int
        get() = halveIfLowBattery(if (isMetered) 4 else 10)

    /** Batterisparläget sänker takten men aldrig under ett anrop. */
    private fun halveIfLowBattery(value: Int): Int =
        if (isLowBattery) (value / 2).coerceAtLeast(1) else value

    /** Hur länge en sajt får ta innan den avbryts. */
    val siteTimeoutMs: Long
        get() = if (isMetered) 9_000L else 6_000L

    val label: String
        get() = when {
            isOffline -> "Offline"
            isLowBattery -> "$base · låg batterinivå"
            else -> base
        }

    private val base: String
        get() = when {
            isOffline -> "Offline"
            isWifi -> "Wi-Fi"
            isMetered -> "Mobildata"
            else -> "Nätverk"
        }

    companion object {
        /** Standard när nätverksstatusen är okänd. */
        val Default = NetworkCondition(isMetered = false, isWifi = false)

        /** Batterinivå (%) där appen sänker takten. */
        const val LOW_BATTERY_PERCENT = 20

        fun from(
            isMetered: Boolean,
            isWifi: Boolean,
            isOffline: Boolean = false,
            isLowBattery: Boolean = false
        ): NetworkCondition = NetworkCondition(
            isMetered = isMetered,
            isWifi = isWifi,
            isOffline = isOffline,
            isLowBattery = isLowBattery
        )

        /** Sant när batteriet är lågt och enheten inte laddar. */
        fun isBatteryLow(context: Context): Boolean {
            val battery = context.applicationContext
                .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return false
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = battery.isCharging
            return !charging && level in 0..LOW_BATTERY_PERCENT
        }

        /** Läser av enhetens aktuella nätverk. */
        fun current(context: Context): NetworkCondition {
            val lowBattery = isBatteryLow(context)
            val manager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Default.copy(isLowBattery = lowBattery)
            val network = manager.activeNetwork
                ?: return NetworkCondition(false, false, isOffline = true, isLowBattery = lowBattery)
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return NetworkCondition(false, false, isOffline = true, isLowBattery = lowBattery)
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isMetered = !isWifi || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return NetworkCondition(
                isMetered = isMetered,
                isWifi = isWifi,
                isOffline = !hasInternet,
                isLowBattery = lowBattery
            )
        }
    }
}
