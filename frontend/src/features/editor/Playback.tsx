import { useEffect, useRef, useState } from 'react';
import type { SimulationResponse } from '../../api/archlabClient';

import { nextTracePoint, previousTracePoint } from './traceState';
import { SPEEDS, activityAt } from './playbackTimeline';

export default function Playback({ result, onTimeChange }: { result: SimulationResponse; onTimeChange: (time: number, sequence: number) => void }) {
  const [time, setTime] = useState(0);
  const [sequence, setSequence] = useState(Number.POSITIVE_INFINITY);
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
      setSequence(Number.POSITIVE_INFINITY);
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
  useEffect(() => { onTimeChange(time, sequence); }, [time, sequence, onTimeChange]);
  const current = activityAt(timeline, time);
  return <section className="playback" aria-label="Reprodução da simulação">
    <strong>Reprodução</strong>
    <output aria-label="Relógio virtual">{Math.round(time)} / {total} ms</output>
    <button type="button" onClick={() => { setStepMode(false); setPlaying((v) => !v); }} disabled={time >= total && !playing}>{playing ? 'Pausar' : 'Continuar'}</button>
    <label>Velocidade <select aria-label="Velocidade" value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>{SPEEDS.map((s) => <option key={s} value={s}>{s}×</option>)}</select></label>
    <button type="button" onClick={() => { setPlaying(false); setStepMode(true); }}>Passo a passo</button>
    <button type="button" disabled={!stepMode || (time === 0 && sequence === 0)} onClick={() => { const prev = previousTracePoint(result.trace, time, sequence); if (prev) { setTime(prev.timestampMs); setSequence(prev.sequence); } else { setTime(0); setSequence(0); } }}>Anterior</button>
    <button type="button" disabled={!stepMode || (time >= total && sequence === Number.POSITIVE_INFINITY)} onClick={() => { const next = nextTracePoint(result.trace, time, sequence); if (next) { setTime(next.timestampMs); setSequence(next.sequence); } else { setTime(total); setSequence(Number.POSITIVE_INFINITY); } }}>Próximo</button>
    <p aria-live="polite">Atividade: {current < 0 ? 'Pronta' : `${current + 1} de ${timeline.length}`}</p>
    <progress value={time} max={total || 1} aria-label="Progresso virtual" />
  </section>;
}
