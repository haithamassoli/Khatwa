package com.haithamassoli.khatwa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haithamassoli.khatwa.ui.theme.KhatwaTheme
import kotlinx.coroutines.launch

// OWNER: UI-A agent.
class MainActivity : ComponentActivity() {
    // Same instance as `viewModel()` inside setContent (same owner, same default key).
    private val mainVm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
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
                    PendingFeedbackPrompt(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Widget / quick tile trips, places shared from Google Maps, geo: and maps links.
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            TripLaunch.ACTION -> {
                val id = intent.getStringExtra(TripLaunch.EXTRA_PLACE_ID)
                mainVm.store.places.value.firstOrNull { it.id == id }?.let { mainVm.startTrip(it) }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                // Some apps put the place name in the subject and only the link in the text.
                val full = if (!subject.isNullOrBlank() && text?.contains(subject) != true) "$subject\n${text.orEmpty()}" else text
                resolveShared(full, null)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { resolveShared(null, it) }
        }
    }

    private fun resolveShared(text: String?, data: String?) {
        if (text.isNullOrBlank() && data.isNullOrBlank()) return
        lifecycleScope.launch {
            val place = IncomingLinks.resolve(text, data, mainVm.google)
            if (place != null) mainVm.applySharedDestination(place.pt, place.name)
            else Toast.makeText(this@MainActivity, "تعذر قراءة المكان المشارك", Toast.LENGTH_LONG).show()
        }
    }
}
