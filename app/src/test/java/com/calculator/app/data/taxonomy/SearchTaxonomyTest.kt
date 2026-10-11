package com.calculator.app.data.taxonomy

import com.calculator.app.ai.AgeSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTaxonomyTest {

    @Test
    fun everyCategoryHasLabelEmojiDescriptionAndSubcategories() {
        assertTrue("taxonomin ska ha flera kategorier", SearchTaxonomy.categories.size >= 10)

        for (category in SearchTaxonomy.categories) {
            assertTrue("kategori utan id", category.id.isNotBlank())
            assertTrue("kategori utan etikett: ${category.id}", category.label.isNotBlank())
            assertTrue("kategori utan emoji: ${category.id}", category.emoji.isNotBlank())
            assertTrue("kategori utan beskrivning: ${category.id}", category.description.isNotBlank())
            assertTrue(
                "kategorin ${category.id} har för få underkategorier",
                category.subcategories.size >= 3
            )
        }
    }

    @Test
    fun idsAreUniqueAndLookupsWork() {
        val categoryIds = SearchTaxonomy.categories.map { it.id }
        assertEquals("kategori-id:n måste vara unika", categoryIds.size, categoryIds.distinct().size)

        val subcategoryIds = SearchTaxonomy.allSubcategories.map { it.id }
        assertEquals("underkategori-id:n måste vara unika", subcategoryIds.size, subcategoryIds.distinct().size)
        assertEquals(
            "allSubcategories ska innehålla varje underkategori",
            SearchTaxonomy.categories.sumOf { it.subcategories.size },
            SearchTaxonomy.allSubcategories.size
        )

        assertNotNull(SearchTaxonomy.category("appearance"))
        assertNull(SearchTaxonomy.category("finns-inte"))
        assertNull(SearchTaxonomy.subcategory("finns-inte"))
        assertNotNull(SearchTaxonomy.subcategory("blonde"))
        assertTrue(SearchTaxonomy.subcategoriesFor("finns-inte").isEmpty())
    }

    @Test
    fun everySubcategoryHasUsableTerms() {
        for (subcategory in SearchTaxonomy.allSubcategories) {
            assertTrue("underkategori utan etikett: ${subcategory.id}", subcategory.label.isNotBlank())
            assertTrue("underkategori utan termer: ${subcategory.id}", subcategory.terms.isNotEmpty())
            for (term in subcategory.terms) {
                assertTrue("tom term i ${subcategory.id}", term.isNotBlank())
                assertEquals(
                    "termerna ska vara gemener så att de matchar sajterna (${subcategory.id}: $term)",
                    term.lowercase(),
                    term
                )
            }
            assertEquals(
                "primaryTerm ska vara första termen",
                subcategory.terms.first(),
                subcategory.primaryTerm
            )
        }
    }

    @Test
    fun lookupsReturnSelectedTermsAndLabels() {
        assertEquals(listOf("blonde"), SearchTaxonomy.termsFor(listOf("blonde")))
        assertEquals(listOf("Blond"), SearchTaxonomy.labelsFor(listOf("blonde")))
        assertEquals(setOf("appearance"), SearchTaxonomy.categoryIdsFor(listOf("blonde")))
        assertEquals(emptyList<String>(), SearchTaxonomy.termsFor(listOf("finns-inte")))
        assertEquals(emptyList<String>(), SearchTaxonomy.labelsFor(listOf("finns-inte")))
    }

    @Test
    fun termsAreDeduplicatedWhenSeveralSubcategoriesAreSelected() {
        val terms = SearchTaxonomy.termsFor(listOf("blonde", "brunette"))
        assertEquals(terms.distinct(), terms)
        assertTrue(terms.containsAll(listOf("blonde", "brunette")))
    }

    @Test
    fun noCategoryOrSubcategoryUsesBlockedAgeTerms() {
        for (category in SearchTaxonomy.categories) {
            assertFalse(
                "kategorinamnet ${category.label} innehåller en blockerad term",
                AgeSafety.containsBlockedTerm(category.label)
            )
            assertFalse(
                "kategoribeskrivningen ${category.description} innehåller en blockerad term",
                AgeSafety.containsBlockedTerm(category.description)
            )
        }
        for (subcategory in SearchTaxonomy.allSubcategories) {
            assertFalse(
                "underkategorin ${subcategory.label} innehåller en blockerad term",
                AgeSafety.containsBlockedTerm(subcategory.label)
            )
            assertFalse(
                "termerna i ${subcategory.id} innehåller en blockerad term",
                AgeSafety.containsBlockedTerm(subcategory.terms.joinToString(" "))
            )
        }
    }
}
