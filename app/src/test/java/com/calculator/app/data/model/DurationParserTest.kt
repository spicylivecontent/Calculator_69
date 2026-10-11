package com.calculator.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationParserTest {

    @Test
    fun colonFormatParsesToSeconds() {
        assertEquals(754, DurationParser.toSeconds("12:34"))
        assertEquals(3723, DurationParser.toSeconds("1:02:03"))
        assertEquals(65, DurationParser.toSeconds("01:05"))
    }

    @Test
    fun wordFormatsParseToSeconds() {
        assertEquals(720, DurationParser.toSeconds("12 min"))
        assertEquals(3900, DurationParser.toSeconds("1h 5min"))
        assertEquals(90, DurationParser.toSeconds("1 min 30 sec"))
        assertEquals(2700, DurationParser.toSeconds("45 minuter"))
    }

    @Test
    fun ambiguousOrInvalidInputGivesNull() {
        assertNull(DurationParser.toSeconds(null))
        assertNull(DurationParser.toSeconds(""))
        assertNull(DurationParser.toSeconds("  "))
        assertNull(DurationParser.toSeconds("abc"))
        assertNull(DurationParser.toSeconds("12"))
        assertNull(DurationParser.toSeconds("0:00"))
        assertNull(DurationParser.toSeconds("1:2:3:4"))
    }

    @Test
    fun formatUsesShortClockStyle() {
        assertEquals("12:34", DurationParser.format(754))
        assertEquals("1:02:03", DurationParser.format(3723))
        assertEquals("00:45", DurationParser.format(45))
        assertNull(DurationParser.format(0))
        assertNull(DurationParser.format(null))
    }
}
