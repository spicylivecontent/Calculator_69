package com.calculator.app.ui.calculator

import java.util.Locale
import kotlin.math.abs

/** Keep calculator results reusable as expressions without rounding tiny values to zero. */
internal object CalculatorResultFormatter {
    fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        if (value == 0.0) return "0"
        if (abs(value) < 0.00000001 || abs(value) >= 1.0e16) return value.toString()
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    }
}
