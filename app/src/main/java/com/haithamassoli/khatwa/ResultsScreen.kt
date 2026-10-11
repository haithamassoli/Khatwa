package com.haithamassoli.khatwa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// OWNER: UI-B agent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: MainViewModel) {
    val status = vm.status
    val loading = status is SearchStatus.Loading
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var keep by remember { mutableStateOf<Candidate?>(null) }
    var saved by remember { mutableStateOf(setOf<String>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (loading) "جارٍ البحث…" else "النتائج") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (status is SearchStatus.Done) {
                        IconButton(onClick = { vm.search(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة البحث")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (status !is SearchStatus.Done) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (status) {
                    is SearchStatus.Loading -> {
                        CircularProgressIndicator()
                        Text(stageText(status.stage), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        LinearProgressIndicator(
                            progress = { (status.stage.ordinal + 1f) / SearchStage.entries.size },
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                        OutlinedButton(onClick = { vm.cancelSearch() }) { Text("إلغاء") }
                    }
                    is SearchStatus.Error -> {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Text(status.message, textAlign = TextAlign.Center)
                        Button(onClick = { vm.search() }) { Text("حاول مرة أخرى") }
                    }
                    else -> Text("لا توجد نتيجة حالية. عدّل المدخلات وابدأ البحث.", textAlign = TextAlign.Center)
                }
                TextButton(onClick = { vm.back() }) { Text("رجوع إلى التخطيط") }
            }
            return@Scaffold
        }
        val r = status.result
        val base = r.baseline
        val objective = r.input.objective
        val best = r.alternatives.firstOrNull()
        val onKeep: (Candidate) -> Unit = { keep = it }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (best ?: base)?.let { shown ->
                val data = RouteMapData.of(r.input, shown, if (shown == base) emptyList() else r.baselinePolyline)
                    .let { if (shown == base && it.drive.isEmpty()) it.copy(drive = r.baselinePolyline) else it }
                if (data.points.size >= 2) item(key = "map") { ResultsMap(vm, data, shown == base) }
            }
            item(key = "notices") { Notices(vm, r) }
            if (best == null) {
                item(key = "empty") { EmptyState(vm, base) }
            } else {
                itemsIndexed(r.alternatives) { i, alt ->
                    AlternativeCard(vm, alt, base, objective, best = i == 0 && r.alternatives.size > 1, saved = saved, onKeep = onKeep)
                }
                item(key = "baseline") {
                    if (base != null) BaselineCard(vm, base)
                    else InfoLine(Icons.Outlined.Info, "تعذر حساب الرحلة المباشرة للمقارنة، لذلك لا تظهر فروق عنها.")
                }
            }
            item(key = "footer") { Footer(r) }
        }
    }

    keep?.let { c ->
        KeepPointDialog(
            c = c,
            onDismiss = { keep = null },
            onSave = { name, side ->
                vm.keepAutoPoint(c, name, side)
                saved = saved + c.id
                keep = null
                scope.launch { snackbar.showSnackbar("حُفظت النقطة وستظهر في بحثك القادم") }
            },
        )
    }
}

@Composable
private fun ResultsMap(vm: MainViewModel, data: RouteMapData, directOnly: Boolean) {
    Column {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.large)) {
            RouteMap(
                data,
                contentDescription = if (directOnly) "خريطة الرحلة المباشرة من البداية إلى الوجهة"
                else "خريطة تقارن المسار المباشر بالبديل، مع المشي إلى نقطة الركوب ومن نقطة النزول",
                modifier = Modifier.fillMaxSize(),
            )
            // Transparent overlay: keeps list scrolling smooth over the static map and makes the whole map one tap target.
            Box(
                Modifier.matchParentSize().clickable(onClickLabel = "عرض التفاصيل", role = Role.Button) {
                    (vm.status as? SearchStatus.Done)?.result?.let { r -> (r.alternatives.firstOrNull() ?: r.baseline)?.let(vm::openOption) }
                }
            )
        }
        val lines = listOfNotNull(
            LegendBaseline.takeIf { data.baseline.size > 1 },
            (if (directOnly) LegendDrive.copy(label = "المباشر") else LegendDrive).takeIf { data.drive.size > 1 },
            LegendWalk.takeIf { data.walkStart.size > 1 || data.walkEnd.size > 1 },
        )
        if (lines.isNotEmpty()) MapLegend(lines)
    }
}

@Composable
private fun Notices(vm: MainViewModel, r: SearchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (r.fromCache) {
            InfoLine(Icons.Outlined.History, "نتيجة محفوظة من ${fmtAgo(r.calculatedAtMs)}،") {
                TextButton(onClick = { vm.search(force = true) }) { Text("تحديث") }
            }
        }
        if (r.night) InfoLine(Icons.Outlined.NightsStay, "بعد المغرب: عرضنا الركوب من الباب فقط")
        if (r.droppedAfterCheck > 0) {
            InfoLine(
                Icons.Outlined.Verified,
                if (r.droppedAfterCheck == 1) "استبعدنا بديلاً لأن التوفير اختفى عند التحقق من المسار الفعلي"
                else "استبعدنا ${r.droppedAfterCheck} بدائل لأن التوفير اختفى عند التحقق من المسار الفعلي",
            )
        }
        cleanWarnings(r.warnings).forEach { InfoLine(Icons.Outlined.Info, it) }
    }
}

@Composable
private fun AlternativeCard(
    vm: MainViewModel,
    o: RouteOption,
    base: RouteOption?,
    objective: Objective,
    best: Boolean,
    saved: Set<String>,
    onKeep: (Candidate) -> Unit,
) {
    var expanded by rememberSaveable(o.pickup.id, o.dropoff.id) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (best) StatusChip(Icons.Outlined.CheckCircle, "الأفضل")
            OptionSummary(o, base, objective)
            PointLines(o, saved, onKeep)
            Column {
                ExpandToggle(expanded, "عرض الأرقام", "إخفاء الأرقام") { expanded = !expanded }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        OptionNumbers(o, base)
                        if (o.verified) InfoLine(Icons.Outlined.Verified, "✓ تم التحقق من المسار الفعلي", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookButton(vm, o, Modifier.weight(1f))
                OutlinedButton(onClick = { vm.openOption(o) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("التفاصيل") }
            }
        }
    }
}

@Composable
private fun BaselineCard(vm: MainViewModel, base: RouteOption) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("أو الرحلة المباشرة", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    "${fmtDuration(base.driveS)} بالسيارة", fmtDistance(base.driveM), base.fareJod?.let { "~${fmtJod(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookButton(vm, base, Modifier.weight(1f), outlined = true, label = "احجز المباشر")
                TextButton(onClick = { vm.openOption(base) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("التفاصيل") }
            }
        }
    }
}

@Composable
private fun EmptyState(vm: MainViewModel, base: RouteOption?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (base == null) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                Text("لا يوجد خيار صالح ضمن حدود المشي المحددة", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("جرّب زيادة حد المشي أو أعد البحث بعد قليل.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                OutlinedButton(onClick = { vm.back() }) { Text("تعديل البحث") }
                return@Column
            }
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text("الرحلة المباشرة هي الأفضل الآن", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "لم نجد نقطة قريبة توفّر وقتاً يستحق المشي. اركب من مكانك.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(
                    "${fmtDuration(base.driveS)} بالسيارة", fmtDistance(base.driveM), base.fareJod?.let { "~${fmtJod(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookButton(vm, base, Modifier.weight(1f))
                OutlinedButton(onClick = { vm.openOption(base) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("التفاصيل") }
            }
        }
    }
}

@Composable
private fun Footer(r: SearchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        val failed = if (r.pairsFailed > 0) " · تعذر حساب ${r.pairsFailed}" else ""
        Text(
            "حُسبت الساعة ${fmtClock(r.calculatedAtMs)} · ${if (r.trafficAware) "مع الزحمة الحية" else "دون زحمة حية"} · فُحص ${r.pairsChecked} زوجاً$failed",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "* الأوقات دون احتساب انتظار السائق. النتائج مبنية على نقاط محدودة ولا تضمن أقل أجرة؛ تأكد من السعر في تطبيق التوصيل قبل الحجز.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
