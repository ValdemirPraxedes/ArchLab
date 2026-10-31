import { useEffect, useRef, useState } from 'react';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { cursorEvents, inFlightMessages, nextCursor, orderedTrace, previousCursor, traceStateAt } from './traceV2';
import type { TraceCursor } from './traceV2';

type Status = 'REPRODUZINDO' | 'PAUSADA' | 'CONCLUÍDA';
type Props = { result: SimulationResponseV2; onStatus: (status: Status) => void; onCursor?: (cursor: TraceCursor) => void };
export function SimulationPlaybackV2({ result, onStatus, onCursor }: Props) {
  const [cursor, setCursor] = useState<TraceCursor>({ time: 0, sequence: 0 });
  const time = cursor.time;
  const [selectedSequence, setSelectedSequence] = useState<number | null>(null);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [step, setStep] = useState(false);
  const frameTime = useRef<number | null>(null);
  const total = result.execution.totalDurationMs;
  const sorted = orderedTrace(result.trace);
  useEffect(() => {
    if (!playing || step) { frameTime.current = null; return; }
    let frame = 0;
    const tick = (now: number) => {
      if (frameTime.current === null) frameTime.current = now;
      const delta = Math.max(0, Math.min(now-frameTime.current, 100));
      frameTime.current = now;
      setCursor(current => {
        const next = Math.min(total, current.time + delta * speed);
        if (next >= total) setPlaying(false);
        return { time: next, sequence: Number.POSITIVE_INFINITY };
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(frame); frameTime.current = null; };
  }, [playing, step, speed, total]);
  useEffect(() => { onStatus(time >= total ? 'CONCLUÍDA' : playing ? 'REPRODUZINDO' : 'PAUSADA'); }, [time, total, playing, onStatus]);
  useEffect(() => { onCursor?.(cursor); }, [cursor, onCursor]);
  const events = cursorEvents(result.trace,cursor);
  const state = traceStateAt(result,cursor);
  const messages = inFlightMessages(result.trace,cursor);
  const activities = result.timeline.filter(e => e.startMs <= time);
  return <section className="playback" aria-label="Reprodução v2">
    <strong>Reprodução v2</strong>
    <output aria-label="Relógio virtual v2">{Math.round(time)} / {total} ms</output>
    <button type="button" disabled={time >= total && !playing} onClick={() => { setStep(false); setPlaying(v => !v); }}>{playing ? 'Pausar' : 'Continuar'}</button>
    <label>Velocidade <select aria-label="Velocidade v2" value={speed} onChange={e => setSpeed(Number(e.target.value))}>{[0.5,1,2,4].map(n => <option key={n} value={n}>{n}×</option>)}</select></label>
    <button type="button" onClick={() => { setStep(true); setPlaying(false); }}>Passo a passo</button>
    <button type="button" disabled={!step || (time === 0 && cursor.sequence === 0)} onClick={() => setCursor(previousCursor(result.trace,cursor))}>Anterior</button>
    <button type="button" disabled={!step || (time >= total && cursor.sequence === Number.POSITIVE_INFINITY)} onClick={() => setCursor(nextCursor(result.trace,cursor,total))}>Próximo</button>
    <progress aria-label="Progresso v2" value={time} max={total || 1}/>
    <p>Eventos até o instante: {events.length} / {result.trace.length}. Atividades iniciadas: {activities.length} / {result.timeline.length}.</p>
    <details open><summary>Trace recebido do backend · {events.length} eventos</summary><ol>{sorted.map(e => <li key={e.sequence}><button type="button" aria-current={selectedSequence === e.sequence ? 'true' : undefined} onClick={() => { setPlaying(false); setStep(true); setSelectedSequence(e.sequence); setCursor({time:e.timestampMs,sequence:e.sequence}); }}>{e.timestampMs}ms · #{e.sequence} · {e.type}{e.type === 'MESSAGE_EVENT' ? ` · ${e.action} ${e.direction}` : ''}</button></li>)}</ol></details>
    <p>{selectedSequence === null ? 'Acompanhamento automático ativo' : 'Acompanhamento suspenso para inspeção'} {selectedSequence !== null && <button type="button" onClick={() => setSelectedSequence(null)}>Acompanhar execução</button>}</p>
    <details open><summary>Estado reconstruído · {messages.length} mensagens em trânsito</summary><p>Componentes: {Object.entries(state.components).map(([id,status]) => `${id}: ${status}`).join(' · ')}</p><p>Requisições: {Object.entries(state.requests).map(([id,status]) => `${id}: ${status}`).join(' · ')}</p><p>Exchanges: {Object.entries(state.exchanges).map(([id,status]) => `${id}: ${status}`).join(' · ')}</p><p>Mensagens em trânsito: {messages.map(m => `${m.sourceId} → ${m.targetId} (${m.direction})`).join(' · ') || 'nenhuma'}</p></details>
    <details><summary>Timeline recebida do backend</summary><ol>{result.timeline.map(e => <li key={e.id}>{e.kind} · {e.startMs}–{e.endMs}ms</li>)}</ol></details>
    <details><summary>Métricas e mensagens do snapshot</summary>
      <p>Tempo total: {result.metrics.totalDurationMs} ms.</p>
      <p>Processamentos: {result.metrics.processingByComponent.length}; comunicações: {result.metrics.communicationByConnection.length}; esperas: {result.metrics.waitingByComponent.length}.</p>
      <p>Mensagens ilustrativas: {result.messageRepresentations.length}</p>
    </details>
  </section>;
}
