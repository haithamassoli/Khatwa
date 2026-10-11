package com.haithamassoli.khatwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// OWNER: UI-B agent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: MainViewModel) {
    val status = vm.status
    val loading = status == SearchStatus.Loading
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
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { vm.search() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("إعادة البحث")
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (status !is SearchStatus.Done) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (status) {
                    SearchStatus.Loading -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text("جارٍ حساب الخيارات…")
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(onClick = { vm.cancelSearch() }) { Text("إلغاء") }
                    }
                    is SearchStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error)
                    else -> Text("لا توجد نتيجة حالية. عدّل المدخلات وابدأ البحث.")
                }
                Spacer(Modifier.size(16.dp))
                Button(onClick = { vm.back() }) { Text("رجوع إلى التخطيط") }
            }
            return@Scaffold
        }
        val r = status.result
        val base = r.baseline
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ResultsHeader(r) }
            item {
                if (base != null) {
                    OptionCard(
                        title = "المباشر — مرجع",
                        option = base,
                        baseline = null,
                        highlight = false,
                        onClick = { vm.openOption(base) },
                    )
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "تعذر حساب الخيار المباشر للمقارنة، لذلك لا تظهر فروق عن المرجع.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            if (r.alternatives.isEmpty()) {
                item {
                    Text(
                        if (base != null) "الركوب والنزول المباشران هما الأفضل بين الخيارات المفحوصة"
                        else "لا يوجد خيار صالح ضمن حدود المشي المحددة.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                itemsIndexed(r.alternatives) { i, alt ->
                    OptionCard(
                        title = "بديل ${i + 1}",
                        option = alt,
                        baseline = base,
                        highlight = true,
                        onClick = { vm.openOption(alt) },
                    )
                }
            }
            item {
                Text(
                    "النتائج مبنية على مجموعة محدودة من النقاط المفحوصة، ولا تضمن أفضل نقطة ممكنة ولا أقل أجرة. " +
                        "تأكد من السعر في تطبيق التوصيل قبل الحجز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultsHeader(r: SearchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(r.calculatedAtMs))
        Text(
            "آخر تحديث $time · ${if (r.trafficAware) "مع الزحمة الحية" else "دون زحمة حية"}",
            style = MaterialTheme.typography.titleSmall,
        )
        val failed = if (r.pairsFailed > 0) " · تعذر حساب ${r.pairsFailed}" else ""
        Text("فُحص ${r.pairsChecked} زوجاً$failed", style = MaterialTheme.typography.bodySmall)
        r.warnings.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber, contentDescription = "تنبيه",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(w, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    option: RouteOption,
    baseline: RouteOption?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors() else
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (highlight) {
                KwPointLine("الركوب", option.pickup)
                KwPointLine("النزول", option.dropoff)
            }
            HorizontalDivider()
            KwOptionMetrics(option)
            if (baseline != null) {
                HorizontalDivider()
                Text("مقارنة بالمباشر", style = MaterialTheme.typography.labelLarge)
                DeltaLine("المشي والقيادة", option.modeledS - baseline.modeledS, fmtDeltaDuration(option.modeledS - baseline.modeledS))
                DeltaLine("وقت السيارة", option.driveS - baseline.driveS, fmtDeltaDuration(option.driveS - baseline.driveS))
                DeltaLine("مسافة السيارة", option.driveM - baseline.driveM, fmtDeltaDistance(option.driveM - baseline.driveM))
                DeltaLine("المشي", option.walkTotalS - baseline.walkTotalS, fmtDeltaDuration(option.walkTotalS - baseline.walkTotalS))
            }
        }
    }
}

@Composable
internal fun KwPointLine(label: String, c: Candidate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${c.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (c.auto || c.verification == Verification.UNVERIFIED) {
            Spacer(Modifier.width(6.dp))
            KwBadge("غير مُراجعة", MaterialTheme.colorScheme.tertiaryContainer)
        } else if (c.verification == Verification.REPORTED) {
            Spacer(Modifier.width(6.dp))
            KwBadge("مُبلغ عنها", MaterialTheme.colorScheme.errorContainer)
        }
    }
}

@Composable
private fun KwBadge(text: String, color: Color) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** Shared by the results cards and the detail screen. */
@Composable
internal fun KwOptionMetrics(o: RouteOption) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KwMetricRow("مشي البداية", fmtDuration(o.walkStartS))
        KwMetricRow("وقت السيارة", fmtDuration(o.driveS))
        KwMetricRow("مشي النهاية", fmtDuration(o.walkEndS))
        KwMetricRow("مسافة السيارة", fmtDistance(o.driveM))
        KwMetricRow("المشي والقيادة — دون انتظار السائق", fmtDuration(o.modeledS), bold = true)
    }
}

@Composable
internal fun KwMetricRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

/** delta < 0 is better; colour is a secondary cue only, the text carries the meaning. */
@Composable
private fun DeltaLine(label: String, delta: Int, text: String) {
    val color = when {
        text.contains("تقريباً") -> MaterialTheme.colorScheme.onSurfaceVariant
        delta < 0 -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
