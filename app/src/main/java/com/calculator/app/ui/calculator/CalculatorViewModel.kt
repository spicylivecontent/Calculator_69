package com.calculator.app.ui.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.objecthunter.exp4j.ExpressionBuilder

class CalculatorViewModel : ViewModel() {

    var input by mutableStateOf("")
        private set

    var result by mutableStateOf("")
        private set

    var history by mutableStateOf<List<String>>(emptyList())
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

        try {
            val expression = ExpressionBuilder(input).build()
            val res = expression.evaluate()

            val finalResult = if (res == res.toLong().toDouble()) {
                res.toLong().toString()
            } else {
                // Begränsa decimaler
                String.format("%.8f", res).trimEnd('0').trimEnd('.')
            }

            result = finalResult

            // Lägg till i historik
            val entry = "$input = $finalResult"
            history = (history + entry).takeLast(15)

            // Visa resultatet som ny input så man kan fortsätta räkna
            input = finalResult

        } catch (e: Exception) {
            result = "Error"
        }
    }
}
