package com.haithamassoli.khatwa

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AMMAN = LatLng(31.9539, 35.9106)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: MainViewModel) {
    val plan = vm.plan
    val status = vm.status
    val candidates by vm.store.candidates.collectAsState()
    val quota by vm.store.quota.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Reopen on the chosen points when returning from another screen.
    val camera = rememberCameraPositionState {
        val p = plan.destination ?: plan.origin
        position = if (p != null) CameraPosition.fromLatLngZoom(LatLng(p.lat, p.lng), 15f) else CameraPosition.fromLatLngZoom(AMMAN, 13f)
    }

    // true = next map tap sets the start, false = sets the destination.
    var settingStart by remember { mutableStateOf(plan.origin == null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    fun moveCamera(pt: Pt) = scope.launch {
        camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pt.lat, pt.lng), 15f))
    }

    // After "my location", follow the new origin once and advance to destination.
    LaunchedEffect(plan.origin) {
        val o = plan.origin
        if (locating && o != null) {
            locating = false
            settingStart = false
            moveCamera(o)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            permissionMessage = null
            locating = true
            vm.useCurrentLocation()
        } else {
            permissionMessage = "لم يُمنح إذن الموقع. يمكنك تحديد البداية يدوياً بالضغط على الخريطة أو البحث بالاسم."
        }
    }

    fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionMessage = null
            locating = true
            vm.useCurrentLocation()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("خطوة")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.go(Screen.POINTS) }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("نقاطي")
                    }
                    TextButton(onClick = { vm.go(Screen.HISTORY) }) {
                        Icon(Icons.Filled.History, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("السجل")
                    }
                },
            )
        },
        bottomBar = { PlanBottomBar(vm, status, plan, quota) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Box(Modifier.weight(0.42f).fillMaxWidth()) {
                PlanMap(
                    vm = vm,
                    camera = camera,
                    candidates = candidates,
                    onTap = { pt ->
                        if (settingStart) {
                            vm.setOrigin(pt)
                            settingStart = false
                        } else {
                            vm.setDestination(pt)
                        }
                    },
                )
                // Mode toggle sits at the bottom of the map, within thumb reach.
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (settingStart) "اضغط على الخريطة لتحديد البداية" else "اضغط على الخريطة لتحديد الوجهة",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = settingStart,
                                onClick = { settingStart = true },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = { Icon(Icons.Filled.TripOrigin, null, tint = START_COLOR, modifier = Modifier.size(18.dp)) },
                            ) { Text("البداية") }
                            SegmentedButton(
                                selected = !settingStart,
                                onClick = { settingStart = false },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = { Icon(Icons.Filled.Flag, null, tint = END_COLOR, modifier = Modifier.size(18.dp)) },
                            ) { Text("الوجهة") }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .weight(0.58f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val near = plan.origin ?: plan.destination
                PlaceField(
                    vm = vm,
                    label = "البداية",
                    icon = { Icon(Icons.Filled.TripOrigin, contentDescription = "البداية", tint = START_COLOR) },
                    pt = plan.origin,
                    name = plan.originName,
                    near = near,
                    onPick = { hit ->
                        vm.setOrigin(hit.pt, hit.name)
                        settingStart = false
                        moveCamera(hit.pt)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::requestLocation, enabled = !locating) {
                        if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("موقعي الحالي")
                    }
                    OutlinedButton(onClick = vm::swap, enabled = plan.origin != null || plan.destination != null) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تبديل")
                    }
                }
                (permissionMessage ?: vm.locationMessage)?.let { InfoNote(it) }
                PlaceField(
                    vm = vm,
                    label = "الوجهة",
                    icon = { Icon(Icons.Filled.Flag, contentDescription = "الوجهة", tint = END_COLOR) },
                    pt = plan.destination,
                    name = plan.destinationName,
                    near = near,
                    onPick = { hit ->
                        vm.setDestination(hit.pt, hit.name)
                        moveCamera(hit.pt)
                    },
                )
                if (plan.origin != null || plan.destination != null) {
                    Text(
                        "للتعديل الدقيق: اضغط مطولاً على العلامة واسحبها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()
                WalkSlider("أقصى مشي قبل الركوب", plan.maxWalkStartS) { vm.setWalkStart(it) }
                WalkSlider("أقصى مشي بعد النزول", plan.maxWalkEndS) { vm.setWalkEnd(it) }

                HorizontalDivider()
                Text("الأولوية", style = MaterialTheme.typography.titleMedium)
                val objectives = listOf(
                    Objective.MODELED to "أقل مشي وقيادة",
                    Objective.DRIVE_TIME to "أقل وقت سيارة",
                    Objective.DRIVE_DISTANCE to "أقل مسافة سيارة",
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    objectives.forEachIndexed { i, (o, label) ->
                        SegmentedButton(
                            selected = plan.objective == o,
                            onClick = { vm.setObjective(o) },
                            shape = SegmentedButtonDefaults.itemShape(i, objectives.size),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        }
                    }
                }
                val activePoints = candidates.count { it.enabled && it.verification != Verification.REPORTED }
                Text(
                    "النقاط المحفوظة المفعّلة: $activePoints",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val START_COLOR = Color(0xFF2E7D32)
private val END_COLOR = Color(0xFFC62828)

@Composable
private fun PlanMap(vm: MainViewModel, camera: CameraPositionState, candidates: List<Candidate>, onTap: (Pt) -> Unit) {
    val plan = vm.plan
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        onMapClick = { onTap(Pt(it.latitude, it.longitude)) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp),
    ) {
        val activeIcon = remember { planDotIcon(0xFF1565C0.toInt()) }
        val offIcon = remember { planDotIcon(0xFF9E9E9E.toInt()) }
        candidates.forEach { c ->
            val off = !c.enabled || c.verification == Verification.REPORTED
            val statusLabel = when {
                c.verification == Verification.REPORTED -> "مبلّغ عنها"
                !c.enabled -> "موقوفة"
                else -> "نقطة محفوظة"
            }
            Marker(
                state = remember(c.pt) { MarkerState(LatLng(c.pt.lat, c.pt.lng)) },
                title = c.name,
                snippet = listOf(statusLabel, c.roadSide).filter { it.isNotBlank() }.joinToString(" · "),
                icon = if (off) offIcon else activeIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                alpha = if (off) 0.7f else 1f,
            )
        }
        plan.origin?.let {
            PlanDraggableMarker(it, "البداية", plan.originName, BitmapDescriptorFactory.HUE_GREEN) { p -> vm.setOrigin(p) }
        }
        plan.destination?.let {
            PlanDraggableMarker(it, "الوجهة", plan.destinationName, BitmapDescriptorFactory.HUE_RED) { p -> vm.setDestination(p) }
        }
    }
}

@Composable
private fun PlanDraggableMarker(pt: Pt, title: String, name: String, hue: Float, onMoved: (Pt) -> Unit) {
    val state = remember { MarkerState(LatLng(pt.lat, pt.lng)) }
    LaunchedEffect(pt) { if (!state.isDragging) state.position = LatLng(pt.lat, pt.lng) }
    val callback by rememberUpdatedState(onMoved)
    LaunchedEffect(state) {
        snapshotFlow { state.isDragging }.drop(1).filter { !it }.collect {
            callback(Pt(state.position.latitude, state.position.longitude))
        }
    }
    Marker(
        state = state,
        title = title,
        snippet = name.ifBlank { null },
        draggable = true,
        icon = BitmapDescriptorFactory.defaultMarker(hue),
        zIndex = 2f,
    )
}

/** Small round marker for saved points. Must be called after the map is initialized. */
private fun planDotIcon(color: Int): BitmapDescriptor {
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

@Composable
private fun PlaceField(
    vm: MainViewModel,
    label: String,
    icon: @Composable () -> Unit,
    pt: Pt?,
    name: String,
    near: Pt?,
    onPick: (PlaceHit) -> Unit,
) {
    val shown = when {
        pt == null -> ""
        name.isNotBlank() -> name
        else -> "نقطة على الخريطة (%.5f، %.5f)".format(pt.lat, pt.lng)
    }
    var query by remember { mutableStateOf(shown) }
    var typing by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableStateOf(0) }
    val focus = LocalFocusManager.current
    val hitsView = remember { BringIntoViewRequester() }
    // Results render below the field; scroll them above the keyboard.
    LaunchedEffect(hits) { if (hits.isNotEmpty()) { delay(100); hitsView.bringIntoView() } }

    // Reflect point changes coming from the map, GPS, swap or reverse geocoding.
    LaunchedEffect(shown) { if (!typing) query = shown }

    // Debounced place search; also runs immediately on the keyboard search action.
    LaunchedEffect(query, typing, searchTrigger) {
        error = null
        if (!typing || query.trim().length < 2) { hits = emptyList(); loading = false; return@LaunchedEffect }
        if (searchTrigger == 0) delay(500)
        loading = true
        try {
            hits = vm.google.searchPlaces(query.trim(), near ?: Pt(AMMAN.latitude, AMMAN.longitude)).take(6)
            if (hits.isEmpty()) error = "لا توجد نتائج. جرّب اسماً آخر أو اضغط على الخريطة."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            hits = emptyList()
            error = "تعذر البحث عن الأماكن. تحقق من الاتصال أو اختر النقطة على الخريطة."
        } finally {
            loading = false
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; typing = true; searchTrigger = 0 },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("ابحث عن مكان أو اضغط على الخريطة") },
            leadingIcon = icon,
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    typing && query.isNotEmpty() -> IconButton(onClick = { query = ""; hits = emptyList() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "مسح")
                    }
                    else -> Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searchTrigger++ }),
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        if (hits.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).bringIntoViewRequester(hitsView),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Column {
                    hits.forEach { hit ->
                        ListItem(
                            headlineContent = { Text(hit.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (hit.address.isNotBlank()) {
                                { Text(hit.address, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else null,
                            leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                typing = false
                                hits = emptyList()
                                query = hit.name
                                focus.clearFocus()
                                onPick(hit)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkSlider(label: String, valueS: Int, onChange: (Int) -> Unit) {
    val minutes = valueS / 60
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(planMinutesLabel(minutes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange(it.roundToInt() * 60) },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

private fun planMinutesLabel(m: Int): String = when (m) {
    0 -> "بدون مشي"
    1 -> "دقيقة"
    2 -> "دقيقتان"
    in 3..10 -> "$m دقائق"
    else -> "$m دقيقة"
}

@Composable
private fun InfoNote(text: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun PlanBottomBar(vm: MainViewModel, status: SearchStatus, plan: PlanState, quota: Quota) {
    val ready = plan.origin != null && plan.destination != null
    val quotaFull = quota.maxSearches > 0 && quota.searches >= quota.maxSearches
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                SearchStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("جارٍ البحث عن أفضل النقاط…", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = vm::cancelSearch) { Text("إلغاء") }
                }
                is SearchStatus.Error -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = vm::search, enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("حاول مرة أخرى")
                    }
                }
                is SearchStatus.Done -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.go(Screen.RESULTS) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("عرض النتائج")
                    }
                    OutlinedButton(onClick = vm::search, enabled = !quotaFull, modifier = Modifier.height(52.dp)) {
                        Text("ابحث مجدداً")
                    }
                }
                SearchStatus.Idle -> {
                    Button(onClick = vm::search, enabled = ready && !quotaFull, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ابحث")
                    }
                    if (!ready) {
                        Text(
                            "حدد البداية والوجهة أولاً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                if (quotaFull) "استُنفدت حصة البحث هذا الشهر: ${quota.searches} / ${quota.maxSearches}"
                else "البحث هذا الشهر: ${quota.searches} / ${quota.maxSearches}",
                style = MaterialTheme.typography.labelMedium,
                color = if (quotaFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
