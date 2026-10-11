package com.haithamassoli.khatwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtMoney(v: Double, currency: String) =
    if (currency == "JOD") fmtJod(v) else String.format(Locale.US, "%.2f %s", v, currency)

// OWNER: UI-B agent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val fares by vm.store.fares.collectAsState()
    val sorted = remember(fares) { fares.sortedByDescending { it.atMs } }
    var confirmClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val result = (vm.status as? SearchStatus.Done)?.result
    val defaultBaseline = result != null && result.alternatives.isEmpty() || vm.selected?.let { it == result?.baseline } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الأسعار") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (fares.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "حذف السجل كله")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "form") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("سجّل سعراً رأيته", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "اكتب المبلغ الذي عرضه التطبيق؛ نملأ الرحلة والوقت تلقائياً من آخر بحث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        QuickFareForm(vm, defaultBaseline) { scope.launch { snackbar.showSnackbar("سُجّل السعر") } }
                    }
                }
            }
            if (sorted.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "لا توجد أسعار مسجلة بعد. سجّل ما يعرضه تطبيق التوصيل للمسار المباشر وللبديل لتعرف الفرق مع الوقت.",
                        Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item(key = "summary") { HistorySummary(sorted) }
                items(sorted, key = { it.id }) { f -> FareRow(f) { vm.deleteFare(f.id) } }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("حذف السجل") },
            text = { Text("سيُحذف كل ما سجلته من أسعار. لا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun HistorySummary(fares: List<FareObservation>) {
    // Only known amounts count; a missing price is never treated as 0 or estimated (FR-13).
    val rows = fares.filter { it.amount != null }
        .groupBy { it.provider.trim() to it.currency }
        .mapNotNull { (key, list) ->
            val alt = list.filter { !it.isBaseline }.mapNotNull { it.amount }
            val base = list.filter { it.isBaseline }.mapNotNull { it.amount }
            if (alt.isEmpty() || base.isEmpty()) null else Triple(key, alt, base)
        }
    if (rows.isEmpty()) {
        Text("${fares.size} سعر مسجل", style = MaterialTheme.typography.titleSmall)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${fares.size} سعر مسجل", style = MaterialTheme.typography.titleSmall)
            rows.forEach { (key, alt, base) ->
                val (provider, currency) = key
                Text(
                    "$provider: متوسط البديل ${fmtMoney(alt.average(), currency)} (${alt.size}) " +
                        "مقابل المباشر ${fmtMoney(base.average(), currency)} (${base.size})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "متوسطات لرحلات وأوقات مختلفة، وليست مقارنة لنفس الرحلة.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun FareRow(f: FareObservation, onDelete: () -> Unit) {
    val date = remember(f.atMs) { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(f.atMs)) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        f.amount?.let { fmtMoney(it, f.currency) } ?: "غير معروف",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    val who = listOf(f.provider, f.vehicleCategory).filter { it.isNotBlank() }.joinToString(" · ")
                    if (who.isNotEmpty()) Text("  ·  $who", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "${if (f.isBaseline) "للمسار المباشر" else "للبديل"} · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (f.pickupName.isNotBlank() || f.dropoffName.isNotBlank()) {
                    Text("${f.pickupName.ifBlank { "؟" }} ← ${f.dropoffName.ifBlank { "؟" }}", style = MaterialTheme.typography.bodySmall)
                }
                val after = listOfNotNull(
                    f.actualFare?.let { "الأجرة الفعلية ${fmtMoney(it, f.currency)}" },
                    f.actualWaitS?.let { "الانتظار ${fmtDuration(it)}" },
                )
                if (after.isNotEmpty()) Text(after.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (f.note.isNotBlank()) {
                    Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "حذف هذا السعر") }
        }
    }
}
