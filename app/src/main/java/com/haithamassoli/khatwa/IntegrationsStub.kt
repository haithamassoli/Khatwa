package com.haithamassoli.khatwa

import android.content.Context

// TEMPORARY stub so feat/ui-results compiles on its own. Delete at merge; the real one is Integrations.kt.
object Integrations {
    fun book(context: Context, app: RideApp, pickup: Pt, pickupName: String, dropoff: Pt, dropoffName: String) {}
    fun startWalking(context: Context, to: Pt) {}
    fun installedRideApps(context: Context): List<RideApp> = RideApp.entries
}
