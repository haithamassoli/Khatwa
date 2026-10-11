import { describe, expect, it } from "vitest";
import {
  MAX_NEARBY_CELLS, MAX_POINTS_PER_CELL, applyFeedback, cellKey, cellsAround, findMatch, nearby, parseFeedback, type StoredPoint,
} from "../src/points";

const point = (over: Partial<StoredPoint> = {}): StoredPoint => ({
  id: "p1", lat: 31.9539, lng: 35.9106, use: "PICKUP", name: "", up: 1, down: 0, createdAt: 0, updatedAt: 0, ...over,
});

describe("cells", () => {
  it("rounds to 3 decimals", () => {
    expect(cellKey(31.95389, 35.91061, 3)).toBe("pts:31.954,35.911");
    expect(cellKey(-0.0004, 0.0004, 3)).toBe("pts:0.000,0.000");
    expect(cellKey(-31.9539, -35.9106, 3)).toBe("pts:-31.954,-35.911");
  });
  it("reads at most 9 cells and clamps the radius", () => {
    const small = cellsAround(31.9539, 35.9106, 50, 3);
    expect(small.radiusM).toBe(50);
    expect(small.keys).toContain("pts:31.954,35.911");
    const big = cellsAround(31.9539, 35.9106, 800, 3);
    expect(big.keys.length).toBeLessThanOrEqual(MAX_NEARBY_CELLS);
    expect(big.radiusM).toBeLessThan(100);
    expect(big.radiusM).toBeGreaterThan(80);
    // With ~1 km cells an 800 m walk radius fits in 9 reads.
    const coarse = cellsAround(31.9539, 35.9106, 800, 2);
    expect(coarse.radiusM).toBe(800);
    expect(coarse.keys.length).toBeLessThanOrEqual(MAX_NEARBY_CELLS);
  });
});

describe("parseFeedback", () => {
  it("validates and cleans", () => {
    expect(parseFeedback({ lat: 31.9, lng: 35.9, good: true, name: "  عند <b>المول</b>\n" }))
      .toEqual({ lat: 31.9, lng: 35.9, use: "BOTH", good: true, name: "عند bالمول/b" });
    expect(parseFeedback({ lat: 95, lng: 35.9, good: true })).toBeTypeOf("string");
    expect(parseFeedback({ lat: 31.9, lng: 35.9, good: "yes" })).toBeTypeOf("string");
    expect(parseFeedback({ lat: 31.9, lng: 35.9, good: true, use: "TAXI" })).toBeTypeOf("string");
  });
});

describe("applyFeedback", () => {
  const fb = { lat: 31.95391, lng: 35.91061, use: "DROPOFF" as const, good: false, name: "باب الجامعة" };
  it("votes on a point within 15 m", () => {
    const pts = [point()];
    const m = findMatch(pts, fb.lat, fb.lng);
    expect(m).toBe(0);
    const r = applyFeedback(pts, fb, m, 5, () => "new");
    expect(r.pointId).toBe("p1");
    expect(r.points[0]).toMatchObject({ up: 1, down: 1, use: "PICKUP", name: "باب الجامعة", updatedAt: 5 });
    expect(applyFeedback(pts, { ...fb, good: true }, m, 5, () => "new").points[0]).toMatchObject({ up: 2, use: "BOTH" });
  });
  it("creates on 👍 and ignores 👎 for unknown spots", () => {
    expect(findMatch([point()], 31.955, 35.9106)).toBe(-1);
    expect(applyFeedback([], { ...fb, good: false }, -1, 1, () => "new")).toEqual({ points: [], pointId: null });
    const r = applyFeedback([], { ...fb, good: true }, -1, 1, () => "new");
    expect(r.pointId).toBe("new");
    expect(r.points[0]).toMatchObject({ id: "new", up: 1, down: 0, use: "DROPOFF" });
  });
  it("keeps cells bounded", () => {
    const full = Array.from({ length: MAX_POINTS_PER_CELL }, (_, i) => point({ id: `p${i}`, lat: 31.9 + i * 0.001 }));
    const r = applyFeedback(full, { ...fb, lat: 10, lng: 10, good: true }, -1, 1, () => "new");
    expect(r.points.length).toBe(MAX_POINTS_PER_CELL);
  });
});

describe("nearby", () => {
  it("filters by radius and sorts nearest first", () => {
    const a = point({ id: "a", lat: 31.9545 });  // ~67 m north
    const b = point({ id: "b", lat: 31.9540 });  // ~11 m north
    const far = point({ id: "far", lat: 31.96 });
    expect(nearby([[a, far], [b]], 31.9539, 35.9106, 90).map((p) => p.id)).toEqual(["b", "a"]);
  });
});
