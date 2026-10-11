package com.calculator.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationParserTest {

    @Test
    fun parsesColonFormats() {
        assertEquals(754, DurationParser.toSeconds("12:34"))
        assertEquals(3723, DurationParser.toSeconds("1:02:03"))
        assertEquals(125, DurationParser.toSeconds("02:05"))
    }

    @Test
    fun parsesWordFormats() {
        assertEquals(720, DurationParser.toSeconds("12 min"))
        assertEquals(3900, DurationParser.toSeconds("1h 5min"))
        assertEquals(125, DurationParser.toSeconds("2m 5s"))
        assertEquals(5400, DurationParser.toSeconds("1 timme 30 min"))
    }

    @Test
    fun ignoresValuesWithoutADuration() {
        assertNull(DurationParser.toSeconds(null))
        assertNull(DurationParser.toSeconds(""))
        assertNull(DurationParser.toSeconds("HD"))
        assertNull(DurationParser.toSeconds("0:00"))
        assertNull(DurationParser.toSeconds("1080p hd 12:34"))
        assertNull(DurationParser.toSeconds("42"))
    }

    @Test
    fun formatsSecondsBackToLabels() {
        assertEquals("12:34", DurationParser.format(754))
        assertEquals("1:02:03", DurationParser.format(3723))
        assertEquals("02:05", DurationParser.format(125))
        assertNull(DurationParser.format(0))
        assertNull(DurationParser.format(null))
    }
}
