package com.calculator.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calculator.app.ui.calculator.CalculatorScreen
import com.calculator.app.ui.calculator.CalculatorViewModel
import com.calculator.app.ui.calculator.VaultMode
import com.calculator.app.ui.theme.CalculatorTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === FLAG_SECURE ===
        // Blockerar skärmdumpar och gör att app-switchern visar svart/blank istället för innehållet
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        setContent {
            CalculatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: CalculatorViewModel = viewModel()

                    // Hantera excludeFromRecents beroende på vault-läge
                    ManageRecentsVisibility(viewModel.vaultMode)

                    CalculatorScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Extra säkerhet: om vault är öppen när appen går i bakgrunden
        // kan man välja att automatiskt låsa (valfritt – just nu gör vi det inte)
    }
}

/**
 * Styr om appen ska synas i "Senaste appar" eller inte.
 * När vault är öppen → dölj från recent apps.
 */
@Composable
private fun ManageRecentsVisibility(vaultMode: VaultMode) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    DisposableEffect(vaultMode) {
        if (activity != null) {
            if (vaultMode != VaultMode.NONE) {
                // Dölj från recent apps när vault är öppen
                activity.setTaskDescription(
                    android.app.ActivityManager.TaskDescription("Calculator")
                )
                // Äldre API: excludeFromRecents
                activity.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
        onDispose { }
    }
}
