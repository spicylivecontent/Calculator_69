package com.calculator.app.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tidsberoende logik: intervallen hålls korta (30–120 ms) och testerna kontrollerar
 * bara undre gränser och tak, aldrig exakta tidpunkter, så att de inte blir vingliga
 * i CI.
 */
class HostRateLimiterTest {

    @Test
    fun sequentialCallsToSameHostAreSpacedApart() = runBlocking {
        val limiter = HostRateLimiter(minIntervalMs = 120, maxInFlightPerHost = 1)
        val starts = mutableListOf<Long>()

        repeat(3) {
            limiter.withPermit("https://site.example/page") {
                starts += System.currentTimeMillis()
            }
        }

        assertEquals(3, starts.size)
        val gap1 = starts[1] - starts[0]
        val gap2 = starts[2] - starts[1]
        assertTrue("starterna ska hålla minst ~intervallet, gap=$gap1/$gap2", gap1 >= 110 && gap2 >= 110)
    }

    @Test
    fun atMostMaxInFlightPerHost() = runBlocking {
        val limiter = HostRateLimiter(minIntervalMs = 40, maxInFlightPerHost = 2)
        var inFlight = 0
        var maxObserved = 0

        val jobs = (1..8).map {
            async {
                limiter.withPermit("https://site.example/x") {
                    inFlight++
                    if (inFlight > maxObserved) maxObserved = inFlight
                    delay(30)
                    inFlight--
                }
            }
        }
        jobs.awaitAll()

        assertEquals("alla tillstånd släpps efteråt", 0, inFlight)
        assertTrue("högst 2 samtidiga per värd, observerat $maxObserved", maxObserved <= 2)
    }

    @Test
    fun differentHostsDoNotShareTheGate() = runBlocking {
        val limiter = HostRateLimiter(minIntervalMs = 40, maxInFlightPerHost = 1)
        var inFlight = 0
        var maxObserved = 0

        val jobs = listOf("https://a.example/x", "https://b.example/x").map { url ->
            async {
                limiter.withPermit(url) {
                    inFlight++
                    if (inFlight > maxObserved) maxObserved = inFlight
                    delay(50)
                    inFlight--
                }
            }
        }
        jobs.awaitAll()

        assertEquals(0, inFlight)
        assertTrue("olika värdar ska få köras parallellt, observerat $maxObserved", maxObserved >= 2)
    }

    @Test
    fun permitIsReleasedWhenTheBlockThrows() = runBlocking {
        val limiter = HostRateLimiter(minIntervalMs = 1, maxInFlightPerHost = 1)

        runCatching {
            limiter.withPermit("https://site.example/x") { error("provocation") }
        }

        // Om tillståndet inte släppts ovan skulle detta anrop hänga – withTimeout
        // gör att testet faller tydligt i stället för att frysa hela byggjobbet.
        val result = kotlinx.coroutines.withTimeout(5_000) {
            limiter.withPermit("https://site.example/x") { "ok" }
        }
        assertEquals("ok", result)
    }
}
