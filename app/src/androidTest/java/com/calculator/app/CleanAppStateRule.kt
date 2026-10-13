package com.calculator.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Ser till att varje UI-test börjar från en ren app: inga koder, ingen historik och ingen
 * databas. Regeln ligger före Compose-regeln (order 0) så att städningen sker innan
 * aktiviteten startas och ViewModel hinner läsa in ett gammalt tillstånd.
 *
 * Keystore-nycklarna lämnas kvar med flit – de behövs för att koderna ska kunna sparas,
 * och de innehåller inget som påverkar testet.
 */
class CleanAppStateRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                clean()
                base.evaluate()
            }
        }

    private fun clean() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        PREFERENCES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }

        // deleteDatabase tar även bort -wal, -shm och -journal.
        DATABASES.forEach { name -> context.deleteDatabase(name) }
    }

    private companion object {
        val PREFERENCES = listOf(
            "calculator_access",        // PinStore
            "calculator_app_prefs",     // AppPreferences
            "calculator_playback",      // PlaybackStore
            "calculator_content_access", // åldersbekräftelsen
            "calculator_scrape_rules",  // ScrapeRuleStore
            "calculator_vault_secret"   // databasens nyckel
        )

        val DATABASES = listOf(
            "calculator_vault_enc.db",
            "calculator_vault.db"
        )
    }
}
