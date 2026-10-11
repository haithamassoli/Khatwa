package com.haithamassoli.khatwa

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Post-trip 👍/👎 notification: an inexact alarm at [PendingTrip.askAfterMs] posts it, its actions record the vote. */
class FeedbackNotifier : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TRIP_ID) ?: return
        val store = AppStore.get(context)
        val trip = store.pendingTrips.value.firstOrNull { it.id == id }
        when (intent.action) {
            ACTION_ASK -> if (trip != null) notify(context, trip)
            ACTION_GOOD, ACTION_BAD -> {
                if (trip != null) {
                    val good = intent.action == ACTION_GOOD
                    store.vote(trip.pickup, good)
                    store.vote(trip.dropoff, good)
                    store.removePendingTrip(trip.id)
                }
                cancel(context, id)
            }
        }
    }

    companion object {
        private const val CHANNEL = "trip_feedback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_ASK = "com.haithamassoli.khatwa.feedback.ASK"
        private const val ACTION_GOOD = "com.haithamassoli.khatwa.feedback.GOOD"
        private const val ACTION_BAD = "com.haithamassoli.khatwa.feedback.BAD"
        private const val EXTRA_TRIP_ID = "trip_id"

        /** Schedules the feedback notification for [trip] (inexact, no exact-alarm permission needed). */
        fun schedule(context: Context, trip: PendingTrip) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.set(AlarmManager.RTC_WAKEUP, trip.askAfterMs, broadcast(context, ACTION_ASK, trip.id))
        }

        /** Removes the notification and any pending alarm for this trip (call after the user answered in-app). */
        fun cancel(context: Context, tripId: String) {
            NotificationManagerCompat.from(context).cancel(tripId, NOTIFICATION_ID)
            context.getSystemService(AlarmManager::class.java)?.cancel(broadcast(context, ACTION_ASK, tripId))
        }

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun notify(context: Context, trip: PendingTrip) {
            if (!canNotify(context)) return // the in-app PendingFeedbackPrompt asks instead
            ensureChannel(context)
            val open = PendingIntent.getActivity(context, trip.id.hashCode(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_walk)
                .setContentTitle("هل كانت نقطة الركوب مناسبة؟")
                .setContentText("${trip.pickup.name} ← ${trip.dropoff.name}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, "👍 نعم", broadcast(context, ACTION_GOOD, trip.id))
                .addAction(0, "👎 لا", broadcast(context, ACTION_BAD, trip.id))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(trip.id, NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
                // permission revoked between the check and the call
            }
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "تقييم نقطة الركوب", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "سؤال بعد الرحلة: هل كانت نقطة الركوب والنزول مناسبة؟" })
        }

        private fun broadcast(context: Context, action: String, tripId: String): PendingIntent =
            PendingIntent.getBroadcast(context, (action + tripId).hashCode(),
                Intent(context, FeedbackNotifier::class.java).setAction(action).putExtra(EXTRA_TRIP_ID, tripId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Returns a function that asks for POST_NOTIFICATIONS (Android 13+) if it isn't granted yet; no-op otherwise.
 * Call it right before booking so the post-trip feedback notification can be shown.
 */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    val askThen = rememberNotificationPermissionThen()
    return remember(askThen) { { askThen {} } }
}

/**
 * Like [rememberNotificationPermissionRequester], but runs `action` once the permission dialog is answered
 * (or immediately when no dialog is needed). Use it to book only after the dialog closed, otherwise the ride app
 * opens on top of the system dialog:
 * `val askThen = rememberNotificationPermissionThen(); askThen { Integrations.book(...); vm.tripBooked(option) }`
 */
@SuppressLint("InlinedApi") // the string constant is only used behind the SDK check
@Composable
fun rememberNotificationPermissionThen(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke(); pending[0] = null
    }
    return remember(context, launcher) {
        { action ->
            val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (!needed) action()
            else {
                pending[0] = action
                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }.onFailure { pending[0] = null; action() }
            }
        }
    }
}
