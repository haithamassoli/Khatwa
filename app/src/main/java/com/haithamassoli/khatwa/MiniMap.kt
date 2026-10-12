package com.haithamassoli.khatwa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberUpdatedMarkerState

// Map line colours are fixed (not theme colours) so they read the same on light and dark map styles.
internal val MapBaselineColor = Color(0xFF8A9696)
internal val MapDriveColor = Color(0xFF00897B)
internal val MapWalkColor = Color(0xFF1E88E5)
private const val BaselineWidth = 9f
private const val DriveWidth = 14f
private const val WalkWidth = 12f

internal fun Pt.ll() = LatLng(lat, lng)

/** What a route map draws. Lines with fewer than 2 points are skipped. */
data class RouteMapData(
    val origin: Pt,
    val destination: Pt,
    val pickup: Candidate,
    val dropoff: Candidate,
    val baseline: List<Pt> = emptyList(),
    val drive: List<Pt> = emptyList(),
    val walkStart: List<Pt> = emptyList(),
    val walkEnd: List<Pt> = emptyList(),
) {
    val points: List<Pt> get() = (listOf(origin, pickup.pt, dropoff.pt, destination) + baseline + drive + walkStart + walkEnd).distinct()

    companion object {
        /** Walking legs as straight lines when the detailed geometry isn't known. */
        fun of(input: SearchInput, o: RouteOption, baseline: List<Pt>) = RouteMapData(
            input.origin, input.destination, o.pickup, o.dropoff,
            baseline = baseline,
            drive = o.drivePolyline,
            walkStart = if (o.pickup.isDirect) emptyList() else listOf(input.origin, o.pickup.pt),
            walkEnd = if (o.dropoff.isDirect) emptyList() else listOf(o.dropoff.pt, input.destination),
        )
    }
}

@Composable
internal fun RouteMap(
    data: RouteMapData,
    contentDescription: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    padding: Dp = 32.dp,
) {
    val camera = remember { CameraPositionState() } // rememberCameraPositionState is inline, built for JVM 17
    var loaded by remember { mutableStateOf(false) }
    val points = remember(data) { data.points }
    val padPx = with(LocalDensity.current) { padding.roundToPx() }
    LaunchedEffect(loaded, points) {
        if (!loaded || points.isEmpty()) return@LaunchedEffect
        val b = LatLngBounds.builder()
        points.forEach { b.include(it.ll()) }
        runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), padPx)) }
    }
    val ui = if (interactive) MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = true, myLocationButtonEnabled = false)
    else MapUiSettings(
        compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
        rotationGesturesEnabled = false, scrollGesturesEnabled = false, scrollGesturesEnabledDuringRotateOrZoom = false,
        tiltGesturesEnabled = false, zoomControlsEnabled = false, zoomGesturesEnabled = false,
    )
    GoogleMap(
        modifier = modifier,
        mergeDescendants = !interactive,
        contentDescription = contentDescription,
        cameraPositionState = camera,
        uiSettings = ui,
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapLoaded = { loaded = true },
    ) {
        val dotted = listOf(Dot(), Gap(12f))
        if (data.baseline.size > 1) Polyline(data.baseline.map { it.ll() }, color = MapBaselineColor, width = BaselineWidth)
        if (data.drive.size > 1) Polyline(data.drive.map { it.ll() }, color = MapDriveColor, width = DriveWidth)
        if (data.walkStart.size > 1) Polyline(data.walkStart.map { it.ll() }, color = MapWalkColor, width = WalkWidth, pattern = dotted)
        if (data.walkEnd.size > 1) Polyline(data.walkEnd.map { it.ll() }, color = MapWalkColor, width = WalkWidth, pattern = dotted)
        // Pins mark where to get in / out; the start and destination become small dots once walking is involved.
        val startDot = remember { dotIcon(0xFF2E7D32.toInt()) }
        val endDot = remember { dotIcon(0xFFC62828.toInt()) }
        val center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
        if (data.pickup.isDirect) Marker(
            state = rememberUpdatedMarkerState(data.origin.ll()), title = "البداية",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
        ) else {
            Marker(state = rememberUpdatedMarkerState(data.origin.ll()), title = "البداية", icon = startDot, anchor = center)
            Marker(
                state = rememberUpdatedMarkerState(data.pickup.pt.ll()), title = "الركوب: ${data.pickup.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN), zIndex = 1f,
            )
        }
        if (data.dropoff.isDirect) Marker(
            state = rememberUpdatedMarkerState(data.destination.ll()), title = "الوجهة",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        ) else {
            Marker(state = rememberUpdatedMarkerState(data.destination.ll()), title = "الوجهة", icon = endDot, anchor = center)
            Marker(
                state = rememberUpdatedMarkerState(data.dropoff.pt.ll()), title = "النزول: ${data.dropoff.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED), zIndex = 1f,
            )
        }
    }
}

/** Small round marker. Must be called after the map is initialized (inside GoogleMap content). */
internal fun dotIcon(color: Int): BitmapDescriptor {
    val size = 40
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

data class LegendLine(val label: String, val color: Color, val widthPx: Float, val dotted: Boolean)

internal val LegendBaseline = LegendLine("المباشر", MapBaselineColor, 5f, false)
internal val LegendDrive = LegendLine("البديل", MapDriveColor, 10f, false)
internal val LegendWalk = LegendLine("مشي", MapWalkColor, 7f, true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapLegend(lines: List<LegendLine>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        lines.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(28.dp).height(10.dp)) {
                    drawLine(
                        l.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = l.widthPx,
                        pathEffect = if (l.dotted) PathEffect.dashPathEffect(floatArrayOf(4f, 7f)) else null,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(l.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
