package com.haithamassoli.khatwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.UUID

// Shared UI pieces for results, detail and history.

internal fun kwRideName(app: RideApp): String = when (app) {
    RideApp.UBER -> "أوبر"
    RideApp.CAREEM -> "كريم"
    RideApp.JEENY -> "جيني"
}

/** Name to hand to ride apps / the fare log: direct points use the place names typed on the plan screen. */
internal fun MainViewModel.pointName(c: Candidate, start: Boolean): String =
    if (c.isDirect) (if (start) plan.originName else plan.destinationName).ifBlank { c.name } else c.name

/** "احجز": menu of ride apps (installed first); records the trip for feedback before handing off. */
@Composable
internal fun BookButton(vm: MainViewModel, o: RouteOption, modifier: Modifier = Modifier, outlined: Boolean = false, label: String = "احجز") {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val askThen = rememberNotificationPermissionThen()
    val installed = remember { runCatching { Integrations.installedRideApps(context) }.getOrDefault(emptyList()) }
    val apps = remember(installed) { installed + RideApp.entries.filterNot { it in installed } }
    Box(modifier) {
        val m = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        val content: @Composable () -> Unit = {
            Icon(Icons.Filled.LocalTaxi, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        if (outlined) OutlinedButton({ open = true }, m) { content() } else Button({ open = true }, m) { content() }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "احجز عبر", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            apps.forEach { app ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(kwRideName(app))
                            if (app !in installed) {
                                Text("غير مثبت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.LocalTaxi, contentDescription = null) },
                    onClick = {
                        open = false
                        askThen {
                            val r = Integrations.book(context, app, o.pickup.pt, vm.pointName(o.pickup, true), o.dropoff.pt, vm.pointName(o.dropoff, false))
                            // Only ask for feedback when a ride app (or Uber web) actually opened.
                            if (r != BookResult.FAILED && r != BookResult.OPENED_STORE) vm.tripBooked(o)
                        }
                    },
                )
            }
        }
    }
}

/** Headline ("وفّر 8 دقائق"), what-to-do sentence, then fare / climb hints. */
@Composable
internal fun OptionSummary(o: RouteOption, base: RouteOption?, objective: Objective) {
    val isBase = base != null && o == base || (o.pickup.isDirect && o.dropoff.isDirect)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (isBase) "الرحلة المباشرة" else savingTitle(o, base, objective),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (isBase) "اركب من مكانك وانزل عند الوجهة: ${fmtDuration(o.driveS)} بالسيارة · ${fmtDistance(o.driveM)}"
            else tripSentence(o),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!isBase && base != null && objective == Objective.MODELED && base.driveS - o.driveS - (base.modeledS - o.modeledS) >= 60) {
            val d = deltaDuration(o.driveS - base.driveS)
            InfoLine(Icons.Outlined.LocalTaxi, "وقت السيارة: ${d.text}", color = deltaColor(d))
        }
        if (!isBase) fareDelta(o, base)?.let { d ->
            InfoLine(Icons.Outlined.Payments, d.text, color = deltaColor(d))
        }
        if (isBase && o.fareJod != null) InfoLine(Icons.Outlined.Payments, "الأجرة التقديرية نحو ${fmtJod(o.fareJod)}")
        climbText(o)?.let { InfoLine(Icons.Outlined.Terrain, it) }
    }
}

@Composable
internal fun deltaColor(d: Delta, neutral: Boolean = false) = when {
    neutral || d.sign == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    d.sign < 0 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

@Composable
internal fun InfoLine(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = color)
        action?.invoke()
    }
}

/** Neutral, non-interactive status label (never red: a suggested point is not an error). */
@Composable
internal fun StatusChip(icon: ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A pickup / dropoff point with its landmark, status and the "احفظها" action for auto points. */
@Composable
internal fun PointLine(label: String, c: Candidate, saved: Boolean, onKeep: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$label: ${placeLabel(c)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val extra = listOfNotNull(
                c.roadSide.takeIf { it.isNotBlank() }?.let { "الجهة: $it" },
                c.landmark.takeIf { it.isNotBlank() && !c.name.contains(it) }?.let { "قرب: $it" },
            )
            if (extra.isNotEmpty()) {
                Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                saved -> StatusChip(Icons.Outlined.BookmarkAdded, "محفوظة")
                c.auto -> StatusChip(Icons.Outlined.Place, "نقطة مقترحة")
                c.verification == Verification.REPORTED -> StatusChip(Icons.Outlined.Flag, "مُبلغ عنها")
                c.verification == Verification.UNVERIFIED -> StatusChip(Icons.Outlined.Place, "غير مراجعة")
            }
        }
        if (c.auto && !saved && onKeep != null) {
            TextButton(onClick = onKeep) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("احفظها")
            }
        }
    }
}

@Composable
internal fun PointLines(o: RouteOption, saved: Set<String>, onKeep: (Candidate) -> Unit) {
    if (o.pickup.isDirect && o.dropoff.isDirect) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!o.pickup.isDirect) PointLine("الركوب", o.pickup, o.pickup.id in saved) { onKeep(o.pickup) }
        if (!o.dropoff.isDirect) PointLine("النزول", o.dropoff, o.dropoff.id in saved) { onKeep(o.dropoff) }
    }
}

@Composable
internal fun NumberRow(label: String, value: String, delta: Delta? = null, neutral: Boolean = false, bold: Boolean = false, sub: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
            if (delta != null) Text(delta.text, style = MaterialTheme.typography.labelMedium, color = deltaColor(delta, neutral))
        }
    }
}

/** The detailed numbers; deltas are vs the direct trip (omitted for the direct trip itself). */
@Composable
internal fun OptionNumbers(o: RouteOption, base: RouteOption?) {
    val b = base?.takeIf { it != o }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberRow("الوقت الكلي (مشي + سيارة)", fmtDuration(o.modeledS), b?.let { deltaDuration(o.modeledS - it.modeledS) }, bold = true)
        NumberRow("وقت السيارة", fmtDuration(o.driveS), b?.let { deltaDuration(o.driveS - it.driveS) })
        NumberRow("مسافة السيارة", fmtDistance(o.driveM), b?.let { deltaDistance(o.driveM - it.driveM) })
        NumberRow(
            "المشي", if (o.walkTotalS == 0) "بلا مشي" else fmtDuration(o.walkTotalS),
            b?.let { deltaDuration(o.walkTotalS - it.walkTotalS) }, neutral = true,
            sub = if (o.walkTotalS == 0) null else "قبل الركوب ${fmtDuration(o.walkStartS)} · بعد النزول ${fmtDuration(o.walkEndS)}",
        )
        o.fareJod?.let { NumberRow("الأجرة التقديرية", "~${fmtJod(it)}") }
        if (b != null) NumberRow("الرحلة المباشرة", "${fmtDuration(b.modeledS)} · ${fmtDistance(b.driveM)}")
    }
}

@Composable
internal fun ExpandToggle(expanded: Boolean, closed: String, open: String, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(if (expanded) open else closed)
        Spacer(Modifier.width(4.dp))
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
}

@Composable
internal fun KeepPointDialog(c: Candidate, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var side by remember { mutableStateOf(c.roadSide) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
        title = { Text("احفظ النقطة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ستظهر في بحثك القادم. تأكد أن التوقف ممكن هنا.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(side, { side = it }, label = { Text("جهة الشارع (اختياري)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), side.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Pickup → dropoff names of the trip a quote was for, inferred from the open option or the last search. */
internal fun fareNames(vm: MainViewModel, isBaseline: Boolean): Pair<String, String> {
    val r = (vm.status as? SearchStatus.Done)?.result
    val o = if (isBaseline) r?.baseline else vm.selected?.takeIf { it != r?.baseline } ?: r?.alternatives?.firstOrNull()
    return if (o != null) vm.pointName(o.pickup, true) to vm.pointName(o.dropoff, false)
    else vm.plan.originName to vm.plan.destinationName
}

/** One amount + ride app + direct/alternative. Everything else is inferred. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun QuickFareForm(vm: MainViewModel, defaultBaseline: Boolean, onSaved: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val installed = runCatching { Integrations.installedRideApps(context) }.getOrDefault(emptyList())
        listOf(RideApp.CAREEM, RideApp.UBER, RideApp.JEENY).sortedBy { it !in installed }
    }
    val last = remember { vm.store.fares.value.maxByOrNull { it.atMs }?.provider }
    var amount by rememberSaveable { mutableStateOf("") }
    var app by remember { mutableStateOf(apps.firstOrNull { kwRideName(it) == last }) }
    var isBaseline by remember { mutableStateOf(defaultBaseline) }
    val v = parseAmount(amount)
    val bad = v?.isNaN() == true
    val (from, to) = fareNames(vm, isBaseline)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            amount, { amount = it }, Modifier.fillMaxWidth(),
            label = { Text("المبلغ المعروض") }, suffix = { Text("د.أ") }, singleLine = true, isError = bad,
            supportingText = if (bad) ({ Text("أدخل رقماً أكبر من صفر") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { a -> FilterChip(selected = app == a, onClick = { app = a }, label = { Text(kwRideName(a)) }) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(isBaseline, { isBaseline = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("للمسار المباشر") }
            SegmentedButton(!isBaseline, { isBaseline = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("للبديل") }
        }
        if (from.isNotBlank() || to.isNotBlank()) {
            InfoLine(Icons.Outlined.Info, "${from.ifBlank { "؟" }} ← ${to.ifBlank { "؟" }}")
        }
        Button(
            enabled = v != null && !bad && app != null,
            onClick = {
                val a = app ?: return@Button
                vm.logFare(
                    FareObservation(
                        id = UUID.randomUUID().toString(), atMs = System.currentTimeMillis(), provider = kwRideName(a),
                        vehicleCategory = "", amount = v, isBaseline = isBaseline, pickupName = from, dropoffName = to,
                    )
                )
                amount = ""
                onSaved()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("حفظ السعر") }
    }
}

@Composable
internal fun QuickFareDialog(vm: MainViewModel, defaultBaseline: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Payments, contentDescription = null) },
        title = { Text("سجّل سعراً رأيته") },
        text = { QuickFareForm(vm, defaultBaseline, onSaved) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
