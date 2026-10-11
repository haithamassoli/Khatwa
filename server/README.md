# Khatwa proxy (Cloudflare Worker)

A small Worker that sits between the Khatwa app and Google Maps Platform. It keeps the Google key on the server, enforces a per-install monthly search quota and a per-IP daily cap, and stores community pickup/dropoff points.

## Routes

Every request needs `X-Khatwa-Install: <uuid>` (a random id the app creates on first launch). Errors use Google's shape, `{"error":{"message":"..."}}`, so the app's existing error parsing works.

| Proxy route | Method | Google endpoint | Key sent as |
|---|---|---|---|
| `/routes/matrix` | POST | `routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix` | `X-Goog-Api-Key` |
| `/routes/route` | POST | `routes.googleapis.com/directions/v2:computeRoutes` | `X-Goog-Api-Key` |
| `/places/searchText` | POST | `places.googleapis.com/v1/places:searchText` | `X-Goog-Api-Key` |
| `/places/searchNearby` | POST | `places.googleapis.com/v1/places:searchNearby` | `X-Goog-Api-Key` |
| `/roads/nearestRoads` | GET | `roads.googleapis.com/v1/nearestRoads` | `key=` |
| `/geocode/json` | GET | `maps.googleapis.com/maps/api/geocode/json` | `key=` |
| `/elevation/json` | GET | `maps.googleapis.com/maps/api/elevation/json` | `key=` |
| `/quota` | GET | none | returns `{month, searches, max}` |
| `/points/nearby?lat=&lng=&radius=` | GET | none | community points |
| `/points/feedback` | POST | none | `{lat, lng, use, good, name?}` |
| `/health` | GET | none | no auth |

Anything else returns 404. The client's `X-Goog-FieldMask` is forwarded; client-supplied keys (header or `key=` param) are dropped and replaced with the server key.

## Limits

- **Searches**: one successful DRIVE `/routes/matrix` call = one search. `MONTHLY_SEARCHES` (default 100) per install per month, in Amman time to match the app. A failed traffic-aware call followed by the app's traffic-unaware retry counts once. Over the limit: HTTP 429 with an Arabic message.
- **Per IP**: `IP_DAILY_REQUESTS` (default 3000) requests per IP per UTC day. Jordanian mobile carriers use carrier-grade NAT, so many users can share one IP. Keep this generous.
- **Body**: at most 32 KB. Matrix size: at most 25 elements for DRIVE and 10 for WALK. Only `WALK` and `DRIVE` travel modes are allowed.
- **Field masks** are required on POST routes and can't contain `*`. The field mask sets the Places/Routes billing SKU, so wildcards would let a client pull the most expensive tier.
- **Feedback**: `FEEDBACK_DAILY_MAX` (default 30) votes per install per UTC day, and one vote per install per point per 30 days.

Counters live in Workers KV, which is eventually consistent, so a burst of parallel requests can go slightly over a limit. That's fine at this scale. For hard limits, move the counters to a Durable Object or use the Workers Rate Limiting binding for the per-IP cap.

## Community points

Points are stored as one JSON array per grid cell in the same KV namespace. The key is the coordinates rounded to `POINTS_CELL_DECIMALS` (default 3, giving cells of about 110 × 94 m in Amman), for example `pts:31.954,35.911`.

- `POST /points/feedback`: a 👍 on a spot within 15 m of an existing point votes on it. If there's no point there, a 👍 creates one. A 👎 on an unknown spot is ignored. Each cell keeps at most 30 points.
- `GET /points/nearby` reads at most 9 cells. With 3-decimal cells, that only guarantees coverage up to about 94 m, so larger radii get **clamped**. The effective radius is returned as `radius` in `{"radius": 94, "points": [{id, lat, lng, use, name, up, down, distanceM}]}`.

  If you want walking-reach queries (up to ~800 m) in a single request, set `POINTS_CELL_DECIMALS = "2"` (about 1 km cells) **before** collecting data. Changing it later orphans the existing cells.

A point that sits right on a cell edge is matched only against its own cell, so a near-duplicate can occasionally show up across the edge.

## Develop and test

```bash
cd server
npm install
npx tsc --noEmit   # type-check
npm test           # vitest: pure helpers + the request handler with a fake KV and a mocked Google
npx wrangler dev   # local server on http://localhost:8787 (put GOOGLE_MAPS_KEY in .dev.vars)
```

## Deploy

1. Log in: `npx wrangler login`
2. Create the KV namespace and paste the printed `id` into `wrangler.toml` (`[[kv_namespaces]]`):
   ```bash
   npx wrangler kv namespace create QUOTA
   ```
3. Store the Google key as a secret. It never goes in the repo or the APK:
   ```bash
   npx wrangler secret put GOOGLE_MAPS_KEY
   ```
4. (Optional) `npx wrangler secret put APP_SHARED_SECRET`. Requests must then send the same value in `X-Khatwa-Secret`. This is **obfuscation only**: anything shipped in an APK can be extracted. The app doesn't send it yet, so don't set it until the app does. The real fix is [Play Integrity](https://developer.android.com/google/play/integrity): the app gets an integrity token and the Worker verifies it before serving.
5. Deploy:
   ```bash
   npx wrangler deploy
   ```
   Note the URL it prints, for example `https://khatwa-proxy.<account>.workers.dev`.
6. Point the app at it. In the app's `local.properties`:
   ```properties
   KHATWA_PROXY_URL=https://khatwa-proxy.<account>.workers.dev
   ```

### Restrict the Google keys

Use **two** keys:

- **Server key** (the `GOOGLE_MAPS_KEY` secret): in Google Cloud Console → APIs & Services → Credentials, set *API restrictions* to Routes API, Roads API, Places API (New), Geocoding API and Elevation API only. Workers don't have fixed egress IPs, so an IP restriction isn't practical. Rely on the API restrictions, keep the key secret, and set budget alerts and per-API daily quotas in the Cloud Console as a backstop.
- **Android key** (`MAPS_API_KEY` in `local.properties`, used only by the Maps SDK for map tiles): set *Application restrictions* to Android apps with package `com.haithamassoli.khatwa` and your signing SHA-1 fingerprints (debug, upload and Play App Signing), and *API restrictions* to Maps SDK for Android only. This key ships in the APK and is safe to ship because of those restrictions.
