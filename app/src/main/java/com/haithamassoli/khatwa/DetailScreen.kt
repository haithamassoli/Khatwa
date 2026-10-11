package com.haithamassoli.khatwa

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val WalkStartColor = Color(0xFF1E88E5)
private val DriveColor = Color(0xFFFF8F00) // amber: visible on light and dark map styles
private val WalkEndColor = Color(0xFF8E24AA)

private fun Pt.ll() = LatLng(lat, lng)
private fun Pt.coords() = String.format(Locale.US, "%.6f,%.6f", lat, lng)

// OWNER: UI-B agent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: MainViewModel) {
    val option = vm.selected
    val result = (vm.status as? SearchStatus.Done)?.result
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) } // "fare" | "report" | "keepP" | "keepD"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل الخيار") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (option == null || result == null) {
            Column(Modifier.fillMaxSize().padding(inner).padding(24.dp)) {
                Text("لا يوجد خيار محدد.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.back() }) { Text("رجوع") }
            }
            return@Scaffold
        }
        val input = result.input
        val isBaselineOption = option == result.baseline
        val detail = vm.detail
        val error = vm.detailError
        val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

        Column(Modifier.fillMaxSize().padding(inner)) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                DetailMap(input, option, detail)
                if (detail == null && error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            MapLegend()
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                PointInfo("نقطة الركوب", option.pickup)
                PointInfo("نقطة النزول", option.dropoff)

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("التقدير من البحث", style = MaterialTheme.typography.titleSmall)
                        KwOptionMetrics(option)
                    }
                }

                if (detail != null && (detail.walkStartS != null || detail.driveS != null || detail.walkEndS != null)) {
                    DetailCheck(detail, input)
                }

                ActionsSection(
                    option = option,
                    input = input,
                    onShare = { shareOption(context, option) },
                    onWalk = { walkTo(context, it) },
                    onFare = { dialog = "fare" },
                    onReport = { dialog = "report" },
                    onKeep = { dialog = it },
                )
            }
        }

        when (dialog) {
            "fare" -> FareDialog(
                defaultBaseline = isBaselineOption,
                hasBaseline = result.baseline != null,
                onDismiss = { dialog = null },
                onSave = { f ->
                    // The names follow the trip the price was quoted for.
                    val forOption = if (f.isBaseline) result.baseline ?: option else option
                    vm.logFare(f.copy(pickupName = forOption.pickup.name, dropoffName = forOption.dropoff.name))
                    dialog = null
                    notify("سُجّل السعر في السجل")
                },
            )
            "report" -> ReportDialog(
                option = option,
                onDismiss = { dialog = null },
                onReport = { c, reason ->
                    vm.reportPoint(c, reason)
                    dialog = null
                    notify("سُجّل البلاغ وأُوقفت النقطة في البحث القادم")
                },
            )
            "keepP", "keepD" -> {
                val c = if (dialog == "keepP") option.pickup else option.dropoff
                KeepPointDialog(
                    c = c,
                    onDismiss = { dialog = null },
                    onSave = { name, side ->
                        vm.keepAutoPoint(c, name, side)
                        dialog = null
                        notify("حُفظت النقطة كنقطة مراجعة")
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailMap(input: SearchInput, option: RouteOption, detail: OptionDetail?) {
    val camera = remember { CameraPositionState() } // rememberCameraPositionState is inline, built for JVM 17
    var loaded by remember { mutableStateOf(false) }
    val points = remember(input, option, detail) {
        listOf(input.origin, option.pickup.pt, option.dropoff.pt, input.destination) +
            (detail?.let { it.walkStart + it.drive + it.walkEnd } ?: emptyList())
    }
    LaunchedEffect(loaded, points) {
        if (!loaded) return@LaunchedEffect
        val b = LatLngBounds.builder()
        points.forEach { b.include(it.ll()) }
        runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), 120)) }
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        onMapLoaded = { loaded = true },
    ) {
        if (detail != null) {
            val dotted = listOf(Dot(), Gap(10f))
            if (detail.walkStart.size > 1) Polyline(detail.walkStart.map { it.ll() }, color = WalkStartColor, width = 12f, pattern = dotted)
            if (detail.drive.size > 1) Polyline(detail.drive.map { it.ll() }, color = DriveColor, width = 10f)
            if (detail.walkEnd.size > 1) Polyline(detail.walkEnd.map { it.ll() }, color = WalkEndColor, width = 12f, pattern = dotted)
        }
        Marker(
            state = rememberUpdatedMarkerState(input.origin.ll()), title = "البداية",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
        )
        Marker(
            state = rememberUpdatedMarkerState(option.pickup.pt.ll()), title = "الركوب: ${option.pickup.name}",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
        )
        Marker(
            state = rememberUpdatedMarkerState(option.dropoff.pt.ll()), title = "النزول: ${option.dropoff.name}",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
        )
        Marker(
            state = rememberUpdatedMarkerState(input.destination.ll()), title = "الوجهة",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLegend() {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendItem(WalkStartColor, dotted = true, "مشي إلى الركوب (منقط)")
        LegendItem(DriveColor, dotted = false, "السيارة (متصل)")
        LegendItem(WalkEndColor, dotted = true, "مشي من النزول (منقط)")
    }
}

@Composable
private fun LegendItem(color: Color, dotted: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(28.dp).height(8.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 6f,
                pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PointInfo(title: String, c: Candidate) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KwPointLine(title, c)
        if (c.isDirect) {
            Text("المكان نفسه — دون مشي", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("جهة الشارع: ${c.roadSide.ifBlank { "غير محددة" }}", style = MaterialTheme.typography.bodySmall)
            if (c.landmark.isNotBlank()) Text("معلم: ${c.landmark}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailCheck(d: OptionDetail, input: SearchInput) {
    val overStart = d.walkStartS != null && d.walkStartS > input.maxWalkStartS
    val overEnd = d.walkEndS != null && d.walkEndS > input.maxWalkEndS
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("التحقق التفصيلي", style = MaterialTheme.typography.titleSmall)
            d.walkStartS?.let { KwMetricRow("مشي البداية", fmtDuration(it)) }
            d.driveS?.let { KwMetricRow("وقت السيارة", fmtDuration(it)) }
            d.walkEndS?.let { KwMetricRow("مشي النهاية", fmtDuration(it)) }
            if (overStart || overEnd) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        buildString {
                            append("المسار التفصيلي للمشي ")
                            append(
                                when {
                                    overStart && overEnd -> "عند الطرفين"
                                    overStart -> "إلى نقطة الركوب"
                                    else -> "من نقطة النزول"
                                }
                            )
                            append(" يتجاوز الحد المحدد. هذا الخيار غير مُعتمد.")
                        },
                        Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsSection(
    option: RouteOption,
    input: SearchInput,
    onShare: () -> Unit,
    onWalk: (Pt) -> Unit,
    onFare: () -> Unit,
    onReport: () -> Unit,
    onKeep: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        ActionButton(Icons.Default.Share, "مشاركة النقطتين", primary = true, onClick = onShare)
        if (!option.pickup.isDirect) {
            ActionButton(Icons.AutoMirrored.Filled.DirectionsWalk, "إرشاد المشي إلى نقطة الركوب") { onWalk(option.pickup.pt) }
        }
        if (!option.dropoff.isDirect) {
            ActionButton(Icons.AutoMirrored.Filled.DirectionsWalk, "إرشاد المشي من النزول إلى الوجهة") { onWalk(input.destination) }
        }
        ActionButton(Icons.Default.AttachMoney, "تسجيل سعر معروض", onClick = onFare)
        if (!option.pickup.isDirect || !option.dropoff.isDirect) {
            ActionButton(Icons.Default.Flag, "إبلاغ عن نقطة", onClick = onReport)
        }
        if (option.pickup.auto && !option.pickup.isDirect) {
            ActionButton(Icons.Default.BookmarkAdd, "حفظ نقطة الركوب كنقطة مراجعة") { onKeep("keepP") }
        }
        if (option.dropoff.auto && !option.dropoff.isDirect) {
            ActionButton(Icons.Default.BookmarkAdd, "حفظ نقطة النزول كنقطة مراجعة") { onKeep("keepD") }
        }
        Text(
            "تأكد من السعر في تطبيق التوصيل قبل الحجز. الطريق الذي يختاره السائق قد يختلف عن هذا التقدير.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, primary: Boolean = false, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
    if (primary) Button(onClick, Modifier.fillMaxWidth()) { content() }
    else OutlinedButton(onClick, Modifier.fillMaxWidth()) { content() }
}

private fun shareOption(context: Context, o: RouteOption) {
    fun block(label: String, c: Candidate) = buildString {
        append("$label: ${c.name}\n")
        if (c.roadSide.isNotBlank()) append("الجهة: ${c.roadSide}\n")
        append("${c.pt.coords()}\n")
        append("https://maps.google.com/?q=${c.pt.coords()}")
    }
    val text = block("نقطة الركوب", o.pickup) + "\n\n" + block("نقطة النزول", o.dropoff)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "مشاركة النقطتين"))
}

private fun walkTo(context: Context, p: Pt) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${p.coords()}&mode=w"))
        .setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(nav)
    } catch (_: ActivityNotFoundException) {
        val web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${p.coords()}&travelmode=walking")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}

/** Accepts Arabic-Indic digits and separators. Returns null for blank; NaN for invalid / non-positive. */
private fun parseAmount(s: String): Double? {
    val t = s.trim().map { ch ->
        when (ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("")
    if (t.isEmpty()) return null
    val v = t.toDoubleOrNull() ?: return Double.NaN
    return if (v > 0) v else Double.NaN
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FareDialog(
    defaultBaseline: Boolean,
    hasBaseline: Boolean,
    onDismiss: () -> Unit,
    onSave: (FareObservation) -> Unit,
) {
    var provider by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("JOD") }
    var isBaseline by remember { mutableStateOf(defaultBaseline) }
    var actual by remember { mutableStateOf("") }
    var waitMin by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val amountV = parseAmount(amount)
    val actualV = parseAmount(actual)
    val waitV = parseAmount(waitMin)
    val valid = provider.isNotBlank() && amountV?.isNaN() != true && actualV?.isNaN() != true && waitV?.isNaN() != true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل سعر معروض") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(provider, { provider = it }, label = { Text("تطبيق التوصيل") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("كريم", "أوبر", "جيني", "تاكسي أصفر").forEach { p ->
                        FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text(p) })
                    }
                }
                OutlinedTextField(category, { category = it }, label = { Text("صنف السيارة") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        amount, { amount = it }, Modifier.weight(2f),
                        label = { Text("السعر (اتركه فارغاً إن لم يُعرف)") }, singleLine = true,
                        isError = amountV?.isNaN() == true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(currency, { currency = it }, Modifier.weight(1f), label = { Text("العملة") }, singleLine = true)
                }
                Text("السعر يخص:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isBaseline, onClick = { isBaseline = true }, enabled = hasBaseline,
                        label = { Text("السعر للخيار المباشر") },
                    )
                    FilterChip(selected = !isBaseline, onClick = { isBaseline = false }, label = { Text("للخيار البديل") })
                }
                Text("بعد الرحلة (اختياري)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    actual, { actual = it }, label = { Text("الأجرة الفعلية") }, singleLine = true,
                    isError = actualV?.isNaN() == true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    waitMin, { waitMin = it }, label = { Text("الانتظار الفعلي (دقائق)") }, singleLine = true,
                    isError = waitV?.isNaN() == true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        FareObservation(
                            id = UUID.randomUUID().toString(),
                            atMs = System.currentTimeMillis(),
                            provider = provider.trim(),
                            vehicleCategory = category.trim(),
                            amount = amountV,
                            currency = currency.trim().ifBlank { "JOD" },
                            isBaseline = isBaseline,
                            pickupName = "",
                            dropoffName = "",
                            actualFare = actualV,
                            actualWaitS = waitV?.let { (it * 60).toInt() },
                            note = note.trim(),
                        )
                    )
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun ReportDialog(option: RouteOption, onDismiss: () -> Unit, onReport: (Candidate, String) -> Unit) {
    val targets = listOfNotNull(
        option.pickup.takeIf { !it.isDirect }?.let { "نقطة الركوب: ${it.name}" to it },
        option.dropoff.takeIf { !it.isDirect }?.let { "نقطة النزول: ${it.name}" to it },
    )
    val reasons = listOf("جهة شارع خاطئة", "توقف غير مناسب", "مشي غير قابل للتنفيذ", "سبب آخر")
    var target by remember { mutableStateOf(targets.firstOrNull()?.second) }
    var reason by remember { mutableStateOf(reasons.first()) }
    var other by remember { mutableStateOf("") }
    val finalReason = if (reason == reasons.last()) other.trim() else reason

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إبلاغ عن نقطة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                targets.forEach { (label, c) -> RadioLine(label, target == c) { target = c } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                reasons.forEach { r -> RadioLine(r, reason == r) { reason = r } }
                if (reason == reasons.last()) {
                    OutlinedTextField(other, { other = it }, label = { Text("اكتب السبب") }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("ستُوقف النقطة في عمليات البحث القادمة.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && finalReason.isNotBlank(),
                onClick = { target?.let { onReport(it, finalReason) } },
            ) { Text("إبلاغ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun KeepPointDialog(c: Candidate, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var side by remember { mutableStateOf(c.roadSide) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حفظ كنقطة مراجعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تأكد من جهة الشارع ومن إمكانية التوقف قبل الحفظ.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(side, { side = it }, label = { Text("جهة الطريق") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), side.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
