// Community pickup/dropoff points: pure logic. Storage is one KV value (JSON array) per grid cell.

export type PointUse = "PICKUP" | "DROPOFF" | "BOTH";

export type StoredPoint = {
  id: string;
  lat: number;
  lng: number;
  use: PointUse;
  name: string;
  up: number;
  down: number;
  createdAt: number;
  updatedAt: number;
};

export type Feedback = { lat: number; lng: number; use: PointUse; good: boolean; name: string };

const M_PER_DEG = 111_320;
const EARTH_M = 6_371_000;
/** Feedback within this distance of an existing point counts as the same spot. */
export const MERGE_M = 15;
export const MAX_POINTS_PER_CELL = 30;
export const MAX_NEARBY_RESULTS = 50;
export const MAX_NEARBY_CELLS = 9;

export function distanceM(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad;
  const dLng = (bLng - aLng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** KV key of the grid cell containing (lat, lng): coordinates rounded to [decimals] (3 = ~110 x 94 m in Amman). */
export function cellKey(lat: number, lng: number, decimals: number): string {
  return keyOf(Math.round(lat * 10 ** decimals), Math.round(lng * 10 ** decimals), decimals);
}

const keyOf = (i: number, j: number, decimals: number) =>
  `pts:${(i / 10 ** decimals).toFixed(decimals)},${(j / 10 ** decimals).toFixed(decimals)}`;

/**
 * Cells to read for a radius query, never more than [MAX_NEARBY_CELLS]. If the radius needs more cells it is
 * clamped to one cell width (the guaranteed coverage of the 3x3 block); the effective radius is returned.
 */
export function cellsAround(lat: number, lng: number, radiusM: number, decimals: number): { keys: string[]; radiusM: number } {
  const f = 10 ** decimals;
  const cos = Math.max(Math.cos((lat * Math.PI) / 180), 0.01);
  const cover = (r: number) => {
    const dLat = r / M_PER_DEG;
    const dLng = r / (M_PER_DEG * cos);
    const keys: string[] = [];
    for (let i = Math.round((lat - dLat) * f); i <= Math.round((lat + dLat) * f); i++)
      for (let j = Math.round((lng - dLng) * f); j <= Math.round((lng + dLng) * f); j++) keys.push(keyOf(i, j, decimals));
    return keys;
  };
  const r = Math.max(0, radiusM);
  const keys = cover(r);
  if (keys.length <= MAX_NEARBY_CELLS) return { keys, radiusM: r };
  const clamped = Math.floor((M_PER_DEG / f) * cos);
  return { keys: cover(clamped), radiusM: clamped };
}

/** Validates POST /points/feedback. Returns an error message or the parsed feedback. */
export function parseFeedback(body: unknown): Feedback | string {
  if (typeof body !== "object" || body == null) return "Invalid JSON body";
  const b = body as Record<string, unknown>;
  const { lat, lng, good } = b;
  if (typeof lat !== "number" || !Number.isFinite(lat) || lat < -90 || lat > 90) return "Invalid lat";
  if (typeof lng !== "number" || !Number.isFinite(lng) || lng < -180 || lng > 180) return "Invalid lng";
  if (typeof good !== "boolean") return "good must be a boolean";
  const use = b.use ?? "BOTH";
  if (use !== "PICKUP" && use !== "DROPOFF" && use !== "BOTH") return "Invalid use";
  const name = typeof b.name === "string" ? cleanName(b.name) : "";
  return { lat, lng, use, good, name };
}

export const cleanName = (s: string): string => s.replace(/[\u0000-\u001f\u007f<>]/g, "").replace(/\s+/g, " ").trim().slice(0, 60);

/** Index of the stored point this feedback refers to (nearest within [MERGE_M]), or -1. */
export function findMatch(points: StoredPoint[], lat: number, lng: number): number {
  let best = -1;
  let bestD = MERGE_M;
  points.forEach((p, i) => {
    const d = distanceM(p.lat, p.lng, lat, lng);
    if (d <= bestD) { best = i; bestD = d; }
  });
  return best;
}

/**
 * Applies one vote. A 👍 on an unknown spot creates it; a 👎 on an unknown spot is ignored (nothing to downvote).
 * Returns the new cell contents and the affected point id (null if ignored).
 */
export function applyFeedback(
  points: StoredPoint[], fb: Feedback, match: number, now: number, newId: () => string,
): { points: StoredPoint[]; pointId: string | null } {
  if (match >= 0) {
    const p = points[match];
    const next: StoredPoint = {
      ...p,
      up: p.up + (fb.good ? 1 : 0),
      down: p.down + (fb.good ? 0 : 1),
      use: fb.good && p.use !== fb.use ? "BOTH" : p.use, // a 👍 for the other use widens it; a 👎 never does
      name: p.name || fb.name,
      updatedAt: now,
    };
    return { points: points.map((q, i) => (i === match ? next : q)), pointId: p.id };
  }
  if (!fb.good) return { points, pointId: null };
  const created: StoredPoint = {
    id: newId(), lat: round6(fb.lat), lng: round6(fb.lng), use: fb.use, name: fb.name, up: 1, down: 0, createdAt: now, updatedAt: now,
  };
  return { points: trimCell([...points, created]), pointId: created.id };
}

/** Keeps the best [MAX_POINTS_PER_CELL] points: highest net score, then most recently updated. */
export function trimCell(points: StoredPoint[]): StoredPoint[] {
  if (points.length <= MAX_POINTS_PER_CELL) return points;
  return [...points]
    .sort((a, b) => b.up - b.down - (a.up - a.down) || b.updatedAt - a.updatedAt)
    .slice(0, MAX_POINTS_PER_CELL);
}

/** Points from several cells within [radiusM] of the center, nearest first. */
export function nearby(cells: StoredPoint[][], lat: number, lng: number, radiusM: number) {
  return cells.flat()
    .map((p) => ({ p, d: distanceM(lat, lng, p.lat, p.lng) }))
    .filter((x) => x.d <= radiusM)
    .sort((a, b) => a.d - b.d)
    .slice(0, MAX_NEARBY_RESULTS)
    .map(({ p, d }) => ({ id: p.id, lat: p.lat, lng: p.lng, use: p.use, name: p.name, up: p.up, down: p.down, distanceM: Math.round(d) }));
}

export function parseCell(text: string | null): StoredPoint[] {
  if (!text) return [];
  try {
    const v = JSON.parse(text);
    return Array.isArray(v) ? (v as StoredPoint[]) : [];
  } catch {
    return [];
  }
}

const round6 = (v: number) => Math.round(v * 1e6) / 1e6;
