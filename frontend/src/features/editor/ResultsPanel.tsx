import { useEffect, useMemo, useRef, useState } from 'react';
import type { SimulationResponse } from '../../api/archlabClient';
import { reconstructState } from './traceState';

type Tab = 'times' | 'events' | 'states';
type Props = {
  result: SimulationResponse;
  playbackTimeMs: number;
  playbackSequence: number;
  selectedId?: string;
};
export default function ResultsPanel({ result, playbackTimeMs, playbackSequence, selectedId }: Props) {
  const [expanded, setExpanded] = useState(true);
  const [height, setHeight] = useState(260);
  const [tab, setTab] = useState<Tab>('times');
  const [inspectedSequence, setInspectedSequence] = useState<number | null>(null);
  const [following, setFollowing] = useState(true);
  const eventListRef = useRef<HTMLOListElement>(null);
  const automaticScrollRef = useRef(false);
  const sortedTrace = useMemo(() => [...result.trace].sort((a,b) => a.timestampMs - b.timestampMs || a.sequence - b.sequence), [result.trace]);
  const currentEvent = [...sortedTrace].reverse().find((entry) =>
    entry.timestampMs < playbackTimeMs || (entry.timestampMs === playbackTimeMs && entry.sequence <= playbackSequence));
  const currentSequence = currentEvent?.sequence ?? null;
  useEffect(() => {
    if (!following || tab !== 'events' || !expanded) return;
    const list = eventListRef.current;
    const target = list?.querySelector<HTMLButtonElement>(`[data-sequence="${currentSequence}"]`);
    if (!target || !list) return;
    automaticScrollRef.current = true;
    target.scrollIntoView?.({ block: 'nearest' });
    automaticScrollRef.current = false;
  }, [following, tab, expanded, currentSequence]);
  const inspect = (sequence: number) => {
    setInspectedSequence(sequence);
    setFollowing(false);
  };
  const metrics = result.metrics;
  const state = reconstructState(result.initialState, result.trace, playbackTimeMs, playbackSequence);
  const inspectedEvent = result.trace.find((event) => event.sequence === inspectedSequence);
  const rows = [
    ['API pré-processamento', metrics.apiPreProcessingMs],
    ['Banco de dados', metrics.databaseProcessingMs],
    ['API pós-processamento', metrics.apiPostProcessingMs],
    ['Comunicação', metrics.communicationMs],
  ] as const;
  return <section className="results-panel" aria-label="Resultados da simulação">
    <header className="results-panel__header">
      <strong>Resultados</strong>
      <button type="button" aria-expanded={expanded} onClick={() => setExpanded((v) => !v)}>{expanded ? 'Recolher painel' : 'Expandir painel'}</button>
    </header>
    {expanded && <div className="results-panel__body" style={{ height }}>
      <label className="results-resize">Altura do painel
        <input aria-label="Altura do painel" type="range" min="160" max="500" step="10" value={height} onChange={(event) => setHeight(Number(event.target.value))} />
      </label>
      <div className="results-tabs" role="tablist" aria-label="Abas de resultados">
        {([['times','Tempos'], ['events','Eventos'], ['states','Estados']] as const).map(([id, label]) => <button key={id} type="button" role="tab" aria-selected={tab === id} onClick={() => setTab(id)}>{label}</button>)}
      </div>
      <div className="results-panel__content" role="tabpanel">
        {tab === 'times' && <>
          <p><strong>Tempo total: {result.execution.totalDurationMs} ms</strong></p>
          <table><thead><tr><th>Atividade</th><th>Tempo</th></tr></thead><tbody>
            {rows.map(([label, value]) => <tr key={label}><td>{label}</td><td>{value} ms</td></tr>)}
          </tbody></table>
          <p>Espera bloqueante da API: <strong>{metrics.apiWaitingMs} ms</strong> (métrica informativa, não adicionada ao total).</p>
          <h3>Timeline — {result.timeline.length} atividades</h3>
          <ol>{result.timeline.map((activity) => <li key={activity.id}>{activity.kind} — {activity.startMs}–{activity.endMs} ms ({activity.durationMs} ms)</li>)}</ol>
        </>}
        {tab === 'events' && <>
          <p>Eventos do trace: {result.trace.length}. Selecionar um evento não altera a reprodução.</p>
          <div className="results-follow"><span>{following ? 'Acompanhamento automático ativo' : 'Acompanhamento suspenso para inspeção'}</span>
            {!following && <button type="button" onClick={() => { setInspectedSequence(null); setFollowing(true); }}>Acompanhar execução</button>}
          </div>
          <ol className="results-events" ref={eventListRef} onScroll={() => { if (!automaticScrollRef.current) setFollowing(false); }}>{sortedTrace.map((entry) =>
            <li key={entry.sequence}><button type="button" data-sequence={entry.sequence} aria-current={following && currentSequence === entry.sequence ? 'step' : undefined} aria-pressed={inspectedSequence === entry.sequence} onClick={() => inspect(entry.sequence)}>
              #{entry.sequence} — {entry.timestampMs} ms — {entry.type}
            </button></li>)}</ol>
          {inspectedEvent && <div aria-label="Evento selecionado"><strong>Evento #{inspectedEvent.sequence}</strong><pre>{JSON.stringify(inspectedEvent, null, 2)}</pre></div>}
        </>}
        {tab === 'states' && <>
          <p>Estado em {Math.round(playbackTimeMs)} ms{selectedId ? ` — selecionado: ${selectedId}` : ''}</p>
          {([['Componentes',state.components],['Requisições',state.requests],['Interações',state.exchanges]] as const).map(([label, entries]) =>
            <div key={label}><h3>{label}</h3><ul>{Object.entries(entries).map(([id, value]) => <li key={id}><strong>{id}</strong>: {value}{id === selectedId && ' (selecionado)'}</li>)}</ul></div>)}
        </>}
      </div>
    </div>}
  </section>;
}
