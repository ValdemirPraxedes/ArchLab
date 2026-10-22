import { useEffect, useRef, useState } from 'react';
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
export default function Playback({ result, onTimeChange }: { result: SimulationResponse; onTimeChange: (time: number) => void }) {
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [stepMode, setStepMode] = useState(false);
  const [speed, setSpeed] = useState<number>(1);
  const lastFrame = useRef<number | null>(null);
  const total = result.execution.totalDurationMs;
  const timeline = result.timeline;
  useEffect(() => {
    if (!playing || stepMode) { lastFrame.current = null; return; }
    let frame = 0;
    const tick = (now: number) => {
      if (lastFrame.current === null) lastFrame.current = now;
      const delta = Math.max(0, Math.min(now - lastFrame.current, 100));
      lastFrame.current = now;
      setTime((previous) => {
        const next = Math.min(total, previous + delta * speed);
        if (next >= total) setPlaying(false);
        return next;
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(frame); lastFrame.current = null; };
  }, [playing, speed, stepMode, total]);
  useEffect(() => { onTimeChange(time); }, [time, onTimeChange]);
  const current = activityAt(timeline, time);
  return <section className="playback" aria-label="Reprodução da simulação">
    <strong>Reprodução</strong>
    <output aria-label="Relógio virtual">{Math.round(time)} / {total} ms</output>
    <button type="button" onClick={() => { setStepMode(false); setPlaying((v) => !v); }} disabled={time >= total && !playing}>{playing ? 'Pausar' : 'Continuar'}</button>
    <label>Velocidade <select aria-label="Velocidade" value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>{SPEEDS.map((s) => <option key={s} value={s}>{s}×</option>)}</select></label>
    <button type="button" onClick={() => { setPlaying(false); setStepMode(true); }}>Passo a passo</button>
    <button type="button" disabled={!stepMode || time <= 0} onClick={() => setTime((t) => previousBoundary(timeline,t))}>Anterior</button>
    <button type="button" disabled={!stepMode || time >= total} onClick={() => setTime((t) => nextBoundary(timeline,t))}>Próximo</button>
    <p aria-live="polite">Atividade: {current < 0 ? 'Pronta' : `${current + 1} de ${timeline.length}`}</p>
    <progress value={time} max={total || 1} aria-label="Progresso virtual" />
  </section>;
}
