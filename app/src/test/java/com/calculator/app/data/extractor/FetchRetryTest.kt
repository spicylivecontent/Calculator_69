package com.calculator.app.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Omförsök ska bara ske när de kan hjälpa: en tillfällig svacka, inte en sida som svarat
 * 404 eller en adress appen aldrig får hämta.
 */
class FetchRetryTest {

    @Test
    fun networkProblemsAreRetried() {
        assertTrue(Fetch.shouldRetry(Fetch.Attempt.NetworkError, attemptIndex = 0, attempts = 2))
    }

    @Test
    fun temporaryServerErrorsAndRateLimitsAreRetried() {
        for (code in listOf(429, 500, 502, 503, 504)) {
            assertTrue(
                "HTTP $code borde försökas igen",
                Fetch.shouldRetry(Fetch.Attempt.HttpError(code), attemptIndex = 0, attempts = 2)
            )
        }
    }

    @Test
    fun permanentErrorsAreNotRetried() {
        for (code in listOf(400, 401, 403, 404, 410, 451)) {
            assertFalse(
                "HTTP $code ska inte försökas igen",
                Fetch.shouldRetry(Fetch.Attempt.HttpError(code), attemptIndex = 0, attempts = 2)
            )
        }
    }

    @Test
    fun aWorkingPageIsNeverFetchedTwice() {
        assertFalse(Fetch.shouldRetry(Fetch.Attempt.Body("html"), attemptIndex = 0, attempts = 2))
    }

    @Test
    fun forbiddenRequestsAndBrokenValuesAreNotRetried() {
        assertFalse(Fetch.shouldRetry(Fetch.Attempt.Rejected, attemptIndex = 0, attempts = 2))
    }

    @Test
    fun theNumberOfAttemptsIsRespected() {
        assertTrue("första försöket får göras om", Fetch.shouldRetry(Fetch.Attempt.NetworkError, 0, 3))
        assertTrue("andra försöket får göras om när tre är tillåtna", Fetch.shouldRetry(Fetch.Attempt.NetworkError, 1, 3))
        assertFalse("sista försöket ska inte följas av ett till", Fetch.shouldRetry(Fetch.Attempt.NetworkError, 2, 3))
        assertFalse("ett enda försök betyder inga omförsök", Fetch.shouldRetry(Fetch.Attempt.NetworkError, 0, 1))
    }

    @Test
    fun theStandardIsTwoAttempts() {
        assertTrue(Fetch.MAX_ATTEMPTS >= 2)
    }
}
