package com.calculator.app.ui.calculator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.calculator.app.security.PinStore
import net.objecthunter.exp4j.ExpressionBuilder

enum class VaultMode {
    NONE,
    REAL,
    DECOY
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val pinStore = PinStore(application)
    private var realPin: String? = pinStore.getRealPin()
    private var decoyPin: String? = pinStore.getDecoyPin()

    var isPinConfigured by mutableStateOf(realPin != null && decoyPin != null)
        private set

    var input by mutableStateOf("")
        private set

    var result by mutableStateOf("")
        private set

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    var vaultMode by mutableStateOf(VaultMode.NONE)
        private set

    var shouldShowBiometric by mutableStateOf(false)
        private set

    private var pendingVaultMode: VaultMode = VaultMode.NONE
    private var justCalculated = false

    fun configurePins(newRealPin: String, newDecoyPin: String): Boolean {
        if (!isValidPin(newRealPin) || !isValidPin(newDecoyPin) || newRealPin == newDecoyPin) {
            return false
        }
        if (!pinStore.savePins(newRealPin, newDecoyPin)) return false

        realPin = newRealPin
        decoyPin = newDecoyPin
        isPinConfigured = true
        return true
    }

    fun updateRealPin(newPin: String): Boolean {
        if (!isValidPin(newPin) || newPin == decoyPin || !pinStore.saveRealPin(newPin)) {
            return false
        }
        realPin = newPin
        return true
    }

    fun updateDecoyPin(newPin: String): Boolean {
        if (!isValidPin(newPin) || newPin == realPin || !pinStore.saveDecoyPin(newPin)) {
            return false
        }
        decoyPin = newPin
        return true
    }

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Number -> {
                startNewEntryIfNeeded()
                input += action.number
            }
            is CalculatorAction.Operation -> {
                val unaryMinus = action.operation == "-" && (input.isEmpty() || input.lastOrNull() in listOf('+', '*', '/', '%'))
                if (result != ERROR_RESULT && (unaryMinus || (input.isNotEmpty() && !isLastCharOperator()))) {
                    input += action.operation
                    result = ""
                    justCalculated = false
                }
            }
            is CalculatorAction.Clear -> {
                input = ""
                result = ""
                justCalculated = false
            }
            is CalculatorAction.Delete -> {
                if (input.isNotEmpty()) input = input.dropLast(1)
                result = ""
                justCalculated = false
            }
            is CalculatorAction.Decimal -> {
                startNewEntryIfNeeded()
                if (input.isEmpty() || isLastCharOperator()) {
                    input += "0."
                } else if (!currentNumberHasDecimal()) {
                    input += "."
                }
            }
            is CalculatorAction.Calculate -> performCalculation()
        }
    }

    private fun startNewEntryIfNeeded() {
        if (justCalculated || result == ERROR_RESULT) {
            input = ""
            result = ""
            justCalculated = false
        }
    }

    private fun isLastCharOperator(): Boolean = when (input.lastOrNull()) {
        '+', '-', '*', '/', '%' -> true
        else -> false
    }

    private fun currentNumberHasDecimal(): Boolean {
        val parts = input.split('+', '-', '*', '/', '%')
        return parts.last().contains('.')
    }

    private fun performCalculation() {
        if (input.isBlank()) return

        when {
            !justCalculated && input == realPin -> {
                clearCalculator()
                pendingVaultMode = VaultMode.REAL
                shouldShowBiometric = true
            }
            !justCalculated && input == decoyPin -> {
                clearCalculator()
                vaultMode = VaultMode.DECOY
            }
            else -> {
                try {
                    val value = ExpressionBuilder(input).build().evaluate()
                    val finalResult = formatResult(value)
                    if (finalResult == ERROR_RESULT) {
                        result = ERROR_RESULT
                        justCalculated = true
                        return
                    }

                    result = finalResult
                    history = (history + "$input = $finalResult").takeLast(MAX_HISTORY_ITEMS)
                    input = finalResult
                    justCalculated = true
                } catch (_: Exception) {
                    result = ERROR_RESULT
                    justCalculated = true
                }
            }
        }
    }

    private fun formatResult(value: Double): String = CalculatorResultFormatter.format(value)

    private fun clearCalculator() {
        input = ""
        result = ""
        justCalculated = false
    }

    fun onBiometricSuccess() {
        shouldShowBiometric = false
        vaultMode = pendingVaultMode
        pendingVaultMode = VaultMode.NONE
    }

    fun onBiometricFailedOrCancelled() {
        shouldShowBiometric = false
        pendingVaultMode = VaultMode.NONE
    }

    /** Panic / lock the vault immediately. */
    fun lockVault() {
        vaultMode = VaultMode.NONE
        clearCalculator()
        shouldShowBiometric = false
        pendingVaultMode = VaultMode.NONE
    }

    private fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    private companion object {
        const val ERROR_RESULT = "Error"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 12
        const val MAX_HISTORY_ITEMS = 15
    }
}
