import { useEffect, useRef, useState } from 'react';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';

type Status = 'REPRODUZINDO' | 'PAUSADA' | 'CONCLUÍDA';
type Props = { result: SimulationResponseV2; onStatus: (status: Status) => void };
export function SimulationPlaybackV2({ result, onStatus }: Props) {
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [step, setStep] = useState(false);
  const frameTime = useRef<number | null>(null);
  const total = result.execution.totalDurationMs;
  const points = [...new Set([0, total, ...result.trace.map(e => e.timestampMs)])].sort((a,b) => a-b);
  useEffect(() => {
    if (!playing || step) { frameTime.current = null; return; }
    let frame = 0;
    const tick = (now: number) => {
      if (frameTime.current === null) frameTime.current = now;
      const delta = Math.max(0, Math.min(now-frameTime.current, 100));
      frameTime.current = now;
      setTime(current => {
        const next = Math.min(total, current + delta * speed);
        if (next >= total) setPlaying(false);
        return next;
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(frame); frameTime.current = null; };
  }, [playing, step, speed, total]);
  useEffect(() => { onStatus(time >= total ? 'CONCLUÍDA' : playing ? 'REPRODUZINDO' : 'PAUSADA'); }, [time, total, playing, onStatus]);
  const events = result.trace.filter(e => e.timestampMs <= time);
  const activities = result.timeline.filter(e => e.startMs <= time);
  return <section className="playback" aria-label="Reprodução v2">
    <strong>Reprodução v2</strong>
    <output aria-label="Relógio virtual v2">{Math.round(time)} / {total} ms</output>
    <button type="button" disabled={time >= total && !playing} onClick={() => { setStep(false); setPlaying(v => !v); }}>{playing ? 'Pausar' : 'Continuar'}</button>
    <label>Velocidade <select aria-label="Velocidade v2" value={speed} onChange={e => setSpeed(Number(e.target.value))}>{[0.25,0.5,1,2,4].map(n => <option key={n} value={n}>{n}×</option>)}</select></label>
    <button type="button" onClick={() => { setStep(true); setPlaying(false); }}>Passo a passo</button>
    <button type="button" disabled={!step || time <= 0} onClick={() => setTime([...points].reverse().find(n => n < time) ?? 0)}>Anterior</button>
    <button type="button" disabled={!step || time >= total} onClick={() => setTime(points.find(n => n > time) ?? total)}>Próximo</button>
    <progress aria-label="Progresso v2" value={time} max={total || 1}/>
    <p>Eventos até o instante: {events.length} / {result.trace.length}. Atividades iniciadas: {activities.length} / {result.timeline.length}.</p>
    <details><summary>Trace recebido do backend</summary><ol>{events.map(e => <li key={e.sequence}>{e.timestampMs}ms · {e.type} · #{e.sequence}</li>)}</ol></details>
    <details><summary>Timeline recebida do backend</summary><ol>{result.timeline.map(e => <li key={e.id}>{e.kind} · {e.startMs}–{e.endMs}ms</li>)}</ol></details>
    <details><summary>Métricas e mensagens do snapshot</summary>
      <p>Tempo total: {result.metrics.totalDurationMs} ms.</p>
      <p>Processamentos: {result.metrics.processingByComponent.length}; comunicações: {result.metrics.communicationByConnection.length}; esperas: {result.metrics.waitingByComponent.length}.</p>
      <p>Mensagens ilustrativas: {result.messageRepresentations.length}</p>
    </details>
  </section>;
}
