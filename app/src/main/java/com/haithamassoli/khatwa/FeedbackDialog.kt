package com.haithamassoli.khatwa

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val STALE_AFTER_MS = 48 * 60 * 60 * 1000L

/** In-app fallback for the post-trip feedback notification: asks about the first due pending trip. */
@Composable
fun PendingFeedbackPrompt(vm: MainViewModel) {
    val trips by vm.store.pendingTrips.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var later by rememberSaveable { mutableStateOf(listOf<String>()) } // "لاحقاً": don't ask again this session
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }
    LaunchedEffect(trips) {
        // Too old to remember the pickup: drop silently.
        trips.filter { System.currentTimeMillis() - it.bookedAtMs > STALE_AFTER_MS }.forEach {
            vm.tripFeedback(it, null)
        }
    }
    val trip = trips.firstOrNull { it.askAfterMs <= now && it.id !in later && now - it.bookedAtMs <= STALE_AFTER_MS } ?: return
    fun answer(good: Boolean) = vm.tripFeedback(trip, good) // also cancels the notification
    AlertDialog(
        onDismissRequest = { later = later + trip.id },
        title = { Text("هل كانت نقطة الركوب مناسبة؟") },
        text = { Text("الركوب: ${trip.pickup.name}\nالنزول: ${trip.dropoff.name}") },
        confirmButton = {
            Row {
                TextButton(onClick = { answer(false) }) { Text("👎 لا") }
                TextButton(onClick = { answer(true) }) { Text("👍 نعم") }
            }
        },
        dismissButton = { TextButton(onClick = { later = later + trip.id }) { Text("لاحقاً") } },
    )
}
