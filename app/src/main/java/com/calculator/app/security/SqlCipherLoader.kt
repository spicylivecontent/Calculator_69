package com.calculator.app.security

/**
 * Laddar SQLCiphers inbyggda bibliotek en gång.
 *
 * Biblioteket måste vara laddat innan databasen öppnas. Om det inte kan laddas
 * returnerar [load] false; databasen ska då förbli stängd i stället för att falla
 * tillbaka till en okrypterad fil.
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
