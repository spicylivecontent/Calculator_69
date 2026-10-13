package com.calculator.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calculator.app.data.AppPreferences
import com.calculator.app.data.AutoLock
import com.calculator.app.ui.calculator.CalculatorScreen
import com.calculator.app.ui.calculator.CalculatorViewModel
import com.calculator.app.ui.calculator.VaultMode
import com.calculator.app.ui.theme.CalculatorTheme

class MainActivity : FragmentActivity() {

    /** Används av [onStop] för att kunna låsa vaulten när appen lämnas. */
    private var calculatorViewModel: CalculatorViewModel? = null
    private val preferences by lazy { AppPreferences(this) }

    override fun onStop() {
        // Lämnar användaren appen låses vaulten direkt – men inte vid en vridning av
        // skärmen, då byggs aktiviteten bara om och allt är kvar.
        if (!isChangingConfigurations && preferences.autoLock == AutoLock.ON_BACKGROUND) {
            calculatorViewModel?.lockVault()
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: blockerar skärmdumpar + blank i app-switcher
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        setContent {
            CalculatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: CalculatorViewModel = viewModel()
                    SideEffect { calculatorViewModel = viewModel }
                    ManageRecentsVisibility(viewModel.vaultMode)
                    CalculatorScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ManageRecentsVisibility(vaultMode: VaultMode) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    DisposableEffect(vaultMode) {
        if (activity != null && vaultMode != VaultMode.NONE) {
            activity.setTaskDescription(
                android.app.ActivityManager.TaskDescription("Calculator")
            )
        }
        onDispose { }
    }
}
