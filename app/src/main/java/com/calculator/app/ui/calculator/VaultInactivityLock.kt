package com.calculator.app.ui.calculator

import com.calculator.app.data.AutoLock

/**
 * Spårar senaste interaktion i vault-läge och avgör om timeout-lås ska triggas.
 * Används tillsammans med [AutoLock.AFTER_1_MIN] / [AutoLock.AFTER_5_MIN].
 */
class VaultInactivityLock {

    @Volatile
    private var lastInteractionMs: Long = System.currentTimeMillis()

    fun touch() {
        lastInteractionMs = System.currentTimeMillis()
    }

    /** True om vaulten bör låsas enligt [autoLock]-policy. */
    fun shouldLock(autoLock: AutoLock): Boolean {
        val timeout = autoLock.timeoutMillis
        if (timeout <= 0L) return false // ON_BACKGROUND / NEVER handled elsewhere
        return System.currentTimeMillis() - lastInteractionMs >= timeout
    }
}
