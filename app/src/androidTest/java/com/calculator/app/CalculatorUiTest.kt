package com.calculator.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-tester som körs på en riktig Android-enhet (emulator i CI). De täcker det som
 * enhetstesterna inte kommer åt: att skärmarna faktiskt ritas, att knappar och fält
 * reagerar, och att en felaktig kod inte öppnar vaulten.
 *
 * Testerna rör aldrig vaulten efter upplåsning (där finns databasen), utan stannar vid
 * kalkylatorn, kodvalet och den kod som kräver biometri.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorUiTest {

    // Städningen måste ligga före Compose-regeln, annars hinner aktiviteten starta med
    // ett gammalt tillstånd inläst.
    @get:Rule(order = 0)
    val cleanState = CleanAppStateRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pinSetupRejectsMismatchedConfirmation() {
        fillPinSetup(main = "1357", confirmMain = "1358", reserve = "2468")
        compose.onNodeWithText("Spara koder").performClick()

        compose.onNodeWithText("Bekräftelserna stämmer inte.").assertExists()
        compose.onNodeWithText("Ställ in åtkomstkoder").assertExists()
        compose.onNodeWithTag(INPUT_TAG).assertDoesNotExist()
    }

    @Test
    fun pinSetupRejectsEqualCodes() {
        fillPinSetup(main = "1357", reserve = "1357")
        compose.onNodeWithText("Spara koder").performClick()

        compose.onNodeWithText("Huvudkoden och reservkoden måste vara olika.").assertExists()
    }

    @Test
    fun savingThePinsShowsTheCalculator() {
        configurePins()

        compose.onNodeWithText("Ställ in åtkomstkoder").assertDoesNotExist()
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("0")
    }

    @Test
    fun typedDigitsCanBeClearedAndDeleted() {
        configurePins()

        press("7")
        press("8")
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("78")

        press("C")
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("0")

        press("1")
        press("0")
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("10")

        compose.onNode(hasClickAction() and hasContentDescription("Delete")).performClick()
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("1")
    }

    @Test
    fun aCalculationShowsItsResult() {
        configurePins()

        press("6")
        press("*")
        press("7")
        press("=")

        compose.onNodeWithTag(RESULT_TAG).assertTextEquals("42")
        compose.onNodeWithText("6*7 = 42").assertExists()
    }

    @Test
    fun aWrongCodeIsCalculatedInsteadOfOpeningTheVault() {
        configurePins()

        listOf("1", "3", "5", "8").forEach { press(it) }
        press("=")

        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("1358")
        compose.onNodeWithText("1358 = 1358").assertExists()
        compose.onNodeWithTag(RESULT_TAG).assertTextEquals("1358")
        // Kalkylatorn ska fortfarande vara kvar – ingen vault öppnades.
        compose.onNode(hasClickAction() and hasText("C")).assertExists()
    }

    @Test
    fun theMainCodeIsNotCalculatedButStartsTheUnlock() {
        configurePins()

        listOf("1", "3", "5", "7").forEach { press(it) }
        press("=")

        // Rätt kod töms och räknas aldrig ut (den går vidare till biometri i stället).
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("0")
        compose.onNodeWithText("1357 = 1357").assertDoesNotExist()
    }

    @Test
    fun negativeNumbersAndRecoveryAfterDivisionByZeroWork() {
        configurePins()
        listOf("-", "3", "*", "-", "2", "=").forEach { press(it) }
        compose.onNodeWithTag(RESULT_TAG).assertTextEquals("6")
        listOf("/", "0", "=").forEach { press(it) }
        compose.onNodeWithTag(RESULT_TAG).assertTextEquals("Error")
        listOf("7", "=", "+", "1", "=").forEach { press(it) }
        compose.onNodeWithTag(RESULT_TAG).assertTextEquals("8")
    }

    // --- hjälpare ---

    private fun configurePins(main: String = MAIN_PIN, reserve: String = RESERVE_PIN) {
        fillPinSetup(main = main, reserve = reserve)
        compose.onNodeWithText("Spara koder").performClick()
        compose.onNodeWithTag(INPUT_TAG).assertTextEquals("0")
    }

    private fun fillPinSetup(
        main: String,
        confirmMain: String = main,
        reserve: String,
        confirmReserve: String = reserve
    ) {
        val fields = compose.onAllNodes(hasSetTextAction())
        listOf(main, confirmMain, reserve, confirmReserve).forEachIndexed { index, value ->
            fields[index].performClick()
            fields[index].performTextInput(value)
        }
        // Tangentbordet får inte ligga kvar över "Spara koder" när nästa steg klickar.
        runCatching { Espresso.closeSoftKeyboard() }
    }

    /** Trycker på en kalkylatorknapp – bara knappar har klickåtgärd, inte displayen. */
    private fun press(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performClick()
    }

    private companion object {
        /** Testtaggarna från CalculatorScreen. */
        const val INPUT_TAG = "calculator_input"
        const val RESULT_TAG = "calculator_result"

        const val MAIN_PIN = "1357"
        const val RESERVE_PIN = "2468"
    }
}
