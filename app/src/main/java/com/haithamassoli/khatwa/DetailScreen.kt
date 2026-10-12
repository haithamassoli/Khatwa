package com.haithamassoli.khatwa

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

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
    var dialog by remember { mutableStateOf<String?>(null) } // "fare" | "report"
    var keep by remember { mutableStateOf<Candidate?>(null) }
    var saved by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val isBase = option != null && (option == result?.baseline || option.pickup.isDirect && option.dropoff.isDirect)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isBase) "الرحلة المباشرة" else "تفاصيل البديل") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (option != null && result != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات أخرى") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                if (!isBase) DropdownMenuItem(
                                    text = { Text("مشاركة النقطتين") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = { menu = false; shareOption(context, option) },
                                )
                                if (!option.dropoff.isDirect) DropdownMenuItem(
                                    text = { Text("المشي من النزول إلى الوجهة") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null) },
                                    onClick = { menu = false; Integrations.startWalking(context, result.input.destination) },
                                )
                                DropdownMenuItem(
                                    text = { Text("سجّل سعراً رأيته") },
                                    leadingIcon = { Icon(Icons.Outlined.Payments, contentDescription = null) },
                                    onClick = { menu = false; dialog = "fare" },
                                )
                                if (!isBase) DropdownMenuItem(
                                    text = { Text("إبلاغ عن نقطة") },
                                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                                    onClick = { menu = false; dialog = "report" },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { if (option != null && result != null) DetailBottomBar(vm, option, context) },
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
        val base = result.baseline
        val detail = vm.detail
        val error = vm.detailError
        val data = remember(input, option, detail, result.baselinePolyline) {
            RouteMapData(
                input.origin, input.destination, option.pickup, option.dropoff,
                baseline = if (isBase) emptyList() else result.baselinePolyline,
                drive = detail?.drive?.takeIf { it.size > 1 } ?: option.drivePolyline.takeIf { it.size > 1 }
                    ?: (if (isBase) result.baselinePolyline else emptyList()),
                walkStart = detail?.walkStart?.takeIf { it.size > 1 }
                    ?: if (option.pickup.isDirect) emptyList() else listOf(input.origin, option.pickup.pt),
                walkEnd = detail?.walkEnd?.takeIf { it.size > 1 }
                    ?: if (option.dropoff.isDirect) emptyList() else listOf(option.dropoff.pt, input.destination),
            )
        }

        Column(Modifier.fillMaxSize().padding(inner)) {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                RouteMap(
                    data,
                    contentDescription = "خريطة المسار: المشي إلى نقطة الركوب، مسار السيارة، والمشي من النزول إلى الوجهة",
                    modifier = Modifier.fillMaxSize(),
                    interactive = true,
                    padding = 48.dp,
                )
                if (detail == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MapLegend(
                    listOfNotNull(
                        LegendDrive.copy(label = "السيارة").takeIf { data.drive.size > 1 },
                        LegendWalk.takeIf { data.walkStart.size > 1 || data.walkEnd.size > 1 },
                        LegendBaseline.takeIf { data.baseline.size > 1 },
                    )
                )
                OptionSummary(option, base, input.objective)
                if (option.verified) InfoLine(Icons.Outlined.Verified, verifiedText(result), color = MaterialTheme.colorScheme.primary)
                val warnings = listOfNotNull(
                    detail?.let { detailMismatch(option, base, it) },
                    detail?.let { walkOverrun(it, input) },
                )
                warnings.forEach { WarningCard(it) }
                if (error != null) {
                    Text(
                        "$error الأرقام أدناه من البحث.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الأرقام", style = MaterialTheme.typography.titleSmall)
                        OptionNumbers(option, base)
                    }
                }
                PointLines(option, saved) { keep = it }
                Text(
                    "* الأوقات دون احتساب انتظار السائق. الطريق الذي يختاره السائق قد يختلف؛ تأكد من السعر في تطبيق التوصيل قبل الحجز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (dialog) {
            "fare" -> QuickFareDialog(
                vm, defaultBaseline = isBase,
                onDismiss = { dialog = null },
                onSaved = { dialog = null; notify("سُجّل السعر في السجل") },
            )
            "report" -> ReportDialog(
                option = option,
                onDismiss = { dialog = null },
                onReport = { c, reason ->
                    vm.reportPoint(c, reason)
                    dialog = null
                    notify("شكراً، لن نقترح هذه النقطة مجدداً")
                },
            )
        }
        keep?.let { c ->
            KeepPointDialog(
                c = c,
                onDismiss = { keep = null },
                onSave = { name, side ->
                    vm.keepAutoPoint(c, name, side)
                    saved = saved + c.id
                    keep = null
                    notify("حُفظت النقطة وستظهر في بحثك القادم")
                },
            )
        }
    }
}

@Composable
private fun DetailBottomBar(vm: MainViewModel, o: RouteOption, context: Context) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookButton(vm, o, Modifier.weight(1f))
            if (!o.pickup.isDirect) {
                OutlinedButton(
                    onClick = { Integrations.startWalking(context, o.pickup.pt) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ابدأ المشي")
                }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.WarningAmber, contentDescription = "تنبيه", modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun walkOverrun(d: OptionDetail, input: SearchInput): String? {
    val overStart = d.walkStartS != null && input.maxWalkStartS > 0 && d.walkStartS > input.maxWalkStartS + 30
    val overEnd = d.walkEndS != null && input.maxWalkEndS > 0 && d.walkEndS > input.maxWalkEndS + 30
    val where = when {
        overStart && overEnd -> "عند الطرفين"
        overStart -> "إلى نقطة الركوب (${fmtDuration(d.walkStartS)})"
        overEnd -> "من نقطة النزول (${fmtDuration(d.walkEndS)})"
        else -> return null
    }
    return "المشي الفعلي $where أطول من الحد الذي اخترته."
}

private fun shareOption(context: Context, o: RouteOption) {
    fun block(label: String, c: Candidate) = buildString {
        append("$label: ${placeLabel(c)}\n")
        if (c.roadSide.isNotBlank()) append("الجهة: ${c.roadSide}\n")
        append("https://maps.google.com/?q=${c.pt.coords()}")
    }
    val text = block("نقطة الركوب", o.pickup) + "\n\n" + block("نقطة النزول", o.dropoff)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "مشاركة النقطتين"))
}

@Composable
private fun ReportDialog(option: RouteOption, onDismiss: () -> Unit, onReport: (Candidate, String) -> Unit) {
    val targets = listOfNotNull(
        option.pickup.takeIf { !it.isDirect }?.let { "نقطة الركوب: ${it.name}" to it },
        option.dropoff.takeIf { !it.isDirect }?.let { "نقطة النزول: ${it.name}" to it },
    )
    val reasons = listOf("جهة الشارع خاطئة", "لا يمكن التوقف هنا", "المشي غير ممكن أو غير آمن")
    var target by remember { mutableStateOf(targets.firstOrNull()?.second) }
    var reason by remember { mutableStateOf(reasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ما المشكلة؟") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (targets.size > 1) {
                    targets.forEach { (label, c) -> RadioLine(label, target == c) { target = c } }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                reasons.forEach { r -> RadioLine(r, reason == r) { reason = r } }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { target?.let { onReport(it, reason) } }) { Text("إبلاغ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
