import { beforeEach, describe, expect, it } from "vitest";
import { handle, type Env } from "../src/index";

class FakeKV {
  data = new Map<string, string>();
  async get(key: string) { return this.data.get(key) ?? null; }
  async put(key: string, value: string) { this.data.set(key, value); }
}

const INSTALL = "123e4567-e89b-42d3-a456-426614174000";
const NOW = () => new Date("2026-10-11T09:00:00Z");
const wp = { waypoint: { location: { latLng: { latitude: 31.95, longitude: 35.91 } } } };

let kv: FakeKV;
let env: Env;
let calls: { url: string; init?: RequestInit }[];
let upstreamStatus: number;

const upstream = async (url: string, init?: RequestInit) => {
  calls.push({ url, init });
  return new Response(upstreamStatus === 200 ? "[]" : '{"error":{"message":"boom"}}', {
    status: upstreamStatus, headers: { "Content-Type": "application/json" },
  });
};

function req(path: string, init: RequestInit & { headers?: Record<string, string> } = {}) {
  return new Request(`https://proxy.example${path}`, {
    ...init,
    headers: { "X-Khatwa-Install": INSTALL, "CF-Connecting-IP": "1.2.3.4", ...init.headers },
  });
}

const matrix = (mode: string, o = 1, d = 1) => req("/routes/matrix", {
  method: "POST",
  headers: { "X-Goog-FieldMask": "originIndex,destinationIndex,duration", "X-Goog-Api-Key": "client-key" },
  body: JSON.stringify({ travelMode: mode, origins: Array(o).fill(wp), destinations: Array(d).fill(wp) }),
});

const run = (r: Request) => handle(r, env, upstream, NOW);

beforeEach(() => {
  kv = new FakeKV();
  env = { QUOTA: kv as unknown as KVNamespace, GOOGLE_MAPS_KEY: "server-key", MONTHLY_SEARCHES: "2" };
  calls = [];
  upstreamStatus = 200;
});

describe("auth", () => {
  it("requires a UUID install id", async () => {
    const r = await run(new Request("https://proxy.example/quota"));
    expect(r.status).toBe(401);
    expect(((await r.json()) as any).error.message).toBeTruthy();
  });
  it("checks the optional shared secret", async () => {
    env.APP_SHARED_SECRET = "s3";
    expect((await run(req("/quota"))).status).toBe(401);
    expect((await run(req("/quota", { headers: { "X-Khatwa-Secret": "s3" } }))).status).toBe(200);
  });
  it("404s anything outside the allowlist", async () => {
    expect((await run(req("/maps/api/directions/json"))).status).toBe(404);
    expect((await run(req("/routes/matrix"))).status).toBe(405);
  });
});

describe("forwarding", () => {
  it("injects the key header for Routes and drops the client's", async () => {
    expect((await run(matrix("WALK"))).status).toBe(200);
    expect(calls[0].url).toBe("https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix");
    const h = calls[0].init!.headers as Record<string, string>;
    expect(h["X-Goog-Api-Key"]).toBe("server-key");
    expect(h["X-Goog-FieldMask"]).toBe("originIndex,destinationIndex,duration");
  });
  it("puts the key in the query for elevation/geocode/roads", async () => {
    await run(req("/elevation/json?locations=31.9,35.9&key=client"));
    const u = new URL(calls[0].url);
    expect(u.origin + u.pathname).toBe("https://maps.googleapis.com/maps/api/elevation/json");
    expect(u.searchParams.getAll("key")).toEqual(["server-key"]);
  });
  it("forwards places:searchNearby", async () => {
    await run(req("/places/searchNearby", { method: "POST", headers: { "X-Goog-FieldMask": "places.location" }, body: "{}" }));
    expect(calls[0].url).toBe("https://places.googleapis.com/v1/places:searchNearby");
  });
  it("rejects wildcard field masks and oversized matrices", async () => {
    const r = await run(req("/places/searchText", { method: "POST", headers: { "X-Goog-FieldMask": "*" }, body: "{}" }));
    expect(r.status).toBe(400);
    expect((await run(matrix("DRIVE", 5, 6))).status).toBe(400);
    expect((await run(matrix("WALK", 1, 11))).status).toBe(400);
    expect(calls.length).toBe(0);
  });
  it("passes Google errors through", async () => {
    upstreamStatus = 403;
    const r = await run(matrix("WALK"));
    expect(r.status).toBe(403);
    expect(((await r.json()) as any).error.message).toBe("boom");
  });
});

describe("quota", () => {
  it("counts successful DRIVE matrices only and returns 429 when used up", async () => {
    await run(matrix("WALK"));
    upstreamStatus = 500;
    await run(matrix("DRIVE")); // failed upstream: not counted
    upstreamStatus = 200;
    await run(matrix("DRIVE"));
    await run(matrix("DRIVE"));
    const q = (await (await run(req("/quota"))).json()) as any;
    expect(q).toEqual({ month: "2026-10", searches: 2, max: 2 });
    const r = await run(matrix("DRIVE"));
    expect(r.status).toBe(429);
    expect(((await r.json()) as any).error.message).toContain("حصة البحث");
    expect((await run(matrix("WALK"))).status).toBe(200);
  });
  it("caps requests per IP per day", async () => {
    env.IP_DAILY_REQUESTS = "2";
    expect((await run(req("/quota"))).status).toBe(200);
    expect((await run(req("/quota"))).status).toBe(200);
    expect((await run(req("/quota"))).status).toBe(429);
  });
});

describe("community points", () => {
  const feedback = (body: object) => req("/points/feedback", { method: "POST", body: JSON.stringify(body) });

  it("creates, votes once per install, and lists nearby", async () => {
    const first = (await (await run(feedback({ lat: 31.9539, lng: 35.9106, use: "PICKUP", good: true, name: "باب المول" }))).json()) as any;
    expect(first.pointId).toBeTruthy();
    const dup = (await (await run(feedback({ lat: 31.95391, lng: 35.9106, good: true }))).json()) as any;
    expect(dup.duplicate).toBe(true);

    const other = "223e4567-e89b-42d3-a456-426614174000";
    await run(req("/points/feedback", { method: "POST", headers: { "X-Khatwa-Install": other },
      body: JSON.stringify({ lat: 31.9539, lng: 35.9106, good: false }) }));

    const res = (await (await run(req("/points/nearby?lat=31.9540&lng=35.9107&radius=50"))).json()) as any;
    expect(res.radius).toBe(50);
    expect(res.points).toHaveLength(1);
    expect(res.points[0]).toMatchObject({ id: first.pointId, use: "PICKUP", name: "باب المول", up: 1, down: 1 });
  });
  it("rate-limits feedback per install", async () => {
    env.FEEDBACK_DAILY_MAX = "1";
    expect((await run(feedback({ lat: 31.9, lng: 35.9, good: true }))).status).toBe(200);
    expect((await run(feedback({ lat: 31.8, lng: 35.9, good: true }))).status).toBe(429);
  });
  it("validates input", async () => {
    expect((await run(feedback({ lat: "x", lng: 35.9, good: true }))).status).toBe(400);
    expect((await run(req("/points/nearby?lat=31.9"))).status).toBe(400);
  });
});
