package com.calculator.app.security

/**
 * Laddar SQLCiphers inbyggda bibliotek en gång.
 *
 * Biblioteket måste vara laddat innan databasen öppnas. Kan det inte laddas (till
 * exempel på en enhet där det inbyggda biblioteket saknas) returnerar [load] false och
 * appen faller tillbaka på en okrypterad databas i stället för att krascha.
 */
internal object SqlCipherLoader {

    @Volatile
    private var loaded: Boolean? = null

    fun load(): Boolean {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val result = runCatching { System.loadLibrary("sqlcipher") }.isSuccess
            loaded = result
            return result
        }
    }
}
