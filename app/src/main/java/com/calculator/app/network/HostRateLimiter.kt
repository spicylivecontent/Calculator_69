package com.calculator.app.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-värd-begränsning av nätanrop: håller isär sajterna och ser till att en
 * enskild sajt varken får för många samtidiga anrop eller för täta startider.
 * Det minskar risken att sajten blockerar appen när flera sökvarianter och
 * detaljsidor hämtas parallellt.
 *
 * Två regler per värd:
 *  1. Högst [maxInFlightPerHost] anrop är igång samtidigt.
 *  2. Minst [minIntervalMs] millisekunder mellan startider.
 *
 * Parametrarna och klockan är konstruktorargument så att begränsningen kan
 * enhetstestas snabbt och deterministiskt.
 *
 * @param minIntervalMs minsta avstånd mellan startider för samma värd.
 * @param maxInFlightPerHost största antal samtidiga anrop per värd.
 * @param clock tidskälla, injicerbar i tester.
 */
class HostRateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val maxInFlightPerHost: Int = DEFAULT_MAX_IN_FLIGHT_PER_HOST,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class HostState(maxInFlight: Int) {
        /** Slussar hur många anrop som får vara igång mot värdet samtidigt. */
        val gate = Semaphore(maxInFlight.coerceAtLeast(1))
        /** Seriell åtkomst till startiden så att mellanrummet alltid räknas rätt. */
        val startMutex = Mutex()
        var lastStartMs: Long = 0L
    }

    private val hosts = ConcurrentHashMap<String, HostState>()

    private fun hostKey(url: String): String = try {
        (java.net.URI(url).host ?: "unknown").lowercase()
    } catch (_: Exception) {
        "unknown"
    }

    /**
     * Väntar tills det är OK att starta ett anrop mot [url], kör [block] och
     * räknar ned igen oavsett resultat (även vid avbrott eller undantag).
     */
    suspend fun <T> withPermit(url: String, block: suspend () -> T): T {
        val state = hosts.getOrPut(hostKey(url)) { HostState(maxInFlightPerHost) }
        state.gate.acquire()
        try {
            state.startMutex.withLock {
                val waitMs = (state.lastStartMs + minIntervalMs) - clock()
                if (waitMs > 0) delay(waitMs)
                state.lastStartMs = clock()
            }
            return block()
        } finally {
            state.gate.release()
        }
    }

    companion object {
        /** 280 ms mellan startider: mänskligt tempo utan att sökningen känns seg. */
        const val DEFAULT_MIN_INTERVAL_MS = 280L
        const val DEFAULT_MAX_IN_FLIGHT_PER_HOST = 2

        /** Delad instans som Fetch använder. */
        val Default = HostRateLimiter()
    }
}
