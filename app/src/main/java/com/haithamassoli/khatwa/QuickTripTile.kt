package com.haithamassoli.khatwa

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.LocalTime

/** Quick settings tile "رحلة سريعة": opens a trip to work (mornings) or home (later), else the app. */
class QuickTripTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val place = target()
        tile.label = getString(R.string.tile_label)
        tile.subtitle = place?.let(TripLaunch::label)
        tile.contentDescription = listOfNotNull(getString(R.string.tile_label), tile.subtitle).joinToString("، ")
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun target(): SavedPlace? = TripLaunch.quickPlace(AppStore.get(this).places.value, LocalTime.now().hour)

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = TripLaunch.intent(this, target()?.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
