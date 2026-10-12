package com.haithamassoli.khatwa

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews

/** Home screen widget: up to 3 saved places; tapping one searches the best pickup from the current location. */
class TripWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = views(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val BUTTONS = intArrayOf(R.id.widget_place_1, R.id.widget_place_2, R.id.widget_place_3)

        /** Re-render all widget instances (call after saved places change). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TripWidget::class.java))
            if (ids.isEmpty()) return
            val views = views(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun views(context: Context): RemoteViews {
            val places = TripLaunch.ordered(AppStore.get(context).places.value).take(BUTTONS.size)
            val rv = RemoteViews(context.packageName, R.layout.widget_trip)
            rv.setOnClickPendingIntent(R.id.widget_header, activity(context, null))
            rv.setViewVisibility(R.id.widget_empty, if (places.isEmpty()) View.VISIBLE else View.GONE)
            rv.setOnClickPendingIntent(R.id.widget_empty, activity(context, null))
            BUTTONS.forEachIndexed { i, id ->
                val p = places.getOrNull(i)
                if (p == null) rv.setViewVisibility(id, View.GONE)
                else {
                    rv.setViewVisibility(id, View.VISIBLE)
                    rv.setTextViewText(id, TripLaunch.widgetLabel(p))
                    rv.setOnClickPendingIntent(id, activity(context, p.id))
                }
            }
            return rv
        }

        private fun activity(context: Context, placeId: String?): PendingIntent =
            PendingIntent.getActivity(context, placeId?.hashCode() ?: 0, TripLaunch.intent(context, placeId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
