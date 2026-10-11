package com.haithamassoli.khatwa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

// Dialog target: either a new point at [pt] or an existing candidate being edited.
private data class PointEdit(val pt: Pt, val existing: Candidate?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsScreen(vm: MainViewModel) {
    val candidates by vm.store.candidates.collectAsState()
    val points = candidates.filter { !it.isDirect }
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(31.9539, 35.9106), 13f) }
    var editing by remember { mutableStateOf<PointEdit?>(null) }
    var deleting by remember { mutableStateOf<Candidate?>(null) }
    var focusedId by remember { mutableStateOf<String?>(null) }

    fun focus(c: Candidate) {
        focusedId = c.id
        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(c.pt.lat, c.pt.lng), 17f)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نقاطي") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Box(Modifier.weight(0.45f).fillMaxWidth()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                    onMapLongClick = { editing = PointEdit(Pt(it.latitude, it.longitude), null) },
                    contentPadding = PaddingValues(top = 48.dp),
                ) {
                    points.forEach { c ->
                        val off = !c.enabled || c.verification == Verification.REPORTED
                        Marker(
                            state = remember(c.pt) { MarkerState(LatLng(c.pt.lat, c.pt.lng)) },
                            title = c.name,
                            snippet = listOf(pointsStatusLabel(c), c.roadSide).filter { it.isNotBlank() }.joinToString(" · "),
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (off) BitmapDescriptorFactory.HUE_ROSE else BitmapDescriptorFactory.HUE_AZURE
                            ),
                            alpha = if (off) 0.55f else 1f,
                            zIndex = if (c.id == focusedId) 1f else 0f,
                            onClick = { focusedId = c.id; false },
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        "اضغط مطولاً على الخريطة لإضافة نقطة",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                // Alternative to long-press: add a point at the map center.
                SmallFloatingActionButton(
                    onClick = {
                        val t = camera.position.target
                        editing = PointEdit(Pt(t.latitude, t.longitude), null)
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                ) {
                    Icon(Icons.Filled.AddLocationAlt, contentDescription = "إضافة نقطة في وسط الخريطة")
                }
                // Center crosshair for the add-at-center button.
                Text("+", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge, color = Color.DarkGray)
            }

            if (points.isEmpty()) {
                PointsEmptyState(Modifier.weight(0.55f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.55f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("${points.size} نقطة محفوظة", style = MaterialTheme.typography.titleMedium)
                    }
                    items(points, key = { it.id }) { c ->
                        PointCard(
                            c = c,
                            focused = c.id == focusedId,
                            onClick = { focus(c) },
                            onToggle = { vm.saveCandidate(c.copy(enabled = it)) },
                            onEdit = { editing = PointEdit(c.pt, c) },
                            onDelete = { deleting = c },
                        )
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }

    editing?.let { e ->
        PointDialog(
            existing = e.existing,
            onDismiss = { editing = null },
            onSave = { name, roadSide, landmark, use ->
                if (e.existing == null) vm.newCandidate(e.pt, name, roadSide, landmark, use)
                else vm.saveCandidate(e.existing.copy(name = name, roadSide = roadSide, landmark = landmark, use = use))
                editing = null
            },
        )
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("حذف النقطة؟") },
            text = { Text("سيتم حذف \"${c.name}\" نهائياً. يمكنك بدلاً من ذلك إيقافها مؤقتاً.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCandidate(c.id); deleting = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}

private fun pointsStatusLabel(c: Candidate): String = when (c.verification) {
    Verification.VERIFIED -> "مراجعة"
    Verification.REPORTED -> "مبلّغ عنها"
    Verification.UNVERIFIED -> "غير مراجعة"
}

private fun pointsUseLabel(u: PointUse): String = when (u) {
    PointUse.PICKUP -> "ركوب"
    PointUse.DROPOFF -> "نزول"
    PointUse.BOTH -> "ركوب ونزول"
}

@Composable
private fun PointCard(
    c: Candidate,
    focused: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val reported = c.verification == Verification.REPORTED
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (c.verification) {
                            Verification.VERIFIED -> Icons.Filled.Verified to MaterialTheme.colorScheme.primary
                            Verification.REPORTED -> Icons.Filled.Report to MaterialTheme.colorScheme.error
                            Verification.UNVERIFIED -> Icons.AutoMirrored.Filled.Help to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${pointsStatusLabel(c)} · ${pointsUseLabel(c.use)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (reported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = c.enabled, onCheckedChange = onToggle)
                    Text(if (c.enabled) "مفعّلة" else "موقوفة", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (c.roadSide.isNotBlank()) {
                Text("جهة الشارع: ${c.roadSide}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (c.landmark.isNotBlank()) {
                Text("معلم قريب: ${c.landmark}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تعديل")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PointsEmptyState(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("لا توجد نقاط محفوظة بعد", style = MaterialTheme.typography.titleMedium)
        Text(
            "احفظ نقاط ركوب ونزول راجعتها بنفسك حول رحلاتك المعتادة، مع جهة الشارع ومعلم قريب، فهذا يحسّن النتائج ويجعلها أدق.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "يقترح التطبيق أيضاً نقاطاً تلقائية غير مراجعة أثناء البحث، ويمكنك حفظ المناسب منها.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointDialog(
    existing: Candidate?,
    onDismiss: () -> Unit,
    onSave: (name: String, roadSide: String, landmark: String, use: PointUse) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var roadSide by remember { mutableStateOf(existing?.roadSide ?: "") }
    var landmark by remember { mutableStateOf(existing?.landmark ?: "") }
    var use by remember { mutableStateOf(existing?.use ?: PointUse.BOTH) }
    var triedSave by remember { mutableStateOf(false) }
    val nameMissing = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "نقطة جديدة" else "تعديل النقطة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم *") },
                    singleLine = true,
                    isError = triedSave && nameMissing,
                    supportingText = if (triedSave && nameMissing) {
                        { Text("الاسم مطلوب") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = roadSide,
                    onValueChange = { roadSide = it },
                    label = { Text("جهة الشارع") },
                    placeholder = { Text("جهة المول / باتجاه الدوار السابع") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = landmark,
                    onValueChange = { landmark = it },
                    label = { Text("معلم قريب") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("الاستخدام", style = MaterialTheme.typography.labelLarge)
                val uses = listOf(PointUse.PICKUP to "ركوب", PointUse.DROPOFF to "نزول", PointUse.BOTH to "كلاهما")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    uses.forEachIndexed { i, (u, label) ->
                        SegmentedButton(
                            selected = use == u,
                            onClick = { use = u },
                            shape = SegmentedButtonDefaults.itemShape(i, uses.size),
                        ) { Text(label) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                triedSave = true
                if (!nameMissing) onSave(name.trim(), roadSide.trim(), landmark.trim(), use)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
