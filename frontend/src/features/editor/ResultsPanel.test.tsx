import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import ResultsPanel from './ResultsPanel';
import type { SimulationResponse } from '../../api/archlabClient';

const fixture = {
 execution: { id:'e',status:'COMPLETED',totalDurationMs:260 },
 initialState: {components:{'api-1':'IDLE'},requests:{'req-1':'CREATED'},exchanges:{}},
 metrics: {apiPreProcessingMs:30,apiPostProcessingMs:20,databaseProcessingMs:150,communicationMs:60,apiWaitingMs:170},
 timeline: Array.from({length:7},(_,i)=>({id:`a${i}`,kind:'PROCESSING',componentId:'api-1',requestId:'req-1',phase:'API_PRE_PROCESSING',startMs:i*10,endMs:(i+1)*10,durationMs:10})),
 trace: [{type:'STATE_TRANSITION',timestampMs:50,sequence:1,entityKind:'COMPONENT',entityId:'api-1',previousState:'IDLE',newState:'WAITING',cause:'DATABASE_QUERY_SENT'}],
} as SimulationResponse;
describe('painel de resultados', () => {
 it('mostra tempos sem somar espera ao total e sete atividades', () => {
  render(<ResultsPanel result={fixture} playbackTimeMs={50} playbackSequence={1} />);
  expect(screen.getByText('Tempo total: 260 ms')).toBeInTheDocument();
  expect(screen.getByText(/Espera bloqueante da API:/)).toHaveTextContent('170 ms');
  expect(screen.getByText('Timeline — 7 atividades')).toBeInTheDocument();
 });
 it('alterna abas, inspeciona evento e preserva relógio', () => {
  render(<ResultsPanel result={fixture} playbackTimeMs={50} playbackSequence={1} />);
  fireEvent.click(screen.getByRole('tab',{name:'Eventos'}));
  fireEvent.click(screen.getByRole('button',{name:/#1 — 50 ms/}));
  expect(screen.getByLabelText('Evento selecionado')).toHaveTextContent('DATABASE_QUERY_SENT');
  fireEvent.click(screen.getByRole('tab',{name:'Estados'}));
  expect(screen.getByText('api-1').closest('li')).toHaveTextContent('WAITING');
 });
 it('recolhe e expande sem descartar dados', () => {
  render(<ResultsPanel result={fixture} playbackTimeMs={0} playbackSequence={0} />);
  fireEvent.click(screen.getByRole('button',{name:'Recolher painel'}));
  expect(screen.queryByRole('tab',{name:'Eventos'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Expandir painel'}));
  expect(screen.getByRole('tab',{name:'Eventos'})).toBeInTheDocument();
 });
});
