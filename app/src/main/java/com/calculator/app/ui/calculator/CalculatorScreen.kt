package com.calculator.app.ui.calculator

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = viewModel()
) {
    val historyListState = rememberLazyListState()
    val inputScrollState = rememberScrollState()

    // Auto-scroll historik
    LaunchedEffect(viewModel.history.size) {
        if (viewModel.history.isNotEmpty()) {
            historyListState.animateScrollToItem(viewModel.history.size - 1)
        }
    }

    // Auto-scroll input
    LaunchedEffect(viewModel.input) {
        inputScrollState.animateScrollTo(inputScrollState.maxValue)
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F2027),
            Color(0xFF203A43),
            Color(0xFF2C5364)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {

        // HISTORIK
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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

        // DISPLAY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = viewModel.result,
                color = Color(0xFF90A4AE),
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )

            Text(
                text = viewModel.input.ifEmpty { "0" },
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(inputScrollState)
            )
        }

        // KNAPPRAD
        Column(
            modifier = Modifier.weight(1.6f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rad 1: C, DEL, %, /
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("C", Modifier.weight(1f), Color(0xFF546E7A)) {
                    viewModel.onAction(CalculatorAction.Clear)
                }
                CalcIconButton(Modifier.weight(1f), Color(0xFF546E7A)) {
                    viewModel.onAction(CalculatorAction.Delete)
                }
                CalcButton("%", Modifier.weight(1f), Color(0xFF546E7A)) {
                    viewModel.onAction(CalculatorAction.Operation("%"))
                }
                CalcButton("/", Modifier.weight(1f), Color(0xFFFF9800)) {
                    viewModel.onAction(CalculatorAction.Operation("/"))
                }
            }

            // Rad 2: 7 8 9 *
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("7", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("7")) }
                CalcButton("8", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("8")) }
                CalcButton("9", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("9")) }
                CalcButton("*", Modifier.weight(1f), Color(0xFFFF9800)) {
                    viewModel.onAction(CalculatorAction.Operation("*"))
                }
            }

            // Rad 3: 4 5 6 -
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("4", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("4")) }
                CalcButton("5", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("5")) }
                CalcButton("6", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("6")) }
                CalcButton("-", Modifier.weight(1f), Color(0xFFFF9800)) {
                    viewModel.onAction(CalculatorAction.Operation("-"))
                }
            }

            // Rad 4: 1 2 3 +
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("1", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("1")) }
                CalcButton("2", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("2")) }
                CalcButton("3", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Number("3")) }
                CalcButton("+", Modifier.weight(1f), Color(0xFFFF9800)) {
                    viewModel.onAction(CalculatorAction.Operation("+"))
                }
            }

            // Rad 5: 0 . =
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("0", Modifier.weight(2f)) { viewModel.onAction(CalculatorAction.Number("0")) }
                CalcButton(".", Modifier.weight(1f)) { viewModel.onAction(CalculatorAction.Decimal) }
                CalcButton("=", Modifier.weight(1f), Color(0xFFFF9800)) {
                    viewModel.onAction(CalculatorAction.Calculate)
                }
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
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = symbol,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
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
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Backspace,
            contentDescription = "Delete",
            modifier = Modifier.size(28.dp)
        )
    }
}
