package com.calculator.app.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinSetupScreen(onSavePins: (realPin: String, decoyPin: String) -> Boolean) {
    var realPin by remember { mutableStateOf("") }
    var confirmRealPin by remember { mutableStateOf("") }
    var decoyPin by remember { mutableStateOf("") }
    var confirmDecoyPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ställ in åtkomstkoder", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Välj en huvudkod och en separat reservkod. Koderna krypteras på enheten. Huvudkoden kräver också biometrisk autentisering.",
            color = Color(0xFFB0BEC5),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))

        PinField("Huvudkod (4–12 siffror)", realPin) { realPin = it }
        PinField("Bekräfta huvudkod", confirmRealPin) { confirmRealPin = it }
        PinField("Reservkod (4–12 siffror)", decoyPin) { decoyPin = it }
        PinField("Bekräfta reservkod", confirmDecoyPin) { confirmDecoyPin = it }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                errorMessage = when {
                    !realPin.isValidPin() || !decoyPin.isValidPin() -> "Båda koderna måste vara 4–12 siffror."
                    realPin != confirmRealPin || decoyPin != confirmDecoyPin -> "Bekräftelserna stämmer inte."
                    realPin == decoyPin -> "Huvudkoden och reservkoden måste vara olika."
                    !onSavePins(realPin, decoyPin) -> "Koderna kunde inte sparas säkert. Försök igen."
                    else -> null
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            Text("Spara koder", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            if (entered.length <= 12 && entered.all { it.isDigit() }) onValueChange(entered)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00E676),
            unfocusedBorderColor = Color(0xFF607D8B),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF00E676),
            unfocusedLabelColor = Color(0xFF90A4AE),
            cursorColor = Color(0xFF00E676)
        )
    )
}

private fun String.isValidPin(): Boolean = length in 4..12 && all { it.isDigit() }
