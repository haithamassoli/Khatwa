package com.haithamassoli.khatwa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
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
import java.util.UUID

private val AMMAN = LatLng(31.9539, 35.9106)

private data class PlaceEdit(val kind: PlaceKind, val place: SavedPlace?)

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: MainViewModel) {
    val plan = vm.plan
    val status = vm.status
    val candidates by vm.store.candidates.collectAsState()
    val quota by vm.store.quota.collectAsState()
    val places by vm.store.places.collectAsState()
    val settings by vm.store.settings.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Reopen on the chosen points when returning from another screen.
    val camera = rememberCameraPositionState {
        val p = plan.destination ?: plan.origin
        position = if (p != null) CameraPosition.fromLatLngZoom(LatLng(p.lat, p.lng), 15f) else CameraPosition.fromLatLngZoom(AMMAN, 13f)
    }
    val sheetState = rememberStandardBottomSheetState()
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val sheetScroll = rememberScrollState()

    // true = next map tap sets the start, false = sets the destination.
    var settingStart by remember { mutableStateOf(plan.origin == null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var managePlaces by remember { mutableStateOf(false) }
    var placeEdit by remember { mutableStateOf<PlaceEdit?>(null) }
    var peekPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }

    fun moveCamera(pt: Pt) = scope.launch {
        camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pt.lat, pt.lng), 15f))
    }

    // Origin is set asynchronously; afterwards follow it once, advance to destination and run a pending shared search.
    fun locate() {
        permissionMessage = null
        locating = true
        val before = vm.plan.origin
        scope.launch {
            try { vm.useCurrentLocation().join() } finally { locating = false }
            val now = vm.plan.origin
            if (now != null && now != before) { settingStart = false; moveCamera(now) }
            if (now == null) settingStart = true
            vm.consumeAutoSearch()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate()
        else {
            permissionMessage = "لم يُمنح إذن الموقع. حدد البداية بالضغط على الخريطة أو بالبحث عن اسم المكان."
            settingStart = true
            vm.consumeAutoSearch()
        }
    }

    fun requestLocation() {
        if (hasLocationPermission(context)) locate()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Opened from a shared place: destination is set, get the start from GPS then search.
    LaunchedEffect(vm.pendingAutoSearch) {
        if (!vm.pendingAutoSearch) return@LaunchedEffect
        vm.plan.destination?.let { moveCamera(it) }
        when {
            hasLocationPermission(context) -> locate()
            vm.plan.origin != null -> vm.consumeAutoSearch()
            else -> requestLocation()
        }
    }

    // Most trips start where the user is standing: fill the start without asking when the permission is already there.
    LaunchedEffect(Unit) {
        if (vm.plan.origin == null && !vm.pendingAutoSearch && hasLocationPermission(context)) locate()
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.PartiallyExpanded) sheetScroll.animateScrollTo(0)
    }
    BackHandler(sheetState.currentValue == SheetValue.Expanded) { scope.launch { sheetState.partialExpand() } }

    fun openPlace(p: SavedPlace) {
        vm.setDestination(p.pt, p.name)
        moveCamera(p.pt)
        if (vm.plan.origin == null) requestLocation()
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val handleHeight = 24.dp
    val peek = (if (peekPx == 0) 300.dp else with(density) { peekPx.toDp() }) + handleHeight + navBottom
    val nightNow = settings.noWalkAtNight && Night.isNight(plan.origin ?: plan.destination ?: Pt(AMMAN.latitude, AMMAN.longitude))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight - statusTop - 8.dp
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peek,
            sheetShadowElevation = 8.dp,
            sheetDragHandle = {
                Box(
                    Modifier.fillMaxWidth().height(handleHeight).semantics { contentDescription = "اسحب لعرض المزيد من الخيارات" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                }
            },
            sheetContent = {
                Column(Modifier.fillMaxWidth().heightIn(max = maxSheet).imePadding().verticalScroll(sheetScroll)) {
                    // Peek part: always visible above the navigation bar.
                    Column(
                        Modifier.onSizeChanged { peekPx = it.height }.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShortcutRow(
                            places = places,
                            onOpen = ::openPlace,
                            onEdit = { k, p -> placeEdit = PlaceEdit(k, p) },
                            onManage = { managePlaces = true },
                        )
                        val near = plan.origin ?: plan.destination
                        val expand: () -> Unit = { scope.launch { sheetState.expand() } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlaceField(
                                    vm = vm,
                                    label = "البداية",
                                    icon = { Icon(Icons.Filled.TripOrigin, contentDescription = "البداية", tint = START_COLOR) },
                                    pt = plan.origin,
                                    name = plan.originName,
                                    near = near,
                                    onFocused = expand,
                                    action = {
                                        IconButton(onClick = ::requestLocation, enabled = !locating) {
                                            if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                            else Icon(Icons.Filled.MyLocation, contentDescription = "استخدم موقعي الحالي")
                                        }
                                    },
                                    onPick = { hit ->
                                        vm.setOrigin(hit.pt, hit.name)
                                        settingStart = false
                                        moveCamera(hit.pt)
                                    },
                                )
                                PlaceField(
                                    vm = vm,
                                    label = "الوجهة",
                                    icon = { Icon(Icons.Filled.Flag, contentDescription = "الوجهة", tint = END_COLOR) },
                                    pt = plan.destination,
                                    name = plan.destinationName,
                                    near = near,
                                    onFocused = expand,
                                    onPick = { hit ->
                                        vm.setDestination(hit.pt, hit.name)
                                        moveCamera(hit.pt)
                                    },
                                )
                            }
                            IconButton(onClick = vm::swap, enabled = plan.origin != null || plan.destination != null) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "تبديل البداية والوجهة")
                            }
                        }
                        (permissionMessage ?: vm.locationMessage)?.let { InfoNote(it) }
                        if (nightNow) InfoNote("بعد المغرب: نقترح الركوب من الباب فقط", Icons.Filled.Bedtime)
                        SearchActions(vm, status, plan, quota)
                    }

                    Column(
                        Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("خيارات البحث") },
                            supportingContent = { Text(optionsSummary(plan, nightNow)) },
                            leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                            trailingContent = {
                                Icon(if (showOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (showOptions) "إخفاء الخيارات" else "عرض الخيارات")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showOptions = !showOptions },
                        )
                        AnimatedVisibility(showOptions) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الأهم عندي", style = MaterialTheme.typography.labelLarge)
                                ObjectiveChoice(plan.objective, vm::setObjective)
                                Text("أقصى وقت مشي", style = MaterialTheme.typography.labelLarge)
                                WalkPresetRow("قبل الركوب", plan.maxWalkStartS, enabled = !nightNow, onChange = vm::setWalkStart)
                                WalkPresetRow("بعد النزول", plan.maxWalkEndS, enabled = !nightNow, onChange = vm::setWalkEnd)
                            }
                        }
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                PlanMap(
                    vm = vm,
                    camera = camera,
                    candidates = candidates,
                    contentPadding = PaddingValues(top = with(density) { topPx.toDp() }, bottom = peek),
                    onTap = { pt ->
                        if (settingStart) {
                            vm.setOrigin(pt)
                            settingStart = false
                        } else {
                            vm.setDestination(pt)
                        }
                    },
                )
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { topPx = it.height }.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp) {
                            Row(Modifier.height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("خطوة", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AppMenu(
                            onPoints = { vm.go(Screen.POINTS) },
                            onHistory = { vm.go(Screen.HISTORY) },
                            onSettings = { showSettings = true },
                        )
                    }
                    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
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
            }
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
    if (managePlaces) ManagePlacesDialog(
        places = places,
        onPick = { k, p -> managePlaces = false; placeEdit = PlaceEdit(k, p) },
        onDismiss = { managePlaces = false },
    )
    placeEdit?.let { e ->
        PlaceEditDialog(
            edit = e,
            plan = plan,
            onSave = { vm.savePlace(it); placeEdit = null },
            onDelete = { vm.deletePlace(it.id); placeEdit = null },
            onDismiss = { placeEdit = null },
        )
    }
}

private val START_COLOR = Color(0xFF2E7D32)
private val END_COLOR = Color(0xFFC62828)

@Composable
private fun AppMenu(onPoints: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = CircleShape, shadowElevation = 3.dp) {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.Menu, contentDescription = "القائمة") }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(
                Triple("نقاط الركوب المحفوظة", Icons.AutoMirrored.Filled.ListAlt, onPoints),
                Triple("سجل الأسعار", Icons.Filled.History, onHistory),
                Triple("الإعدادات", Icons.Filled.Settings, onSettings),
            ).forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { open = false; action() },
                )
            }
        }
    }
}

@Composable
private fun PlanMap(vm: MainViewModel, camera: CameraPositionState, candidates: List<Candidate>, contentPadding: PaddingValues, onTap: (Pt) -> Unit) {
    val plan = vm.plan
    GoogleMap(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "خريطة لاختيار نقطة البداية والوجهة" },
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        onMapClick = { onTap(Pt(it.latitude, it.longitude)) },
        contentPadding = contentPadding,
    ) {
        val activeIcon = remember { dotIcon(0xFF1565C0.toInt()) }
        val offIcon = remember { dotIcon(0xFF9E9E9E.toInt()) }
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

@Composable
private fun PlaceField(
    vm: MainViewModel,
    label: String,
    icon: @Composable () -> Unit,
    pt: Pt?,
    name: String,
    near: Pt?,
    onFocused: () -> Unit,
    onPick: (PlaceHit) -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val shown = when {
        pt == null -> ""
        name.isNotBlank() -> name
        else -> "نقطة على الخريطة"
    }
    var query by remember { mutableStateOf(shown) }
    var typing by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableIntStateOf(0) }
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
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() },
            label = { Text(label) },
            placeholder = { Text("ابحث عن مكان أو اضغط على الخريطة", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = icon,
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    typing && query.isNotEmpty() -> IconButton(onClick = { query = ""; hits = emptyList() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "مسح")
                    }
                    action != null -> action()
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

private fun kindLabel(k: PlaceKind) = when (k) {
    PlaceKind.HOME -> "البيت"
    PlaceKind.WORK -> "العمل"
    PlaceKind.UNIVERSITY -> "الجامعة"
    PlaceKind.OTHER -> "مكان آخر"
}

private fun kindIcon(k: PlaceKind) = when (k) {
    PlaceKind.HOME -> Icons.Filled.Home
    PlaceKind.WORK -> Icons.Filled.Work
    PlaceKind.UNIVERSITY -> Icons.Filled.School
    PlaceKind.OTHER -> Icons.Filled.Place
}

private val MAIN_KINDS = listOf(PlaceKind.HOME, PlaceKind.WORK, PlaceKind.UNIVERSITY)

@Composable
private fun ShortcutRow(
    places: List<SavedPlace>,
    onOpen: (SavedPlace) -> Unit,
    onEdit: (PlaceKind, SavedPlace?) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MAIN_KINDS.forEach { k ->
            val p = places.firstOrNull { it.kind == k }
            ShortcutChip(
                label = if (p != null) kindLabel(k) else "+ ${kindLabel(k)}",
                icon = kindIcon(k),
                set = p != null,
                onClick = { if (p != null) onOpen(p) else onEdit(k, null) },
                onLongClick = { onEdit(k, p) },
            )
        }
        places.filter { it.kind == PlaceKind.OTHER }.forEach { p ->
            ShortcutChip(p.name, kindIcon(p.kind), set = true, onClick = { onOpen(p) }, onLongClick = { onEdit(p.kind, p) })
        }
        IconButton(onClick = onManage) { Icon(Icons.Filled.Edit, contentDescription = "تعديل الأماكن المتكررة") }
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, set: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .then(if (set) Modifier.background(colors.secondaryContainer) else Modifier.border(BorderStroke(1.dp, colors.outline), shape))
            .combinedClickable(onClickLabel = if (set) "اذهب إلى $label" else "حفظ $label", onLongClickLabel = "تعديل", onLongClick = onLongClick, onClick = onClick)
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (set) colors.onSecondaryContainer else colors.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

@Composable
private fun ManagePlacesDialog(places: List<SavedPlace>, onPick: (PlaceKind, SavedPlace?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الأماكن المتكررة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val rows = MAIN_KINDS.map { k -> k to places.firstOrNull { it.kind == k } } +
                    places.filter { it.kind == PlaceKind.OTHER }.map { it.kind to it }
                rows.forEach { (k, p) ->
                    ListItem(
                        headlineContent = { Text(if (k == PlaceKind.OTHER && p != null) p.name else kindLabel(k)) },
                        supportingContent = { Text(p?.name ?: "غير محدد", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(kindIcon(k), contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(k, p) },
                    )
                }
                ListItem(
                    headlineContent = { Text("+ مكان جديد") },
                    leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(PlaceKind.OTHER, null) },
                )
                Text(
                    "تلميح: اضغط مطولاً على أي اختصار لتعديله.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun PlaceEditDialog(edit: PlaceEdit, plan: PlanState, onSave: (SavedPlace) -> Unit, onDelete: (SavedPlace) -> Unit, onDismiss: () -> Unit) {
    val (kind, place) = edit
    val isNewOther = kind == PlaceKind.OTHER && place == null
    val label = if (kind == PlaceKind.OTHER) place?.name ?: "مكان جديد" else kindLabel(kind)
    fun save(pt: Pt, name: String) = onSave(
        SavedPlace(
            id = place?.id ?: UUID.randomUUID().toString(),
            kind = kind,
            name = name.ifBlank { if (kind == PlaceKind.OTHER) "مكان محفوظ" else kindLabel(kind) },
            pt = pt,
        )
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(kindIcon(kind), contentDescription = null) },
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        place != null -> "المحفوظ حالياً: ${place.name}"
                        plan.destination == null && plan.origin == null ->
                            "اختر الوجهة أولاً (بالبحث أو بالضغط على الخريطة)، ثم اضغط «+ $label» لحفظها والوصول إليها بضغطة واحدة."
                        else -> "احفظ نقطة من رحلتك الحالية لتصل إليها بضغطة واحدة."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                plan.destination?.let { d ->
                    TextButton(onClick = { save(d, plan.destinationName) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isNewOther) "احفظ الوجهة الحالية كمكان جديد" else "اجعل الوجهة الحالية $label")
                    }
                }
                plan.origin?.let { o ->
                    TextButton(onClick = { save(o, plan.originName) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isNewOther) "احفظ البداية الحالية كمكان جديد" else "اجعل البداية الحالية $label")
                    }
                }
                place?.let { p ->
                    TextButton(
                        onClick = { onDelete(p) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("حذف الاختصار") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun WalkPresetRow(label: String, valueS: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        WalkPresetChips(valueS, enabled = enabled, onChange = onChange)
    }
}

private fun objectiveLabel(o: Objective) = when (o) {
    Objective.FASTEST -> "أصل أسرع"
    Objective.CHEAPEST -> "أدفع أقل"
}

/** "أصل أسرع · مشي حتى 5 د": what the search will do, without opening the options. */
private fun optionsSummary(plan: PlanState, night: Boolean): String {
    val walk = when {
        night -> "بلا مشي بعد المغرب"
        plan.maxWalkStartS == 0 && plan.maxWalkEndS == 0 -> "بلا مشي"
        plan.maxWalkStartS == plan.maxWalkEndS -> "مشي حتى ${fmtDuration(plan.maxWalkStartS)}"
        else -> "مشي حتى ${fmtDuration(plan.maxWalkStartS)} قبل و${fmtDuration(plan.maxWalkEndS)} بعد"
    }
    return "${objectiveLabel(plan.objective)} · $walk"
}

@Composable
private fun ObjectiveChoice(selected: Objective, onSelect: (Objective) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Objective.entries.forEachIndexed { i, o ->
            SegmentedButton(selected == o, { onSelect(o) }, SegmentedButtonDefaults.itemShape(i, Objective.entries.size)) {
                Text(objectiveLabel(o))
            }
        }
    }
}

@Composable
private fun InfoNote(text: String, icon: ImageVector = Icons.Filled.Info) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

private fun stageLabel(s: SearchStage) = when (s) {
    SearchStage.CANDIDATES -> "نبحث عن نقاط ركوب قريبة…"
    SearchStage.WALKING -> "نحسب مسافات المشي…"
    SearchStage.DRIVING -> "نقارن مسارات السيارة…"
    SearchStage.VERIFYING -> "نتحقق من المسار الفعلي…"
    SearchStage.NAMING -> "نحدد المعالم القريبة…"
}

@Composable
private fun SearchProgress(stage: SearchStage, onCancel: () -> Unit) {
    val total = SearchStage.entries.size
    val progress by animateFloatAsState((stage.ordinal + 1f) / total, label = "searchStage")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stageLabel(stage), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(
                    "الخطوة ${stage.ordinal + 1} من $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onCancel) { Text("إلغاء") }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SearchActions(vm: MainViewModel, status: SearchStatus, plan: PlanState, quota: Quota) {
    val ready = plan.origin != null && plan.destination != null
    val quotaFull = quota.maxSearches > 0 && quota.searches >= quota.maxSearches
    val quotaNear = quota.maxSearches > 0 && quota.searches * 5 >= quota.maxSearches * 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            is SearchStatus.Loading -> SearchProgress(status.stage, vm::cancelSearch)
            is SearchStatus.Error -> {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { vm.search() }, enabled = ready && !quotaFull, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("حاول مرة أخرى")
                }
            }
            // Refreshing lives on the results screen; one button here.
            is SearchStatus.Done -> Button(onClick = { vm.go(Screen.RESULTS) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("عرض النتائج")
            }
            SearchStatus.Idle -> {
                Button(onClick = { vm.search() }, enabled = ready && !quotaFull, modifier = Modifier.fillMaxWidth().height(52.dp)) {
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
        if (quotaNear) {
            Text(
                if (quotaFull) "استُنفدت حصة البحث هذا الشهر: ${quota.searches} / ${quota.maxSearches}"
                else "اقتربت من حد البحث الشهري: ${quota.searches} / ${quota.maxSearches}",
                style = MaterialTheme.typography.labelMedium,
                color = if (quotaFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
