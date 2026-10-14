package com.calculator.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextMatcherTest {
    @Test
    fun singleWordDoesNotMatchInsideAnotherWord() {
        assertFalse(SearchTextMatcher.containsTerm("A classic video", "ass"))
        assertTrue(SearchTextMatcher.containsTerm("A video tagged ass", "ass"))
        assertTrue(SearchTextMatcher.containsTerm("ASS in uppercase", "ass"))
    }

    @Test
    fun phraseRequiresWholeWordBoundariesAndAllowsExtraWhitespace() {
        assertTrue(SearchTextMatcher.containsTerm("Scene: two   women together", "two women"))
        assertFalse(SearchTextMatcher.containsTerm("Scene: twenty women together", "two women"))
        assertFalse(SearchTextMatcher.containsTerm("notcouple content", "couple"))
    }

    @Test
    fun blankTermsNeverMatch() {
        assertFalse(SearchTextMatcher.containsTerm("some text", "  "))
        assertFalse(SearchTextMatcher.containsTerm("", "word"))
    }
}
