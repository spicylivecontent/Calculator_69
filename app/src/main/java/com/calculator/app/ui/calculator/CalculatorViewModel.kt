package com.calculator.app.ui.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.objecthunter.exp4j.ExpressionBuilder

class CalculatorViewModel : ViewModel() {

    // === PIN-inställning (kan ändras senare i Settings) ===
    // Standard-PIN: 6969
    private var secretPin: String = "6969"

    var input by mutableStateOf("")
        private set

    var result by mutableStateOf("")
        private set

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    // true när rätt PIN har angetts
    var isVaultUnlocked by mutableStateOf(false)
        private set

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Number -> {
                input += action.number
            }
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
                if (input.isNotEmpty()) {
                    input = input.dropLast(1)
                }
            }
            is CalculatorAction.Decimal -> {
                if (input.isEmpty() || isLastCharOperator()) {
                    input += "0."
                } else if (!currentNumberHasDecimal()) {
                    input += "."
                }
            }
            is CalculatorAction.Calculate -> {
                performCalculation()
            }
        }
    }

    private fun isLastCharOperator(): Boolean {
        if (input.isEmpty()) return true
        val last = input.last()
        return last in listOf('+', '-', '*', '/', '%')
    }

    private fun currentNumberHasDecimal(): Boolean {
        val parts = input.split('+', '-', '*', '/', '%')
        return parts.last().contains('.')
    }

    private fun performCalculation() {
        if (input.isBlank()) return

        // === HEMLIG PIN-KOLL ===
        // Om användaren bara skrivit PIN-koden (inga operatorer) och trycker =
        if (input == secretPin) {
            // Rensa allt och lås upp vaulten
            input = ""
            result = ""
            isVaultUnlocked = true
            return
        }

        // Vanlig beräkning
        try {
            val expression = ExpressionBuilder(input).build()
            val res = expression.evaluate()

            val finalResult = if (res == res.toLong().toDouble()) {
                res.toLong().toString()
            } else {
                String.format("%.8f", res).trimEnd('0').trimEnd('.')
            }

            result = finalResult

            val entry = "$input = $finalResult"
            history = (history + entry).takeLast(15)

            input = finalResult

        } catch (e: Exception) {
            result = "Error"
        }
    }

    /** Anropas när man vill stänga vaulten och gå tillbaka till miniräknaren */
    fun lockVault() {
        isVaultUnlocked = false
        input = ""
        result = ""
    }

    /** Tillåter att ändra PIN senare (används i Settings) */
    fun updatePin(newPin: String) {
        if (newPin.length in 4..12 && newPin.all { it.isDigit() }) {
            secretPin = newPin
        }
    }
}
