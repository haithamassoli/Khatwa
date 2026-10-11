import { describe, expect, it } from "vitest";
import { checkMatrix, checkRoute, monthKey, readBody, upstreamGetUrl, validFieldMask } from "../src/lib";

const wp = { waypoint: { location: { latLng: { latitude: 31.95, longitude: 35.91 } } } };
const many = (n: number) => Array.from({ length: n }, () => wp);

describe("checkMatrix", () => {
  it("allows a 5x5 DRIVE matrix", () => {
    expect(checkMatrix({ travelMode: "DRIVE", origins: many(5), destinations: many(5) })).toEqual({ ok: true, travelMode: "DRIVE", elements: 25 });
  });
  it("treats a missing travelMode as DRIVE", () => {
    expect(checkMatrix({ origins: many(5), destinations: many(6) }).ok).toBe(false);
  });
  it("caps WALK at 10 elements", () => {
    expect(checkMatrix({ travelMode: "WALK", origins: many(1), destinations: many(8) }).ok).toBe(true);
    expect(checkMatrix({ travelMode: "WALK", origins: many(1), destinations: many(11) }).ok).toBe(false);
  });
  it("rejects other modes and malformed bodies", () => {
    expect(checkMatrix({ travelMode: "TRANSIT", origins: many(1), destinations: many(1) }).ok).toBe(false);
    expect(checkMatrix({ travelMode: "DRIVE" }).ok).toBe(false);
    expect(checkMatrix(null).ok).toBe(false);
  });
});

describe("checkRoute", () => {
  it("allows WALK and DRIVE only", () => {
    expect(checkRoute({ travelMode: "WALK" })).toBeNull();
    expect(checkRoute({})).toBeNull();
    expect(checkRoute({ travelMode: "BICYCLE" })).not.toBeNull();
  });
});

describe("upstreamGetUrl", () => {
  it("drops client keys and adds the server key", () => {
    const u = new URL(upstreamGetUrl("https://maps.googleapis.com/maps/api/elevation/json",
      new URLSearchParams("locations=31.9,35.9|32.0,35.8&key=stolen&KEY=x"), "server-key"));
    expect(u.searchParams.getAll("key")).toEqual(["server-key"]);
    expect(u.searchParams.has("KEY")).toBe(false);
    expect(u.searchParams.get("locations")).toBe("31.9,35.9|32.0,35.8");
  });
});

describe("validFieldMask", () => {
  it("accepts explicit masks and rejects wildcards", () => {
    expect(validFieldMask("originIndex,destinationIndex,duration")).toBe(true);
    expect(validFieldMask("places.displayName,places.location")).toBe(true);
    expect(validFieldMask("*")).toBe(false);
    expect(validFieldMask("places.*")).toBe(false);
    expect(validFieldMask(null)).toBe(false);
  });
});

describe("monthKey", () => {
  it("uses Amman time", () => {
    // 22:30 UTC on Oct 31 is already November in Amman (UTC+3).
    expect(monthKey(new Date("2026-10-31T22:30:00Z"))).toBe("2026-11");
    expect(monthKey(new Date("2026-10-15T10:00:00Z"))).toBe("2026-10");
  });
});

describe("readBody", () => {
  it("rejects bodies over the limit", async () => {
    const big = new Request("https://x/routes/matrix", { method: "POST", body: "a".repeat(40_000) });
    expect(await readBody(big)).toBeNull();
    const ok = new Request("https://x/routes/matrix", { method: "POST", body: "{}" });
    expect(await readBody(ok)).toBe("{}");
  });
});
