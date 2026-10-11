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
  <img src="docs/screenshots/1-plan.png" width="260" alt="Plan screen: pick start, destination and max walking time">
  &nbsp;
  <img src="docs/screenshots/2-results.png" width="260" alt="Results: direct trip compared with the best alternative">
  &nbsp;
  <img src="docs/screenshots/3-detail.png" width="260" alt="Option detail: walk and drive legs on the map">
</p>

<p align="center">
  <sub><b>Plan</b>: set start, destination and how far you'll walk &nbsp;·&nbsp; <b>Results</b>: alternatives compared with the direct ride &nbsp;·&nbsp; <b>Detail</b>: walk and drive legs on the map</sub>
</p>

## Features

- **Pick start and destination** by tapping the map, searching places, or using your current location.
- **Walking limits**: set the most you'll walk before pickup and after dropoff (0–10 min each).
- **Three ranking goals**: total trip time (walk + drive), drive time only, or drive distance only.
- **Honest comparison**: every alternative is shown next to the direct door-to-door trip, with the difference in total time, drive time, distance and walking. Options that are worse on every metric are dropped.
- **Traffic-aware**: drive times use live traffic, departing when you'd actually reach the pickup point.
- **Saved points**: keep your own reviewed pickup/dropoff spots with road side and a nearby landmark. When you have none, the app suggests points on nearby roads automatically.
- **Report bad points**: mark a point as wrong side of the road or unsafe to stop, and it's excluded from future searches.
- **Fare log**: record the price the ride app quoted for the direct trip and for the alternative, so you can see over time whether it actually saves money.
- **Cost guard**: a monthly search cap (100 searches) and at most 25 drive-matrix elements per search keep Google Maps billing predictable.

## How it works

1. Up to 5 candidate points per side are picked: the exact start/destination, then your saved points nearest-first, then auto-suggested points snapped to roads (Roads API) in rings around you.
2. Walking times to each candidate come from the Routes API walk matrix. Candidates over your walking limit are dropped before any drive elements are requested.
3. A traffic-aware drive matrix covers every remaining pickup × dropoff pair.
4. [`Engine.rank`](app/src/main/java/com/haithamassoli/khatwa/Engine.kt) keeps only the Pareto-optimal pairs that beat the direct trip on your chosen goal, and returns the top 3.

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

Then add the key to `local.properties` in the project root (this file is git-ignored):

```properties
MAPS_API_KEY=your_api_key_here
```

> [!TIP]
> Restrict the key to your app's package name (`com.haithamassoli.khatwa`) and SHA-1 certificate fingerprint, and limit it to the APIs above.

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
└── HistoryScreen.kt     # Fare history
server/                  # Optional Cloudflare Worker proxy (see server/README.md)
```

## Privacy

There's no backend and no account. Saved points, fare history and the quota counter are stored as JSON files in the app's private storage on your device. Coordinates are sent only to Google Maps Platform to calculate routes. If you build with the optional proxy, requests pass through your own Worker. It stores a random install id with a monthly search count, per-IP daily counters (kept for 2 days), and any community point votes. It doesn't store routes or searches.

## Disclaimer

Results come from a limited set of sampled points and don't guarantee the best possible spot or the lowest fare. Check the price in your ride-hailing app before booking, and make sure the pickup spot is safe and legal to stop at.
