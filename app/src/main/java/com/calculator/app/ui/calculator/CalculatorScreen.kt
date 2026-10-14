package com.calculator.app.ui.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calculator.app.security.BiometricUnlock
import com.calculator.app.ui.vault.VaultScreen

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Back-knappen = panic när vault är öppen
    BackHandler(enabled = viewModel.vaultMode != VaultMode.NONE) {
        viewModel.lockVault()
    }

    // Cancel prompts owned by a disposed screen. Late success callbacks must not
    // reopen a vault after cancellation, locking, or activity recreation.
    DisposableEffect(viewModel.shouldShowBiometric, activity) {
        val prompt = if (viewModel.shouldShowBiometric) {
            if (activity == null) {
                viewModel.onBiometricFailedOrCancelled()
                null
            } else {
                BiometricUnlock.prompt(
                    activity = activity,
                    onSuccess = viewModel::onBiometricSuccess,
                    onFailedOrCancelled = viewModel::onBiometricFailedOrCancelled
                )
            }
        } else null
        onDispose {
            if (prompt != null) {
                viewModel.onBiometricFailedOrCancelled()
                prompt.invoke()
            }
        }
    }

    if (viewModel.pinStorageError) {
        PinStorageErrorScreen()
        return
    }

    if (!viewModel.isPinConfigured) {
        PinSetupScreen(onSavePins = viewModel::configurePins)
        return
    }

    when (viewModel.vaultMode) {
        VaultMode.REAL -> VaultScreen(
            isDecoy = false,
            onLock = { viewModel.lockVault() },
            onChangeRealPin = viewModel::updateRealPin,
            onChangeDecoyPin = viewModel::updateDecoyPin
        )
        VaultMode.DECOY -> VaultScreen(isDecoy = true, onLock = { viewModel.lockVault() })
        VaultMode.NONE -> CalculatorUI(viewModel = viewModel)
    }
}

@Composable
private fun PinStorageErrorScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Åtkomstkoderna kan inte läsas",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Vaulten förblir låst eftersom sparade koder inte kunde dekrypteras. Appen ersätter inte " +
                "befintliga koder med nya och öppnar inte en okrypterad vault. Om du återställer appdata " +
                "förloras även sparat innehåll.",
            color = Color(0xFFB0BEC5),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
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
            // Taggarna används av UI-testerna i androidTest för att läsa displayen,
            // eftersom siffrorna även finns som knapptexter.
            Text(
                viewModel.result,
                color = Color(0xFF90A4AE),
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                modifier = Modifier.testTag("calculator_result")
            )
            Text(
                viewModel.input.ifEmpty { "0" },
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(inputScrollState).testTag("calculator_input")
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
