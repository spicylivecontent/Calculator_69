package com.calculator.app.ui.calculator

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Back-knappen fungerar som panic när vault är öppen
    BackHandler(enabled = viewModel.vaultMode != VaultMode.NONE) {
        viewModel.lockVault()
    }

    // Hantera biometrisk prompt
    LaunchedEffect(viewModel.shouldShowBiometric) {
        if (viewModel.shouldShowBiometric && activity != null) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = { viewModel.onBiometricSuccess() },
                onFailedOrCancelled = { viewModel.onBiometricFailedOrCancelled() }
            )
        }
    }

    when (viewModel.vaultMode) {
        VaultMode.REAL -> RealVaultPlaceholder(onLock = { viewModel.lockVault() })
        VaultMode.DECOY -> DecoyVaultPlaceholder(onLock = { viewModel.lockVault() })
        VaultMode.NONE -> CalculatorUI(viewModel = viewModel)
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailedOrCancelled: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailedOrCancelled()
            }

            override fun onAuthenticationFailed() {
                // Låt användaren försöka igen
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Bekräfta identitet")
        .setSubtitle("Använd fingeravtryck eller ansikte för att öppna vaulten")
        .setNegativeButtonText("Avbryt")
        .build()

    val biometricManager = BiometricManager.from(activity)
    when (biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> {
            Toast.makeText(activity, "Ingen biometri tillgänglig – öppnar ändå", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }
}

@Composable
private fun CalculatorUI(viewModel: CalculatorViewModel) {
    val historyListState = rememberLazyListState()
    val inputScrollState = rememberScrollState()

    LaunchedEffect(viewModel.history.size) {
        if (viewModel.history.isNotEmpty()) {
            historyListState.animateScrollToItem(viewModel.history.size - 1)
        }
    }

    LaunchedEffect(viewModel.input) {
        inputScrollState.animateScrollTo(inputScrollState.maxValue)
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
            state = historyListState,
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            items(viewModel.history) { item ->
                Text(
                    text = item,
                    color = Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(viewModel.result, color = Color(0xFF90A4AE), fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(
                viewModel.input.ifEmpty { "0" },
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(inputScrollState)
            )
        }

        Column(modifier = Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcButton("C", Modifier.weight(1f), Color(0xFF546E7A)) { viewModel.onAction(CalculatorAction.Clear) }
                CalcIconButton(Modifier.weight(1f), Color(0xFF546E7A)) { viewModel.onAction(CalculatorAction.Delete) }
                CalcButton("%", Modifier.weight(1f), Color(0xFF546E7A)) { viewModel.onAction(CalculatorAction.Operation("%")) }
                CalcButton("/", Modifier.weight(1f), Color(0xFFFF9800)) { viewModel.onAction(CalculatorAction.Operation("/")) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcButton("7", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("7")) }
                CalcButton("8", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("8")) }
                CalcButton("9", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("9")) }
                CalcButton("*", Modifier.weight(1f), Color(0xFFFF9800)) { viewModel.onAction(CalculatorAction.Operation("*")) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcButton("4", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("4")) }
                CalcButton("5", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("5")) }
                CalcButton("6", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("6")) }
                CalcButton("-", Modifier.weight(1f), Color(0xFFFF9800)) { viewModel.onAction(CalculatorAction.Operation("-")) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcButton("1", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("1")) }
                CalcButton("2", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("2")) }
                CalcButton("3", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("3")) }
                CalcButton("+", Modifier.weight(1f), Color(0xFFFF9800)) { viewModel.onAction(CalculatorAction.Operation("+")) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalcButton("0", Modifier.weight(2f)) { viewModel.onAction(CalculatorAction.Number("0")) }
                CalcButton(".", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Decimal) }
                CalcButton("=", Modifier.weight(1f), Color(0xFFFF9800)) { viewModel.onAction(CalculatorAction.Calculate) }
            }
        }
    }
}

@Composable
fun RealVaultPlaceholder(onLock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))))
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFF00E676), modifier = Modifier.size(72.dp))
            Text("Vault Unlocked", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Riktiga vaulten (placeholder).\nTryck bakåt-knappen eller knappen nedan för att låsa.",
                color = Color(0xFFB0BEC5),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLock,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Lås igen (Panic)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun DecoyVaultPlaceholder(onLock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2C2C2C), Color(0xFF1E1E1E))))
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(72.dp))
            Text("Private Notes", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Inga anteckningar ännu.\n(Detta är decoy-vaulten)",
                color = Color(0xFFB0BEC5),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLock,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Stäng", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun CalcButton(
    symbol: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF37474F),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(symbol, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CalcIconButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF546E7A),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(Icons.Default.Backspace, "Delete", modifier = Modifier.size(28.dp))
    }
}
