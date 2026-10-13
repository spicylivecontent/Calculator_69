package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regler som kommer från nätet måste vara ofarliga att använda: bara HTTPS, bara kända
 * fält, och allt som inte validerar ska kastas utan att de övriga reglerna förstörs.
 */
class ScrapeRuleTest {

    private val validJson = """
        {
          "version": 2,
          "sites": {
            "mysite": {
              "baseUrl": "https://mysite.example",
              "searchUrl": "https://mysite.example/search?q={query}&p={page}",
              "hrefPattern": "/video/([a-z0-9-]+)",
              "idGroup": 1,
              "requireHrefContains": "/video/",
              "titleSelectors": ["h2", "a.title"],
              "thumbAttributes": ["data-src"],
              "durationSelectors": [".duration"],
              "limit": 24
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesAValidRuleWithAllFields() {
        val rules = ScrapeRules.parse(validJson)

        assertNotNull(rules)
        assertEquals(2, rules!!.version)
        assertEquals(1, rules.bySite.size)
        assertEquals(0, rules.rejected)

        val rule = rules.forSite("mysite")
        assertNotNull(rule)
        assertEquals("mysite", rule!!.siteName)
        assertEquals("https://mysite.example", rule.baseUrl)
        assertEquals(24, rule.limit)
        assertTrue(rule.supportsPagination)
        assertEquals(listOf("h2", "a.title"), rule.titleSelectors)
        val pattern = rule.hrefPattern!!
        assertTrue("mönstret ska hitta videolänken", pattern.containsMatchIn("/video/blonde-kitchen-1"))
        assertEquals("blonde-kitchen-1", pattern.find("/video/blonde-kitchen-1")!!.groupValues[1])
    }

    @Test
    fun buildsSearchUrlsWithEncodedQueryAndPage() {
        val rule = ScrapeRules.parse(validJson)!!.forSite("mysite")!!

        val url = rule.searchUrlFor("blonde kitchen", 3)

        assertEquals("https://mysite.example/search?q=blonde+kitchen&p=3", url)
        assertNull("tom fråga ska inte ge en URL", rule.searchUrlFor("   ", 1))
    }

    @Test
    fun rulesWithoutPaginationAreMarkedAsSuch() {
        val json = """
            { "version": 3, "sites": { "onesite": {
              "baseUrl": "https://onesite.example",
              "searchUrl": "https://onesite.example/s?q={query}"
            } } }
        """.trimIndent()

        val rule = ScrapeRules.parse(json)!!.forSite("onesite")

        assertNotNull(rule)
        assertFalse(rule!!.supportsPagination)
        assertEquals("https://onesite.example/s?q=abc", rule.searchUrlFor("abc", 7))
    }

    @Test
    fun insecureOrIncompleteRulesAreRejected() {
        val json = """
            {
              "version": 4,
              "sites": {
                "insecure": {
                  "baseUrl": "http://insecure.example",
                  "searchUrl": "https://insecure.example/s?q={query}"
                },
                "noquery": {
                  "baseUrl": "https://noquery.example",
                  "searchUrl": "https://noquery.example/search"
                },
                "cleartextsearch": {
                  "baseUrl": "https://cleartextsearch.example",
                  "searchUrl": "http://cleartextsearch.example/s?q={query}"
                },
                "missingfields": { "baseUrl": "https://missing.example" }
              }
            }
        """.trimIndent()

        val rules = ScrapeRules.parse(json)!!

        assertTrue("inga regler får godkännas: ${rules.bySite.keys}", rules.bySite.isEmpty())
        assertEquals(4, rules.rejected)
    }

    @Test
    fun anInvalidRegexOnlyDropsThatSite() {
        val json = """
            {
              "version": 5,
              "sites": {
                "broken": {
                  "baseUrl": "https://broken.example",
                  "searchUrl": "https://broken.example/s?q={query}",
                  "hrefPattern": "([a-z"
                },
                "working": {
                  "baseUrl": "https://working.example",
                  "searchUrl": "https://working.example/s?q={query}",
                  "hrefPattern": "/v/([0-9]+)"
                }
              }
            }
        """.trimIndent()

        val rules = ScrapeRules.parse(json)!!

        assertNull(rules.forSite("broken"))
        assertNotNull("den fungerande regeln ska behållas", rules.forSite("working"))
        assertEquals(1, rules.rejected)
    }

    @Test
    fun olderVersionsAreNeverApplied() {
        assertNotNull(ScrapeRules.parse(validJson))
        assertNull(
            "en lika gammal eller äldre version ska ignoreras",
            ScrapeRules.parse(validJson, minVersion = 2)
        )
        assertNull(
            "en äldre version får inte rulla tillbaka en nyare",
            ScrapeRules.parse("""{ "version": 1, "sites": {} }""", minVersion = 2)
        )
    }

    @Test
    fun brokenInputIsIgnoredInsteadOfCrashing() {
        assertNull(ScrapeRules.parse(null))
        assertNull(ScrapeRules.parse(""))
        assertNull(ScrapeRules.parse("{ inte json"))
        assertNull(ScrapeRules.parse("[]"))
        assertNull("version krävs", ScrapeRules.parse("""{ "sites": {} }"""))
        assertNull("version 0 är ogiltig", ScrapeRules.parse("""{ "version": 0, "sites": {} }"""))
    }

    @Test
    fun sizesAndValuesAreKeptWithinBounds() {
        val manySelectors = (1..40).joinToString(",") { "\"sel$it\"" }
        val json = """
            {
              "version": 6,
              "sites": {
                "bounds": {
                  "baseUrl": "https://bounds.example",
                  "searchUrl": "https://bounds.example/s?q={query}",
                  "titleSelectors": [$manySelectors],
                  "thumbAttributes": ["${"x".repeat(200)}"],
                  "durationSelectors": [],
                  "limit": 500,
                  "idGroup": 42
                }
              }
            }
        """.trimIndent()

        val rule = ScrapeRules.parse(json)!!.forSite("bounds")!!

        assertEquals("limit ska kapas", 60, rule.limit)
        assertEquals("idGroup utanför intervallet faller tillbaka på 1", 1, rule.idGroup)
        assertEquals("högst 20 selektorer", 20, rule.titleSelectors.size)
        assertTrue("orimligt långa selektorer kastas", rule.thumbAttributes.isEmpty())
    }

    @Test
    fun listingConfigFallsBackToTheSharedDefaults() {
        val json = """
            { "version": 7, "sites": { "plain": {
              "baseUrl": "https://plain.example",
              "searchUrl": "https://plain.example/s?q={query}"
            } } }
        """.trimIndent()

        val config = ScrapeRules.parse(json)!!.forSite("plain")!!.toListingConfig()

        assertEquals(ListingConfig.DEFAULT_TITLE_SELECTORS, config.titleSelectors)
        assertEquals(ListingConfig.DEFAULT_THUMB_ATTRIBUTES, config.thumbAttributes)
        assertEquals(ListingConfig.DEFAULT_DURATION_SELECTORS, config.durationSelectors)
        assertEquals(ListingConfig.DEFAULT_LIMIT, config.limit)
        assertNotNull(config.hrefPattern)
    }

    @Test
    fun newSitesAreAddedButExistingOnesAreReplaced() {
        val json = """
            { "version": 8, "sites": {
              "eporner": {
                "baseUrl": "https://eporner.example",
                "searchUrl": "https://eporner.example/s?q={query}"
              },
              "brandnew": {
                "baseUrl": "https://brandnew.example",
                "searchUrl": "https://brandnew.example/s?q={query}"
              }
            } }
        """.trimIndent()

        val rules = ScrapeRules.parse(json)!!
        val newOnes = rules.newSites(listOf("eporner", "xnxx"))

        assertEquals(listOf("brandnew"), newOnes.map { it.siteName })
        assertNotNull("en känd sajt ska kunna ersättas", rules.forSite("eporner"))
    }

    @Test
    fun siteNamesAreNormalisedAndValidated() {
        val json = """
            { "version": 9, "sites": {
              "MySite": {
                "baseUrl": "https://mysite.example",
                "searchUrl": "https://mysite.example/s?q={query}"
              },
              "fel namn!": {
                "baseUrl": "https://invalid.example",
                "searchUrl": "https://invalid.example/s?q={query}"
              }
            } }
        """.trimIndent()

        val rules = ScrapeRules.parse(json)!!

        assertNotNull("namnet ska normaliseras till gemener", rules.forSite("mysite"))
        assertNull(rules.forSite("fel namn!"))
        assertEquals(1, rules.rejected)
    }
}
