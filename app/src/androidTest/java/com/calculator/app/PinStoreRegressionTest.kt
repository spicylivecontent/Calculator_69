package com.calculator.app

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.calculator.app.ui.calculator.CalculatorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Authentication storage failures must not turn the PIN screen into an unlocked setup flow. */
class PinStoreRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun unreadableStoredPinsFailClosedInsteadOfAllowingCredentialReset() {
        val prefs = context.getSharedPreferences("calculator_access", Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putString("real_pin_encrypted", "corrupt-ciphertext")
            .putString("decoy_pin_encrypted", "corrupt-ciphertext")
            .commit()

        try {
            val viewModel = CalculatorViewModel(context.applicationContext as Application)

            assertTrue(viewModel.pinStorageError)
            assertFalse(viewModel.isPinConfigured)
            assertFalse(viewModel.configurePins("1357", "2468"))
            assertEquals("corrupt-ciphertext", prefs.getString("real_pin_encrypted", null))
            assertEquals("corrupt-ciphertext", prefs.getString("decoy_pin_encrypted", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
