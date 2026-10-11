package com.calculator.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palett för vault-delen. Kalkylatorn behåller sitt eget tema.
val VaultBackground = Color(0xFF0A0A12)
val VaultBackgroundAlt = Color(0xFF101019)
val VaultSurface = Color(0xFF16161F)
val VaultSurfaceHigh = Color(0xFF1E1E2C)
val VaultBorder = Color(0xFF272738)
val VaultAccent = Color(0xFF00E676)
val VaultAccentSoft = Color(0xFF17402C)
val VaultPink = Color(0xFFFF4D8D)
val VaultBlue = Color(0xFF4FC3F7)
val VaultAmber = Color(0xFFFFC107)
val VaultTextPrimary = Color(0xFFF4F4F7)
val VaultTextSecondary = Color(0xFF98A2B3)
val VaultError = Color(0xFFFF5A5A)

val VaultGradient: Brush = Brush.verticalGradient(
    colors = listOf(VaultBackground, VaultBackgroundAlt, Color(0xFF14142A))
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VaultAccent,
            onPrimary = Color.Black,
            primaryContainer = VaultAccentSoft,
            onPrimaryContainer = VaultAccent,
            secondary = VaultPink,
            onSecondary = Color.Black,
            tertiary = VaultBlue,
            onTertiary = Color.Black,
            background = VaultBackground,
            onBackground = VaultTextPrimary,
            surface = VaultSurface,
            onSurface = VaultTextPrimary,
            surfaceVariant = VaultSurfaceHigh,
            onSurfaceVariant = VaultTextSecondary,
            outline = VaultBorder,
            outlineVariant = VaultBorder,
            error = VaultError,
            onError = Color.Black
        ),
        content = content
    )
}
