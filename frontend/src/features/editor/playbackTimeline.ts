import type { SimulationResponse } from '../../api/archlabClient';

export const SPEEDS = [0.5, 1, 2, 4] as const;
type Activity = SimulationResponse['timeline'][number];
export function activityAt(activities: Activity[], time: number): number {
  if (!activities.length) return -1;
  const index = activities.findIndex((a) => a.startMs <= time && time < a.endMs);
  return index >= 0 ? index : activities.reduce((found, a, i) => a.startMs <= time ? i : found, -1);
}
export function nextBoundary(activities: Activity[], time: number): number {
  return activities.map((a) => a.endMs).find((t) => t > time) ?? time;
}
export function previousBoundary(activities: Activity[], time: number): number {
  return activities.map((a) => a.startMs).filter((t) => t < time).reduce((last, t) => t, 0);
}
