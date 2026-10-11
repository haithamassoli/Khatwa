// Pure helpers (no Workers globals) so they can be unit-tested in Node.

export const MAX_BODY_BYTES = 32 * 1024;
export const MAX_DRIVE_ELEMENTS = 25;
export const MAX_WALK_ELEMENTS = 10;

export type Upstream = {
  /** Google endpoint. */
  url: string;
  method: "GET" | "POST";
  /** How the key is attached: header for Routes/Places, query param for the legacy Maps web services. */
  auth: "header" | "query";
};

/** Strict allowlist: proxy path -> Google endpoint. Anything else is a 404. */
export const ROUTES: Record<string, Upstream> = {
  "/routes/matrix": { url: "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix", method: "POST", auth: "header" },
  "/routes/route": { url: "https://routes.googleapis.com/directions/v2:computeRoutes", method: "POST", auth: "header" },
  "/places/searchText": { url: "https://places.googleapis.com/v1/places:searchText", method: "POST", auth: "header" },
  "/places/searchNearby": { url: "https://places.googleapis.com/v1/places:searchNearby", method: "POST", auth: "header" },
  "/roads/nearestRoads": { url: "https://roads.googleapis.com/v1/nearestRoads", method: "GET", auth: "query" },
  "/geocode/json": { url: "https://maps.googleapis.com/maps/api/geocode/json", method: "GET", auth: "query" },
  "/elevation/json": { url: "https://maps.googleapis.com/maps/api/elevation/json", method: "GET", auth: "query" },
};

/** Google-style error body; the app reads error.message. */
export function errorResponse(status: number, message: string): Response {
  return Response.json({ error: { code: status, message } }, { status });
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const isInstallId = (s: string | null): s is string => s != null && UUID.test(s);

/** Builds the upstream GET URL: copies the client's query, drops any client-supplied key, adds ours. */
export function upstreamGetUrl(base: string, clientSearch: URLSearchParams, key: string): string {
  const q = new URLSearchParams();
  for (const [k, v] of clientSearch) if (k.toLowerCase() !== "key") q.append(k, v);
  q.set("key", key);
  return `${base}?${q}`;
}

/** Field masks decide the Places/Routes billing SKU; refuse wildcards and anything odd. */
export function validFieldMask(mask: string | null): mask is string {
  return mask != null && mask.length > 0 && mask.length <= 512 && /^[A-Za-z0-9_.,]+$/.test(mask);
}

/** "YYYY-MM" in Amman time, matching the app's local monthly quota. */
export function monthKey(now: Date, timeZone = "Asia/Amman"): string {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone, year: "numeric", month: "2-digit" }).formatToParts(now);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  return `${get("year")}-${get("month")}`;
}

/** "YYYY-MM-DD" in UTC, for daily counters. */
export const dayKey = (now: Date): string => now.toISOString().slice(0, 10);

export type MatrixCheck =
  | { ok: true; travelMode: "DRIVE" | "WALK"; elements: number }
  | { ok: false; message: string };

/** Validates a computeRouteMatrix body. Google defaults travelMode to DRIVE when it's missing. */
export function checkMatrix(body: unknown): MatrixCheck {
  if (typeof body !== "object" || body == null) return { ok: false, message: "Invalid JSON body" };
  const b = body as Record<string, unknown>;
  const mode = b.travelMode ?? "DRIVE";
  if (mode !== "DRIVE" && mode !== "WALK") return { ok: false, message: `travelMode ${String(mode)} is not allowed` };
  if (!Array.isArray(b.origins) || !Array.isArray(b.destinations)) return { ok: false, message: "origins and destinations are required" };
  const elements = b.origins.length * b.destinations.length;
  const max = mode === "DRIVE" ? MAX_DRIVE_ELEMENTS : MAX_WALK_ELEMENTS;
  if (elements === 0) return { ok: false, message: "Empty matrix" };
  if (elements > max) return { ok: false, message: `Matrix too large: ${elements} elements (max ${max} for ${mode})` };
  return { ok: true, travelMode: mode, elements };
}

/** Validates a computeRoutes body. */
export function checkRoute(body: unknown): string | null {
  if (typeof body !== "object" || body == null) return "Invalid JSON body";
  const mode = (body as Record<string, unknown>).travelMode ?? "DRIVE";
  return mode === "DRIVE" || mode === "WALK" ? null : `travelMode ${String(mode)} is not allowed`;
}

/** Reads a request body up to [limit] bytes; null if larger. */
export async function readBody(req: Request, limit = MAX_BODY_BYTES): Promise<string | null> {
  const declared = Number(req.headers.get("Content-Length") ?? "0");
  if (declared > limit) return null;
  const buf = await req.arrayBuffer();
  if (buf.byteLength > limit) return null;
  return new TextDecoder().decode(buf);
}

export function intVar(v: string | undefined, fallback: number): number {
  const n = Number.parseInt(v ?? "", 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}
