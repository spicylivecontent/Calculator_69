package com.calculator.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConditionTest {

    @Test
    fun wifiGetsMoreParallelWorkThanMobileData() {
        val wifi = NetworkCondition.from(isMetered = false, isWifi = true)
        val mobile = NetworkCondition.from(isMetered = true, isWifi = false)

        assertTrue(
            "Wi-Fi ska tillåta fler samtidiga hämtningar än mobildata",
            wifi.enrichmentParallelism > mobile.enrichmentParallelism
        )
        assertTrue(wifi.siteParallelism > mobile.siteParallelism)
    }

    @Test
    fun mobileDataRunsFewerRequestsButGetsMoreTimePerSite() {
        val wifi = NetworkCondition.from(isMetered = false, isWifi = true)
        val mobile = NetworkCondition.from(isMetered = true, isWifi = false)

        // Färre anrop samtidigt sparar trafik …
        assertTrue(mobile.siteParallelism < wifi.siteParallelism)
        assertTrue(mobile.enrichmentParallelism < wifi.enrichmentParallelism)
        // … men en långsammare sajt får längre tid på sig innan den hoppas över.
        assertTrue("mobildata ska ge mer tid per sajt", mobile.siteTimeoutMs > wifi.siteTimeoutMs)
        assertTrue(mobile.siteTimeoutMs in 5_000L..15_000L)
    }

    @Test
    fun offlineOnlyAllowsOneRequestAtATime() {
        val offline = NetworkCondition.from(isMetered = true, isWifi = false, isOffline = true)

        assertEquals(1, offline.enrichmentParallelism)
        assertEquals("Offline", offline.label)
    }

    @Test
    fun unknownNetworkGetsMiddleGroundValues() {
        val unknown = NetworkCondition.Default

        assertTrue(unknown.enrichmentParallelism in 2..6)
        assertEquals("Nätverk", unknown.label)
    }

    @Test
    fun lowBatteryHalvesTheWorkButNeverBelowOne() {
        val wifi = NetworkCondition.from(isMetered = false, isWifi = true)
        val wifiLow = NetworkCondition.from(isMetered = false, isWifi = true, isLowBattery = true)
        val meteredLow = NetworkCondition.from(isMetered = true, isWifi = false, isLowBattery = true)
        val offlineLow = NetworkCondition.from(isMetered = true, isWifi = false, isOffline = true, isLowBattery = true)

        assertEquals(wifi.enrichmentParallelism / 2, wifiLow.enrichmentParallelism)
        assertEquals(wifi.siteParallelism / 2, wifiLow.siteParallelism)
        assertTrue("mobildata + låg batterinivå ska fortfarande kunna hämta något", meteredLow.enrichmentParallelism >= 1)
        assertEquals("offline eller låg batterinivå ska aldrig ge noll", 1, offlineLow.enrichmentParallelism)
        assertTrue(wifiLow.label.contains("låg batterinivå"))
    }

    @Test
    fun parallelismIsAlwaysWithinSafeBounds() {
        val cases = listOf(
            NetworkCondition.from(isMetered = true, isWifi = false),
            NetworkCondition.from(isMetered = false, isWifi = true),
            NetworkCondition.from(isMetered = false, isWifi = false),
            NetworkCondition.from(isMetered = true, isWifi = true),
            NetworkCondition.from(isMetered = true, isWifi = false, isOffline = true)
        )

        for (condition in cases) {
            assertTrue(
                "parallellitet utanför gränserna: ${condition.enrichmentParallelism}",
                condition.enrichmentParallelism in 1..12
            )
            assertTrue(
                "sajtparallellitet utanför gränserna: ${condition.siteParallelism}",
                condition.siteParallelism in 1..12
            )
            assertTrue(condition.siteTimeoutMs in 4_000L..20_000L)
        }
    }
}
