package com.calculator.app.ui.calculator

sealed class CalculatorAction {
    data class Number(val number: String) : CalculatorAction()
    data class Operation(val operation: String) : CalculatorAction()
    object Clear : CalculatorAction()
    object Delete : CalculatorAction()
    object Calculate : CalculatorAction()
    object Decimal : CalculatorAction()
}
