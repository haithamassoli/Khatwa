import {
  ROUTES, checkMatrix, checkRoute, dayKey, errorResponse, intVar, isInstallId, monthKey, readBody, upstreamGetUrl, validFieldMask,
} from "./lib";
import { applyFeedback, cellKey, cellsAround, findMatch, nearby, parseCell, parseFeedback } from "./points";

export interface Env {
  QUOTA: KVNamespace;
  GOOGLE_MAPS_KEY: string;
  /** Optional. When set, requests must send the same value in X-Khatwa-Secret (obfuscation only). */
  APP_SHARED_SECRET?: string;
  MONTHLY_SEARCHES?: string;
  IP_DAILY_REQUESTS?: string;
  FEEDBACK_DAILY_MAX?: string;
  POINTS_CELL_DECIMALS?: string;
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

const DAY_S = 86_400;
const MSG_QUOTA = (max: number) => `استُنفدت حصة البحث الشهرية (${max} بحث). تتجدد مع بداية الشهر القادم`;
const MSG_IP = "طلبات كثيرة من هذه الشبكة اليوم. حاول لاحقاً";
const MSG_FEEDBACK = "تجاوزت حد التقييمات اليومي. حاول غداً";

export default {
  fetch(req: Request, env: Env): Promise<Response> {
    return handle(req, env, (input, init) => fetch(input, init));
  },
};

export async function handle(req: Request, env: Env, upstream: Fetch, now: () => Date = () => new Date()): Promise<Response> {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  if (path === "/health") return Response.json({ ok: true });

  if (env.APP_SHARED_SECRET && req.headers.get("X-Khatwa-Secret") !== env.APP_SHARED_SECRET) {
    return errorResponse(401, "Unauthorized");
  }
  const install = req.headers.get("X-Khatwa-Install");
  if (!isInstallId(install)) return errorResponse(401, "Missing or invalid X-Khatwa-Install");

  const at = now();
  const ip = req.headers.get("CF-Connecting-IP") ?? "unknown";
  if (!(await bump(env.QUOTA, `ip:${ip}:${dayKey(at)}`, intVar(env.IP_DAILY_REQUESTS, 3000), 2 * DAY_S))) {
    return errorResponse(429, MSG_IP);
  }

  const maxSearches = intVar(env.MONTHLY_SEARCHES, 100);
  const quotaKey = `q:${install}:${monthKey(at)}`;

  if (path === "/quota") {
    if (req.method !== "GET") return errorResponse(405, "Method not allowed");
    return Response.json({ month: monthKey(at), searches: await count(env.QUOTA, quotaKey), max: maxSearches });
  }
  if (path === "/points/nearby") {
    if (req.method !== "GET") return errorResponse(405, "Method not allowed");
    return pointsNearby(url, env);
  }
  if (path === "/points/feedback") {
    if (req.method !== "POST") return errorResponse(405, "Method not allowed");
    return pointsFeedback(req, env, install, at);
  }

  const route = ROUTES[path];
  if (!route) return errorResponse(404, "Not found");
  if (req.method !== route.method) return errorResponse(405, "Method not allowed");
  if (!env.GOOGLE_MAPS_KEY) return errorResponse(500, "Server is missing GOOGLE_MAPS_KEY");

  if (route.method === "GET") {
    return relay(upstream, upstreamGetUrl(route.url, url.searchParams, env.GOOGLE_MAPS_KEY), { method: "GET" });
  }

  const text = await readBody(req);
  if (text == null) return errorResponse(413, "Request body too large");
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }
  const fieldMask = req.headers.get("X-Goog-FieldMask");
  if (!validFieldMask(fieldMask)) return errorResponse(400, "X-Goog-FieldMask is required and must not use wildcards");

  // One "search" = one successful DRIVE matrix (the traffic-unaware retry after a failed call is not double counted).
  let countsAsSearch = false;
  if (path === "/routes/matrix") {
    const m = checkMatrix(body);
    if (!m.ok) return errorResponse(400, m.message);
    if (m.travelMode === "DRIVE") {
      if ((await count(env.QUOTA, quotaKey)) >= maxSearches) return errorResponse(429, MSG_QUOTA(maxSearches));
      countsAsSearch = true;
    }
  } else if (path === "/routes/route") {
    const err = checkRoute(body);
    if (err) return errorResponse(400, err);
  }

  const res = await relay(upstream, route.url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Goog-Api-Key": env.GOOGLE_MAPS_KEY, "X-Goog-FieldMask": fieldMask },
    body: text,
  });
  if (countsAsSearch && res.ok) await bump(env.QUOTA, quotaKey, Number.MAX_SAFE_INTEGER, 40 * DAY_S);
  return res;
}

/** Forwards to Google and passes status + body through (Google's own {"error":{...}} bodies included). */
async function relay(upstream: Fetch, url: string, init: RequestInit): Promise<Response> {
  let res: Response;
  try {
    res = await upstream(url, init);
  } catch {
    return errorResponse(502, "Upstream request failed");
  }
  return new Response(res.body, {
    status: res.status,
    headers: { "Content-Type": res.headers.get("Content-Type") ?? "application/json", "Cache-Control": "no-store" },
  });
}

async function count(kv: KVNamespace, key: string): Promise<number> {
  return Number.parseInt((await kv.get(key)) ?? "0", 10) || 0;
}

/**
 * Increments a counter unless it already reached [max]. Returns false when the limit is hit.
 * KV is eventually consistent, so concurrent requests can overshoot slightly; see README.
 */
async function bump(kv: KVNamespace, key: string, max: number, ttlS: number): Promise<boolean> {
  const n = await count(kv, key);
  if (n >= max) return false;
  await kv.put(key, String(n + 1), { expirationTtl: ttlS });
  return true;
}

async function pointsNearby(url: URL, env: Env): Promise<Response> {
  const lat = Number(url.searchParams.get("lat"));
  const lng = Number(url.searchParams.get("lng"));
  const radius = Math.min(Number(url.searchParams.get("radius") ?? "300") || 300, 1000);
  if (!url.searchParams.has("lat") || !url.searchParams.has("lng") || !Number.isFinite(lat) || !Number.isFinite(lng)
    || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return errorResponse(400, "lat and lng are required");
  }
  const { keys, radiusM } = cellsAround(lat, lng, radius, cellDecimals(env));
  const cells = await Promise.all(keys.map(async (k) => parseCell(await env.QUOTA.get(k))));
  return Response.json({ radius: radiusM, points: nearby(cells, lat, lng, radiusM) });
}

async function pointsFeedback(req: Request, env: Env, install: string, at: Date): Promise<Response> {
  const text = await readBody(req, 4096);
  if (text == null) return errorResponse(413, "Request body too large");
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }
  const fb = parseFeedback(parsed);
  if (typeof fb === "string") return errorResponse(400, fb);
  if (!(await bump(env.QUOTA, `fb:${install}:${dayKey(at)}`, intVar(env.FEEDBACK_DAILY_MAX, 30), 2 * DAY_S))) {
    return errorResponse(429, MSG_FEEDBACK);
  }

  const key = cellKey(fb.lat, fb.lng, cellDecimals(env));
  const points = parseCell(await env.QUOTA.get(key));
  const match = findMatch(points, fb.lat, fb.lng);
  if (match >= 0 && (await env.QUOTA.get(votedKey(install, points[match].id))) != null) {
    return Response.json({ ok: true, pointId: points[match].id, duplicate: true });
  }
  const next = applyFeedback(points, fb, match, at.getTime(), () => crypto.randomUUID());
  if (next.pointId == null) return Response.json({ ok: true, pointId: null });
  await env.QUOTA.put(key, JSON.stringify(next.points));
  await env.QUOTA.put(votedKey(install, next.pointId), "1", { expirationTtl: 30 * DAY_S });
  return Response.json({ ok: true, pointId: next.pointId });
}

const votedKey = (install: string, pointId: string) => `voted:${install}:${pointId}`;
const cellDecimals = (env: Env) => Math.min(intVar(env.POINTS_CELL_DECIMALS, 3), 5);
