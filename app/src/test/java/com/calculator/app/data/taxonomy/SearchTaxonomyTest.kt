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
    fun everyCategoryHasSubcategoriesWithTerms() {
        assertTrue(SearchTaxonomy.categories.size >= 10)
        for (category in SearchTaxonomy.categories) {
            assertTrue("${category.id} saknar underkategorier", category.subcategories.size >= 5)
            for (subcategory in category.subcategories) {
                assertTrue("${subcategory.id} saknar termer", subcategory.terms.isNotEmpty())
                assertTrue(subcategory.terms.all { it.isNotBlank() })
                assertTrue(subcategory.label.isNotBlank())
            }
        }
    }

    @Test
    fun idsAreUnique() {
        val categoryIds = SearchTaxonomy.categories.map { it.id }
        assertEquals(categoryIds.size, categoryIds.toSet().size)

        val subcategoryIds = SearchTaxonomy.allSubcategories.map { it.id }
        assertEquals(subcategoryIds.size, subcategoryIds.toSet().size)
    }

    @Test
    fun noCategoryTermIsAgeBlocked() {
        for (category in SearchTaxonomy.categories) {
            assertFalse(
                "Kategorinamn blockeras: ${category.label}",
                AgeSafety.containsBlockedTerm(category.label)
            )
            assertFalse(
                "Kategoribeskrivning blockeras: ${category.description}",
                AgeSafety.containsBlockedTerm(category.description)
            )
            for (subcategory in category.subcategories) {
                assertFalse(
                    "Etikett blockeras: ${subcategory.label}",
                    AgeSafety.containsBlockedTerm(subcategory.label)
                )
                for (term in subcategory.terms) {
                    assertFalse(
                        "Term blockeras: $term (${subcategory.id})",
                        AgeSafety.containsBlockedTerm(term)
                    )
                }
            }
        }
    }

    @Test
    fun lookupHelpersWork() {
        assertNotNull(SearchTaxonomy.category("appearance"))
        assertNull(SearchTaxonomy.category("finns-inte"))
        assertNotNull(SearchTaxonomy.subcategory("kitchen"))
        assertNull(SearchTaxonomy.subcategory("finns-inte"))

        val kitchen = SearchTaxonomy.subcategory("kitchen")
        assertEquals("Kök", kitchen?.label)
        assertEquals(listOf("kitchen"), kitchen?.terms)

        assertTrue(SearchTaxonomy.subcategoriesFor("location").isNotEmpty())
        assertTrue(SearchTaxonomy.subcategoriesFor("finns-inte").isEmpty())
    }

    @Test
    fun termsAndLabelsForSelection() {
        val selection = setOf("blonde", "kitchen")

        assertEquals(listOf("blonde", "kitchen"), SearchTaxonomy.termsFor(selection))
        assertEquals(listOf("Blond", "Kök"), SearchTaxonomy.labelsFor(selection))
        assertEquals(setOf("appearance", "location"), SearchTaxonomy.categoryIdsFor(selection))
        assertTrue(SearchTaxonomy.termsFor(setOf("busty")).contains("busty"))
        assertTrue(SearchTaxonomy.termsFor(setOf("busty")).contains("big tits"))
    }
}
