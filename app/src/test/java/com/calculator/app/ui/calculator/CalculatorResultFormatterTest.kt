package com.calculator.app.ui.calculator

import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorResultFormatterTest {
    @Test
    fun normalResultsRemainCompact() {
        assertEquals("42", CalculatorResultFormatter.format(42.0))
        assertEquals("0", CalculatorResultFormatter.format(-0.0))
        assertEquals("0.33333333", CalculatorResultFormatter.format(1.0 / 3.0))
        assertEquals("Error", CalculatorResultFormatter.format(Double.NaN))
        assertEquals("Error", CalculatorResultFormatter.format(Double.POSITIVE_INFINITY))
    }

    @Test
    fun tinyAndHugeResultsAreNotRoundedToZeroAndCanBeCalculatedAgain() {
        for (value in listOf(1.0e-9, -1.0e-20, 1.0e20, -1.0e100)) {
            val formatted = CalculatorResultFormatter.format(value)
            assertEquals(value, ExpressionBuilder(formatted).build().evaluate(), 0.0)
        }
    }
}
