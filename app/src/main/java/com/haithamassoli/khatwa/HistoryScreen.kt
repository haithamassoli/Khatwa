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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtMoney(v: Double, currency: String) = String.format(Locale.US, "%.2f %s", v, currency)

// OWNER: UI-B agent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val fares by vm.store.fares.collectAsState()
    val sorted = remember(fares) { fares.sortedByDescending { it.atMs } }
    var confirmClear by remember { mutableStateOf(false) }

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
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Text("حذف السجل")
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (sorted.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("لا توجد أسعار مسجلة بعد.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "من تفاصيل أي خيار اضغط «تسجيل سعر معروض» لتسجيل ما يعرضه تطبيق التوصيل.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HistorySummary(sorted) }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${fares.size} سعر مسجل", style = MaterialTheme.typography.titleSmall)
            rows.forEach { (key, alt, base) ->
                val (provider, currency) = key
                Text(
                    "$provider: متوسط البديل ${fmtMoney(alt.average(), currency)} (${alt.size}) " +
                        "مقابل المباشر ${fmtMoney(base.average(), currency)} (${base.size})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (rows.isNotEmpty()) {
                Text(
                    "متوسطات لرحلات وأوقات مختلفة، وليست مقارنة لنفس الرحلة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FareRow(f: FareObservation, onDelete: () -> Unit) {
    val date = remember(f.atMs) { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(f.atMs)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOf(f.provider, f.vehicleCategory).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    f.amount?.let { fmtMoney(it, f.currency) } ?: "غير معروف",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Text(
                    "${if (f.isBaseline) "الخيار المباشر" else "الخيار البديل"} · $date",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("${f.pickupName} ← ${f.dropoffName}", style = MaterialTheme.typography.bodySmall)
                val after = listOfNotNull(
                    f.actualFare?.let { "الأجرة الفعلية ${fmtMoney(it, f.currency)}" },
                    f.actualWaitS?.let { "الانتظار ${fmtDuration(it)}" },
                )
                if (after.isNotEmpty()) Text(after.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (f.note.isNotBlank()) {
                    Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
        }
    }
}
