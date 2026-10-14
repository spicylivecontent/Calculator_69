package com.calculator.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricSessionTest {
    @Test
    fun successfulSessionCompletesOnlyOnce() {
        var successes = 0
        var failures = 0
        val session = BiometricSession({ successes++ }, { failures++ })
        session.succeed()
        session.succeed()
        session.fail()
        session.cancel()
        assertEquals(1, successes)
        assertEquals(0, failures)
    }

    @Test
    fun errorCannotBeFollowedByAnUnlock() {
        var successes = 0
        var failures = 0
        val session = BiometricSession({ successes++ }, { failures++ })
        session.fail()
        session.fail()
        session.succeed()
        assertEquals(0, successes)
        assertEquals(1, failures)
    }

    @Test
    fun disposedPromptCannotAffectNewSession() {
        var successes = 0
        var failures = 0
        val old = BiometricSession({ successes++ }, { failures++ })
        old.cancel()
        val current = BiometricSession({ successes++ }, { failures++ })
        old.succeed()
        old.fail()
        assertEquals(0, successes)
        assertEquals(0, failures)
        current.succeed()
        assertEquals(1, successes)
    }
}
