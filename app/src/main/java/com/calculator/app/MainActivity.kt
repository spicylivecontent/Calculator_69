package com.calculator.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calculator.app.data.AppPreferences
import com.calculator.app.data.AutoLock
import com.calculator.app.ui.calculator.CalculatorScreen
import com.calculator.app.ui.calculator.CalculatorViewModel
import com.calculator.app.ui.calculator.VaultInactivityLock
import com.calculator.app.ui.calculator.VaultMode
import com.calculator.app.ui.theme.CalculatorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : FragmentActivity() {

    private var calculatorViewModel: CalculatorViewModel? = null
    private val preferences by lazy { AppPreferences(this) }
    private val inactivityLock = VaultInactivityLock()

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN || ev?.action == MotionEvent.ACTION_UP) {
            inactivityLock.touch()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        if (!isChangingConfigurations && preferences.autoLock == AutoLock.ON_BACKGROUND) {
            calculatorViewModel?.lockVault()
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        setContent {
            CalculatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: CalculatorViewModel = viewModel()
                    SideEffect { calculatorViewModel = viewModel }
                    ManageRecentsVisibility(viewModel.vaultMode)
                    InactivityAutoLock(
                        vaultMode = viewModel.vaultMode,
                        autoLock = preferences.autoLock,
                        inactivityLock = inactivityLock,
                        onLock = { viewModel.lockVault() }
                    )
                    CalculatorScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun InactivityAutoLock(
    vaultMode: VaultMode,
    autoLock: AutoLock,
    inactivityLock: VaultInactivityLock,
    onLock: () -> Unit
) {
    val timeout = autoLock.timeoutMillis
    if (vaultMode == VaultMode.NONE || timeout <= 0L) return

    LaunchedEffect(vaultMode, autoLock) {
        inactivityLock.touch()
        while (isActive) {
            delay(5_000L)
            if (inactivityLock.shouldLock(autoLock)) {
                onLock()
                break
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
