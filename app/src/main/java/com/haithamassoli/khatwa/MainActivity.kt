package com.haithamassoli.khatwa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haithamassoli.khatwa.ui.theme.KhatwaTheme

// OWNER: UI-A agent.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KhatwaTheme {
                // Arabic-only UI: force RTL regardless of device locale.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vm: MainViewModel = viewModel()
                    val settings by vm.store.settings.collectAsState()
                    BackHandler(vm.screen != Screen.PLAN) { vm.back() }
                    if (!settings.onboarded) OnboardingScreen(vm)
                    else when (vm.screen) {
                        Screen.PLAN -> PlanScreen(vm)
                        Screen.POINTS -> PointsScreen(vm)
                        Screen.RESULTS -> ResultsScreen(vm)
                        Screen.DETAIL -> DetailScreen(vm)
                        Screen.HISTORY -> HistoryScreen(vm)
                    }
                }
            }
        }
    }
}
