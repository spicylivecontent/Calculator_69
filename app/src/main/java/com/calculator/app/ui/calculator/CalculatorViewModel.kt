package com.calculator.app.ui.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.objecthunter.exp4j.ExpressionBuilder

enum class VaultMode {
    NONE,           // Miniräknare
    REAL,           // Riktiga vaulten
    DECOY           // Falsk/tom vault
}

class CalculatorViewModel : ViewModel() {

    // === PIN-inställningar ===
    private var realPin: String = "6969"
    private var decoyPin: String = "1234"

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

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Number -> input += action.number
            is CalculatorAction.Operation -> {
                if (input.isNotEmpty() && !isLastCharOperator()) {
                    input += action.operation
                }
            }
            is CalculatorAction.Clear -> {
                input = ""
                result = ""
            }
            is CalculatorAction.Delete -> {
                if (input.isNotEmpty()) input = input.dropLast(1)
            }
            is CalculatorAction.Decimal -> {
                if (input.isEmpty() || isLastCharOperator()) {
                    input += "0."
                } else if (!currentNumberHasDecimal()) {
                    input += "."
                }
            }
            is CalculatorAction.Calculate -> performCalculation()
        }
    }

    private fun isLastCharOperator(): Boolean {
        if (input.isEmpty()) return true
        return input.last() in listOf('+', '-', '*', '/', '%')
    }

    private fun currentNumberHasDecimal(): Boolean {
        val parts = input.split('+', '-', '*', '/', '%')
        return parts.last().contains('.')
    }

    private fun performCalculation() {
        if (input.isBlank()) return

        when (input) {
            realPin -> {
                input = ""
                result = ""
                pendingVaultMode = VaultMode.REAL
                shouldShowBiometric = true
            }
            decoyPin -> {
                input = ""
                result = ""
                vaultMode = VaultMode.DECOY
            }
            else -> {
                try {
                    val expression = ExpressionBuilder(input).build()
                    val res = expression.evaluate()

                    val finalResult = if (res == res.toLong().toDouble()) {
                        res.toLong().toString()
                    } else {
                        String.format("%.8f", res).trimEnd('0').trimEnd('.')
                    }

                    result = finalResult
                    history = (history + "$input = $finalResult").takeLast(15)
                    input = finalResult
                } catch (e: Exception) {
                    result = "Error"
                }
            }
        }
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

    /** Panic / Lås vaulten omedelbart */
    fun lockVault() {
        vaultMode = VaultMode.NONE
        input = ""
        result = ""
        shouldShowBiometric = false
        pendingVaultMode = VaultMode.NONE
    }

    fun updateRealPin(newPin: String) {
        if (newPin.length in 4..12 && newPin.all { it.isDigit() }) {
            realPin = newPin
        }
    }

    fun updateDecoyPin(newPin: String) {
        if (newPin.length in 4..12 && newPin.all { it.isDigit() }) {
            decoyPin = newPin
        }
    }
}
