package com.calculator.app.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-domän rate limiting så att en enskild sajt inte får för många
 * samtidiga eller för täta anrop (minskar risken att bli blockerad).
 */
object HostRateLimiter {

    private const val MIN_INTERVAL_MS = 280L
    private const val MAX_IN_FLIGHT_PER_HOST = 2

    private data class HostState(
        val mutex: Mutex = Mutex(),
        var lastStartMs: Long = 0L,
        var inFlight: Int = 0
    )

    private val hosts = ConcurrentHashMap<String, HostState>()

    private fun hostKey(url: String): String {
        return try {
            val u = java.net.URI(url)
            (u.host ?: "unknown").lowercase()
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Väntar tills det är säkert att starta ett anrop mot [url], kör [block],
     * och räknar ner in-flight oavsett resultat.
     */
    suspend fun <T> withPermit(url: String, block: suspend () -> T): T {
        val key = hostKey(url)
        val state = hosts.getOrPut(key) { HostState() }
        state.mutex.withLock {
            while (state.inFlight >= MAX_IN_FLIGHT_PER_HOST) {
                state.mutex.unlock()
                delay(50)
                state.mutex.lock()
            }
            val wait = (state.lastStartMs + MIN_INTERVAL_MS) - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            state.lastStartMs = System.currentTimeMillis()
            state.inFlight++
        }
        return try {
            block()
        } finally {
            state.mutex.withLock { state.inFlight = (state.inFlight - 1).coerceAtLeast(0) }
        }
    }
}
