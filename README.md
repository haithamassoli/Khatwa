<p align="center">
  <img src="logo.svg" width="96" alt="Khatwa logo">
</p>

<h1 align="center">Khatwa · خطوة</h1>

<p align="center">
  Walk a few steps, ride the rest.<br>
  An Android app that finds a better pickup and dropoff point for your ride-hailing trip.
</p>

<p align="center">
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Google Maps Platform" src="https://img.shields.io/badge/Google%20Maps-Routes%20API-34A853?logo=googlemaps&logoColor=white">
</p>

---

Sometimes the door-to-door ride isn't the fastest one. If you walk two minutes to the other side of a divided road, or get dropped off one block early, the driver can skip a U-turn, a one-way loop, or a traffic jam. Khatwa checks the nearby pickup and dropoff points and tells you which pair saves time or distance compared with the direct trip.

The UI is in Arabic (RTL), and the walking heuristics are tuned for Amman's hills and divided roads.

## Screenshots

<p align="center">
  <img src="docs/screenshots/1-plan.png" width="260" alt="Plan screen: start, destination and one search button; options folded into one line">
  &nbsp;
  <img src="docs/screenshots/2-results.png" width="260" alt="Results: here no nearby point saves enough, so the app says the direct trip is best">
  &nbsp;
  <img src="docs/screenshots/3-detail.png" width="260" alt="Detail: route on the map, verified a minute ago, numbers up front">
</p>

<p align="center">
  <sub><b>Plan</b>: one search button, options folded into a one-line summary &nbsp;·&nbsp; <b>Results</b>: an honest "the direct trip is best" when walking doesn't pay &nbsp;·&nbsp; <b>Detail</b>: when the route was verified, numbers before the fine print</sub>
</p>

## Features

- **Savings first, trade-offs included**: each alternative leads with its saving and anything it costs you ("أرخص بـ 0.30 د.أ · أبطأ بـ 4 دقائق"), one sentence of directions ("امشِ 3 دقائق إلى أمام صيدلية X") and a mini map of the direct vs. alternative route.
- **Verified before shown**: the best alternatives and the direct trip are re-checked with a detailed traffic-aware route. If the saving disappears, the alternative is dropped, so the results and detail screens never contradict each other.
- **Only worthwhile deals**: an alternative must save at least 3 minutes (or 10%) and walking must cost less than the drive time it saves; when you'd rather pay less, it must save at least 0.25 JD (or 10%) on the estimated fare.
- **Book in one tap**: Uber opens with pickup and dropoff pre-filled; for Careem and Jeeny the pickup landmark name (or coordinates for unnamed points) is copied so their search can find it, and the app opens. "ابدأ المشي" starts Google Maps walking navigation to the pickup.
- **Share from Google Maps**: share any place to Khatwa (or open a `geo:` / Maps link) and it searches from your current location right away.
- **Frequent trips**: Home / Work / University shortcuts, a home-screen widget and a Quick Settings tile. The widget shows the last answer for each place ("إلى الجامعة · امشِ 3 د ووفّر 6 د").
- **Landmark directions**: suggested points are described by the nearest landmark and street, not compass bearings.
- **Amman-aware walking**: uphill walks get a climb penalty (Elevation API), and an optional "no walking after sunset" mode.
- **Fare estimate**: a rough fare for each option, so you can see the time vs. money trade-off.
- **Post-trip feedback**: after a booked trip the app asks 👍 / 👎; good points are saved, bad ones get disabled.
- **Result cache**: repeated trips at the same hour reuse a recent result without spending quota.
- **Pick start and destination** by tapping the map, searching places, or using your current location.
- **Walking limits**: preset chips for before pickup and after dropoff (none, 2, 5 or 10 min).
- **Two goals**: "أصل أسرع" (least total time, walk + drive) or "أدفع أقل" (lowest estimated fare).
- **Honest comparison**: every alternative is shown next to the direct door-to-door trip, with the difference in total time, drive time, distance and walking. Options that are worse on every metric are dropped.
- **Traffic-aware**: drive times use live traffic, departing when you'd actually reach the pickup point.
- **Saved points**: keep your own reviewed pickup/dropoff spots with road side and a nearby landmark. When you have none, the app suggests points on nearby roads automatically.
- **Report bad points**: mark a point as wrong side of the road or unsafe to stop, and it's excluded from future searches.
- **Fare log**: one number and a ride-app chip to record a quoted price, so you can see over time whether it actually saves money.
- **Cost guard**: a monthly search cap (100 searches) and at most 25 drive-matrix elements per search keep Google Maps billing predictable.

## How it works

1. Up to 5 candidate points per side are picked: the exact start/destination, then your saved points nearest-first, then auto-suggested points snapped to roads (Roads API) in rings around you.
2. Walking times to each candidate come from the Routes API walk matrix. Candidates over your walking limit are dropped before any drive elements are requested.
3. A traffic-aware drive matrix covers every remaining pickup × dropoff pair.
4. [`Engine.rank`](app/src/main/java/com/haithamassoli/khatwa/Engine.kt) keeps only the Pareto-optimal pairs that beat the direct trip by the minimum saving on your chosen goal (time, or estimated fare), and returns the top 3.
5. The direct trip and the top alternatives get a detailed traffic-aware route. Alternatives whose saving no longer clears the threshold are dropped.
6. Auto-suggested points in the final list are named after the nearest landmark (Places API) and street.

The ranking engine is plain Kotlin with no Android dependencies and has unit tests.

## Getting started

### Requirements

- Android Studio (recent, with AGP 9.x support)
- JDK 17
- A device or emulator on Android 12 (API 31) or newer
- A Google Maps Platform API key

### Google Maps API key

Enable these APIs in your Google Cloud project:

- Maps SDK for Android
- Routes API
- Roads API
- Places API (New)
- Geocoding API
- Elevation API

Then add the key to `local.properties` in the project root (this file is git-ignored):

```properties
MAPS_API_KEY=your_api_key_here
```

> [!TIP]
> Restrict the key to Android apps with your package name (`com.haithamassoli.khatwa`) and SHA-1 certificate fingerprint, and limit it to the APIs above. The app sends `X-Android-Package` / `X-Android-Cert` headers on its REST calls, so this restriction also covers the Routes, Places, Roads, Geocoding and Elevation requests.

### Server proxy (recommended before publishing)

By default the app calls Google directly, which means `MAPS_API_KEY` is compiled into the APK. Anyone can extract it, and the 100-searches-a-month cap is only checked on the device (clearing app data resets it).

[`server/`](server/README.md) contains a small Cloudflare Worker that fixes this:

- The Google key lives only on the server, as a Worker secret.
- Each install gets a random id (`filesDir/install.json`), and the Worker enforces the monthly search quota per install plus a daily cap per IP.
- Only the Google endpoints the app uses are forwarded, with matrix-size and field-mask checks.
- It also stores community pickup/dropoff points (`CommunityPoints.kt` is the client).

To use it, deploy the Worker (steps in [server/README.md](server/README.md#deploy)), then add its URL to `local.properties`:

```properties
KHATWA_PROXY_URL=https://khatwa-proxy.<account>.workers.dev
MAPS_API_KEY=your_android_restricted_maps_sdk_key
```

When `KHATWA_PROXY_URL` is set:

- Every REST call (Routes, Roads, Places, Geocoding, Elevation) goes to the proxy with an `X-Khatwa-Install` header and no key.
- `BuildConfig.MAPS_API_KEY` is left empty.
- `MAPS_API_KEY` is then used only for the Maps SDK map tiles (manifest). That key is safe to ship **only** if it's restricted to Android apps (package `com.haithamassoli.khatwa` + your SHA-1 fingerprints) and to the Maps SDK for Android. The REST key must never be shipped; keep it in the Worker.

Leave `KHATWA_PROXY_URL` empty to keep calling Google directly (handy for local development).

> [!NOTE]
> The install id is random and isn't tied to an account, so reinstalling gives a fresh quota. The per-IP cap limits abuse. For real attestation, add Play Integrity checks to the Worker.

### Build and run

```bash
git clone https://github.com/haithamassoli44/Khatwa.git
cd Khatwa
./gradlew installDebug
```

Or open the project in Android Studio and press **Run**.

### Tests

```bash
./gradlew testDebugUnitTest
```

## Project structure

```
app/src/main/java/com/haithamassoli/khatwa/
├── MainActivity.kt      # Entry point, forces RTL, screen switch
├── MainViewModel.kt     # UI state and actions
├── Model.kt             # Shared data types (seconds and meters everywhere)
├── Engine.kt            # Pure ranking logic (Pareto filter + sort)
├── Searcher.kt          # Candidate selection, matrix calls, quota
├── Google.kt            # Google Maps Platform REST client (direct or via the proxy)
├── CommunityPoints.kt   # Client for shared points on the proxy
├── Install.kt           # Random install id for the proxy quota
├── Store.kt             # Local JSON storage for points, fares, quota
├── PlanScreen.kt        # Start/destination/walk limits
├── ResultsScreen.kt     # Direct vs. alternatives
├── DetailScreen.kt      # Route map + fare logging
├── PointsScreen.kt      # Saved points
├── HistoryScreen.kt     # Fare history
├── OnboardingScreen.kt  # One-time welcome screen
├── SettingsSheet.kt     # Night mode, default walk time
├── ResultCache.kt       # Per-route/hour result cache
├── Fare.kt              # Rough fare estimate
├── Night.kt             # Sunset calculation
├── Integrations.kt      # Booking deep links, walking navigation
├── IncomingLinks.kt     # Shared places, geo: and Maps links
├── FeedbackNotifier.kt  # Post-trip 👍/👎 notification
├── TripWidget.kt        # Home-screen widget
└── QuickTripTile.kt     # Quick Settings tile
server/                  # Optional Cloudflare Worker proxy (see server/README.md)
```

## Privacy

There's no backend and no account. Saved points, fare history and the quota counter are stored as JSON files in the app's private storage on your device. Coordinates are sent only to Google Maps Platform to calculate routes. If you build with the optional proxy, requests pass through your own Worker. It stores a random install id with a monthly search count, per-IP daily counters (kept for 2 days), and any community point votes. It doesn't store routes or searches.

## Disclaimer

Results come from a limited set of sampled points and don't guarantee the best possible spot or the lowest fare. Check the price in your ride-hailing app before booking, and make sure the pickup spot is safe and legal to stop at.
