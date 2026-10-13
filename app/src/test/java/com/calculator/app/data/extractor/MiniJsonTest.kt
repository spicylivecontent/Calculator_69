package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun parsesObjectsArraysAndScalars() {
        val parsed = MiniJson.parse(
            """{"name":"Blonde","count":12,"ok":true,"list":["a","b"],"nested":{"deep":"value"}}"""
        ) as Map<*, *>

        assertEquals("Blonde", parsed["name"])
        assertEquals(12.0, parsed["count"])
        assertEquals(true, parsed["ok"])
        assertEquals(listOf("a", "b"), parsed["list"])
        @Suppress("UNCHECKED_CAST")
        val nested = parsed["nested"] as Map<*, *>
        assertEquals("value", nested["deep"])
    }

    @Test
    fun handlesEscapesAndUnicode() {
        val parsed = MiniJson.parse("""{"text":"rad1\nrad2 \"citat\" \u00e5"}""") as Map<*, *>

        val text = parsed["text"] as String
        assertTrue(text.contains("\n"))
        assertTrue(text.contains("\"citat\""))
        assertTrue(text.contains("å"))
    }

    @Test
    fun invalidInputGivesNullInsteadOfCrashing() {
        assertNull(MiniJson.parse(""))
        assertNull(MiniJson.parse("   "))
        assertNull(MiniJson.parse("{inte json"))
        assertNull(MiniJson.parse(null))
    }

    @Test
    fun findsTheFirstMatchingObjectInNestedStructures() {
        val parsed = MiniJson.parse(
            """{"@graph":[{"@type":"BreadcrumbList"},{"@type":"VideoObject","name":"Hittad"}]}"""
        )

        val video = MiniJson.findObject(parsed) { map ->
            (MiniJson.string(map["@type"]).orEmpty()).contains("VideoObject")
        }

        assertEquals("Hittad", MiniJson.string(video?.get("name")))
        assertNull(MiniJson.findObject(parsed) { false })
    }

    @Test
    fun stringsHelperSplitsCommaSeparatedValues() {
        assertEquals(listOf("kök", "amatör"), MiniJson.strings("kök, amatör"))
        assertEquals(listOf("a", "b"), MiniJson.strings(listOf("a", "b")))
        assertEquals(emptyList<String>(), MiniJson.strings(null))
    }
}
