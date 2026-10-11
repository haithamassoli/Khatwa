package com.haithamassoli.khatwa

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

enum class Screen { PLAN, RESULTS, DETAIL, POINTS, HISTORY }

sealed interface SearchStatus {
    data object Idle : SearchStatus
    data class Loading(val stage: SearchStage = SearchStage.CANDIDATES) : SearchStatus
    data class Done(val result: SearchResult) : SearchStatus
    data class Error(val message: String) : SearchStatus
}

data class PlanState(
    val origin: Pt? = null,
    val originName: String = "",
    val destination: Pt? = null,
    val destinationName: String = "",
    val maxWalkStartS: Int = 300,
    val maxWalkEndS: Int = 300,
    val objective: Objective = Objective.MODELED,
)

// Shared UI contract. UI agents read state and call methods; don't add screens' private state here.
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = AppStore.get(app) // shared with the widget, quick tile and feedback receiver
    val google = Google(BuildConfig.MAPS_API_KEY, BuildConfig.KHATWA_PROXY_URL, Install.id(app), app.packageName, Install.certSha1(app))
    private val searcher = Searcher(google, store)

    var screen by mutableStateOf(Screen.PLAN); private set
    var plan by mutableStateOf(PlanState()); private set
    var status by mutableStateOf<SearchStatus>(SearchStatus.Idle); private set
    var selected by mutableStateOf<RouteOption?>(null); private set
    var detail by mutableStateOf<OptionDetail?>(null); private set
    var detailError by mutableStateOf<String?>(null); private set
    var locationMessage by mutableStateOf<String?>(null); private set

    /** Set when the app was opened from a share / geo link and should search once the origin is known. */
    var pendingAutoSearch by mutableStateOf(false); private set

    init {
        val d = store.settings.value.defaultWalkS
        plan = plan.copy(maxWalkStartS = d, maxWalkEndS = d)
    }

    private var requestId = 0L
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    fun go(s: Screen) { screen = s }

    /** System back. Returns false when already on the root screen. */
    fun back(): Boolean {
        screen = when (screen) {
            Screen.PLAN -> return false
            Screen.DETAIL -> Screen.RESULTS
            else -> Screen.PLAN
        }
        return true
    }

    // FR-14: any input change drops the current result and orphans any in-flight request.
    private fun edit(f: (PlanState) -> PlanState) {
        val next = f(plan)
        if (next == plan) return
        plan = next
        cancelSearch()
        status = SearchStatus.Idle
    }

    fun setOrigin(pt: Pt, name: String = "") { edit { it.copy(origin = pt, originName = name) }; if (name.isEmpty()) nameLater(pt, true) }
    fun setDestination(pt: Pt, name: String = "") { edit { it.copy(destination = pt, destinationName = name) }; if (name.isEmpty()) nameLater(pt, false) }
    fun swap() = edit { it.copy(origin = it.destination, originName = it.destinationName, destination = it.origin, destinationName = it.originName) }
    fun setWalkStart(s: Int) = edit { it.copy(maxWalkStartS = s.coerceIn(0, 600)) }
    fun setWalkEnd(s: Int) = edit { it.copy(maxWalkEndS = s.coerceIn(0, 600)) }
    fun setObjective(o: Objective) = edit { it.copy(objective = o) }

    private fun nameLater(pt: Pt, origin: Boolean) = viewModelScope.launch {
        val name = runCatching { google.reverseGeocode(pt) }.getOrNull() ?: return@launch
        // Only apply if the point hasn't changed meanwhile; naming is cosmetic so it doesn't reset results.
        plan = if (origin && plan.origin == pt) plan.copy(originName = name)
        else if (!origin && plan.destination == pt) plan.copy(destinationName = name) else plan
    }

    /** Call only after location permission is granted. */
    @SuppressLint("MissingPermission")
    fun useCurrentLocation() = viewModelScope.launch {
        locationMessage = null
        val loc = runCatching {
            LocationServices.getFusedLocationProviderClient(getApplication<Application>())
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        }.getOrNull()
        if (loc == null) { locationMessage = "تعذر تحديد الموقع. اختر نقطة البداية على الخريطة."; return@launch }
        setOrigin(Pt(loc.latitude, loc.longitude))
        if (loc.accuracy > 50f) locationMessage = "دقة الموقع ضعيفة (±${loc.accuracy.toInt()} م). تأكد من النقطة على الخريطة أو اسحبها."
    }

    /** [force] bypasses the local result cache. */
    fun search(force: Boolean = false) {
        val p = plan
        val o = p.origin ?: return
        val d = p.destination ?: return
        cancelSearch()
        val id = ++requestId
        status = SearchStatus.Loading()
        // Night rule: no walking after sunset when the user opted in.
        val night = store.settings.value.noWalkAtNight && Night.isNight(o)
        val input = SearchInput(o, d, if (night) 0 else p.maxWalkStartS, if (night) 0 else p.maxWalkEndS, p.objective)
        searchJob = viewModelScope.launch {
            val next = try {
                SearchStatus.Done(searcher.search(id, input, force) { stage ->
                    if (id == requestId && status is SearchStatus.Loading) status = SearchStatus.Loading(stage)
                }.let { if (night) it.copy(night = true) else it })
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuotaExceededException) {
                SearchStatus.Error("استُنفدت حصة البحث الشهرية. تتجدد في ${e.resetsOn}.")
            } catch (e: Exception) {
                SearchStatus.Error("تعذر إكمال البحث: ${e.message ?: "خطأ في الشبكة"}. المدخلات محفوظة، حاول مرة أخرى.")
            }
            if (id != requestId) return@launch // stale response (PRD §10)
            status = next
            if (next is SearchStatus.Done) screen = Screen.RESULTS
        }
    }

    fun cancelSearch() {
        requestId++
        searchJob?.cancel()
        searchJob = null
        if (status is SearchStatus.Loading) status = SearchStatus.Idle
    }

    fun openOption(o: RouteOption) {
        val input = (status as? SearchStatus.Done)?.result?.input ?: return
        selected = o
        detail = null
        detailError = null
        screen = Screen.DETAIL
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                detail = searcher.detail(o, input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = "تعذر تحميل المسار التفصيلي: ${e.message ?: ""}"
            }
        }
    }

    /** Opened from a shared place / geo link: set destination, then search from the current location (see [pendingAutoSearch]). */
    fun applySharedDestination(pt: Pt, name: String) {
        setDestination(pt, name)
        screen = Screen.PLAN
        pendingAutoSearch = true
    }

    /** UI calls this once the origin is set (or the user dismissed); runs the deferred search at most once. */
    fun consumeAutoSearch() {
        if (!pendingAutoSearch) return
        pendingAutoSearch = false
        if (plan.origin != null && plan.destination != null) search()
    }

    /** Start a frequent trip (shortcut, widget, quick tile): current origin stays unless [from] is given. */
    fun startTrip(to: SavedPlace, from: SavedPlace? = null) {
        if (from != null) setOrigin(from.pt, from.name)
        applySharedDestination(to.pt, to.name)
    }

    // Saved places & settings
    fun savePlace(p: SavedPlace) { store.upsertPlace(p); TripWidget.refresh(getApplication()) }
    fun deletePlace(id: String) { store.deletePlace(id); TripWidget.refresh(getApplication()) }
    fun updateSettings(f: (Settings) -> Settings) = store.updateSettings(f)

    // Booking & feedback
    /** Record a booked trip and schedule the 👍/👎 notification for after roughly the trip duration. */
    fun tripBooked(o: RouteOption): PendingTrip {
        val now = System.currentTimeMillis()
        val trip = PendingTrip(UUID.randomUUID().toString(), now, now + (o.modeledS + 300) * 1000L, o.pickup, o.dropoff)
        store.addPendingTrip(trip)
        FeedbackNotifier.schedule(getApplication(), trip)
        return trip
    }

    fun tripFeedback(t: PendingTrip, good: Boolean?) {
        if (good != null) { store.vote(t.pickup, good); store.vote(t.dropoff, good) }
        store.removePendingTrip(t.id)
        FeedbackNotifier.cancel(getApplication(), t.id)
    }

    // Points
    fun saveCandidate(c: Candidate) = store.upsertCandidate(c)
    fun deleteCandidate(id: String) = store.deleteCandidate(id)
    fun newCandidate(pt: Pt, name: String, roadSide: String, landmark: String, use: PointUse) =
        store.upsertCandidate(Candidate(UUID.randomUUID().toString(), pt, name, roadSide, landmark, use))

    /** FR-17: mark unsuitable / wrong road side. Disables it for future searches. Auto points get saved as reported. */
    fun reportPoint(c: Candidate, reason: String) = store.upsertCandidate(
        c.copy(id = if (c.auto || c.isDirect) UUID.randomUUID().toString() else c.id,
            verification = Verification.REPORTED, enabled = false, auto = false, isDirect = false,
            landmark = listOf(c.landmark, "بلاغ: $reason").filter { it.isNotBlank() }.joinToString(" · "))
    )

    /** Keep an auto-suggested point as a reviewed one. */
    fun keepAutoPoint(c: Candidate, name: String, roadSide: String) = store.upsertCandidate(
        c.copy(id = UUID.randomUUID().toString(), name = name, roadSide = roadSide, auto = false, isDirect = false, verification = Verification.VERIFIED)
    )

    // History
    fun logFare(f: FareObservation) = store.addFare(f)
    fun deleteFare(id: String) = store.deleteFare(id)
    fun clearHistory() = store.clearHistory()
}
