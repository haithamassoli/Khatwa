package com.haithamassoli.khatwa

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import java.net.URLEncoder
import java.util.Locale

/** What [Integrations.book] did, so the UI can react (e.g. record the trip only when an app actually opened). */
enum class BookResult {
    /** Opened the ride app with pickup/dropoff prefilled (Uber deep link). */
    OPENED_PREFILLED,
    /** Copied the pickup address to the clipboard and opened the ride app. */
    OPENED_WITH_CLIPBOARD,
    /** The app isn't installed: opened its web booking page (Uber) instead. */
    OPENED_WEB,
    /** The app isn't installed: opened its Play Store page. */
    OPENED_STORE,
    FAILED,
}

/** Booking in ride-hailing apps and walking navigation in Google Maps. */
object Integrations {

    fun packageName(app: RideApp): String = when (app) {
        RideApp.UBER -> "com.ubercab"
        RideApp.CAREEM -> "com.careem.acma"
        RideApp.JEENY -> "me.com.easytaxi" // Jeeny, formerly Easy Taxi (verified on Google Play)
    }

    /** Arabic display name for buttons. */
    fun label(app: RideApp): String = when (app) {
        RideApp.UBER -> "أوبر"
        RideApp.CAREEM -> "كريم"
        RideApp.JEENY -> "جيني"
    }

    fun isInstalled(context: Context, app: RideApp): Boolean = isInstalled(context, packageName(app))

    /** Installed ride apps first (in enum order), then the others; use [isInstalled] to tell them apart. */
    fun rideAppsInstalledFirst(context: Context): List<RideApp> = RideApp.entries.sortedBy { !isInstalled(context, it) }

    /** Only the installed ride apps. */
    fun installedRideApps(context: Context): List<RideApp> = RideApp.entries.filter { isInstalled(context, it) }

    /**
     * Opens [app] to book pickup → dropoff. Uber gets a prefilled deep link; Careem / Jeeny have no public deep link,
     * so the pickup address is copied to the clipboard and the app is launched. Not installed → web / Play Store.
     * Call [MainViewModel.tripBooked] yourself if the result isn't [BookResult.FAILED].
     */
    fun book(context: Context, app: RideApp, pickup: Pt, pickupName: String, dropoff: Pt, dropoffName: String): BookResult =
        when (app) {
            RideApp.UBER -> bookUber(context, pickup, pickupName, dropoff, dropoffName)
            RideApp.CAREEM, RideApp.JEENY -> bookWithClipboard(context, app, pickup, pickupName)
        }

    /** Convenience overload for a result row. */
    fun book(context: Context, app: RideApp, option: RouteOption): BookResult =
        book(context, app, option.pickup.pt, option.pickup.name, option.dropoff.pt, option.dropoff.name)

    fun uberQuery(pickup: Pt, pickupName: String, dropoff: Pt, dropoffName: String): String = listOf(
        "action" to "setPickup",
        "pickup[latitude]" to coord(pickup.lat),
        "pickup[longitude]" to coord(pickup.lng),
        "pickup[nickname]" to pickupName,
        "dropoff[latitude]" to coord(dropoff.lat),
        "dropoff[longitude]" to coord(dropoff.lng),
        "dropoff[nickname]" to dropoffName,
    ).filter { it.second.isNotBlank() }.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun bookUber(context: Context, pickup: Pt, pickupName: String, dropoff: Pt, dropoffName: String): BookResult {
        val q = uberQuery(pickup, pickupName, dropoff, dropoffName)
        val app = Intent(Intent.ACTION_VIEW, "uber://?$q".toUri()).setPackage(packageName(RideApp.UBER))
        if (start(context, app)) return BookResult.OPENED_PREFILLED
        return if (start(context, Intent(Intent.ACTION_VIEW, "https://m.uber.com/ul/?$q".toUri()))) BookResult.OPENED_WEB
        else BookResult.FAILED
    }

    /**
     * Text placed on the clipboard for apps without a deep link: something their place search can find.
     * "أمام صيدلية الشفاء" → "صيدلية الشفاء"; unnamed points fall back to coordinates.
     */
    fun pickupClipText(pickup: Pt, pickupName: String): String {
        val name = pickupName.trim().removePrefix("أمام ").removePrefix("قرب ").trim()
        val unnamed = name.isEmpty() || name.startsWith("نقطة مقترحة") || name.startsWith("نقطة على الخريطة")
        return if (unnamed) "${coord(pickup.lat)},${coord(pickup.lng)}" else name
    }

    private fun bookWithClipboard(context: Context, app: RideApp, pickup: Pt, pickupName: String): BookResult {
        val pkg = packageName(app)
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            val text = pickupClipText(pickup, pickupName)
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("نقطة الركوب", text))
            Toast.makeText(context, "نُسخ «$text»: الصقه في خانة الركوب في ${label(app)}", Toast.LENGTH_LONG).show()
            if (start(context, launch)) return BookResult.OPENED_WITH_CLIPBOARD
        }
        return if (openStore(context, pkg)) BookResult.OPENED_STORE else BookResult.FAILED
    }

    fun openStore(context: Context, pkg: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())) ||
            start(context, Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()))

    /** Google Maps walking navigation to [to]; falls back to a Maps directions URL (browser or any maps app). */
    fun startWalking(context: Context, to: Pt): Boolean {
        val ll = "${coord(to.lat)},${coord(to.lng)}"
        val nav = Intent(Intent.ACTION_VIEW, "google.navigation:q=$ll&mode=w".toUri()).setPackage(MAPS_PACKAGE)
        if (start(context, nav)) return true
        val web = Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/dir/?api=1&destination=$ll&travelmode=walking".toUri())
        if (start(context, web)) return true
        Toast.makeText(context, "تعذر فتح الخرائط", Toast.LENGTH_SHORT).show()
        return false
    }

    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    // Needs the <queries> entries in the manifest (package visibility).
    private fun isInstalled(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun coord(d: Double) = String.format(Locale.US, "%.6f", d)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** One shared [Store] per process so the view model, widget, tile and notification receiver never overwrite each other. */
object AppStore {
    @Volatile private var instance: Store? = null

    fun get(context: Context): Store = instance ?: synchronized(this) {
        instance ?: Store(context.applicationContext).also { instance = it }
    }
}

/** Opening MainActivity for a frequent trip (widget, quick settings tile, shortcuts). */
object TripLaunch {
    const val ACTION = "com.haithamassoli.khatwa.TRIP"
    const val EXTRA_PLACE_ID = "place_id"

    fun intent(context: Context, placeId: String?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(if (placeId != null) ACTION else Intent.ACTION_MAIN)
            .apply { if (placeId != null) putExtra(EXTRA_PLACE_ID, placeId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Widget order: home, work, university, then the rest. */
    fun ordered(places: List<SavedPlace>): List<SavedPlace> = places.sortedBy { it.kind.ordinal }

    /** Quick tile target: mornings (before 14:00) go to work/university, later go home; else any saved place. */
    fun quickPlace(places: List<SavedPlace>, hour: Int): SavedPlace? {
        val prefs = if (hour < 14) listOf(PlaceKind.WORK, PlaceKind.UNIVERSITY, PlaceKind.HOME)
        else listOf(PlaceKind.HOME, PlaceKind.WORK, PlaceKind.UNIVERSITY)
        return prefs.firstNotNullOfOrNull { k -> places.firstOrNull { it.kind == k } } ?: places.firstOrNull()
    }

    /** Widget button: destination plus the last result, so the common answer is visible without opening the app. */
    fun widgetLabel(p: SavedPlace): String = if (p.tip.isBlank()) label(p) else "${label(p)} · ${p.tip}"

    fun label(p: SavedPlace): String = when (p.kind) {
        PlaceKind.HOME -> "إلى البيت"
        PlaceKind.WORK -> "إلى العمل"
        PlaceKind.UNIVERSITY -> "إلى الجامعة"
        PlaceKind.OTHER -> "إلى ${p.name}"
    }
}
