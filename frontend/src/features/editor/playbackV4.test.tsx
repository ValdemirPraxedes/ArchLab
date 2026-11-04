import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { SimulationPlaybackV2 } from './SimulationPlaybackV2';

const broker = {
  execution:{id:'exec-001',status:'COMPLETED_WITH_ERRORS',mainStatus:'COMPLETED',requestId:'publish-1',entryComponentId:'c',entryConnectionId:'cb',totalDurationMs:30010},
  initialState:{components:{c:'IDLE',b:'IDLE'},requests:{'publish-1':'CREATED'},exchanges:{}},
  trace:[
    {sequence:1,timestampMs:0,type:'STATE_TRANSITION',entityKind:'BROKER_MESSAGE',entityId:'broker-message-1',previousState:null,newState:'CREATED',cause:'MESSAGE_CREATED'},
    {sequence:2,timestampMs:10,type:'STATE_TRANSITION',entityKind:'BROKER_MESSAGE',entityId:'broker-message-1',previousState:'CREATED',newState:'QUEUED',cause:'NO_CONSUMER'},
    {sequence:3,timestampMs:30010,type:'STATE_TRANSITION',entityKind:'BROKER_MESSAGE',entityId:'broker-message-1',previousState:'QUEUED',newState:'TIMED_OUT',cause:'QUEUE_WAIT_TIMEOUT'},
  ],timeline:[],messageRepresentations:[],resourceStates:[{id:'broker-message-1',kind:'BROKER_MESSAGE',status:'TIMED_OUT'}],
  metrics:{totalDurationMs:30010,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[],
    messaging:[{connectionId:'cb',published:1,delivered:0,processed:0,pending:0,failures:1}]},
} as unknown as SimulationResponseV2;

describe('reprodução de recursos v0.4', () => {
  it('distingue publicação concluída da falha global e acompanha timeout virtual', () => {
    render(<SimulationPlaybackV2 result={broker} onStatus={vi.fn()}/>);
    expect(screen.getByText(/Operação principal:/)).toHaveTextContent('COMPLETED');
    expect(screen.getByText(/Conclusão global:/)).toHaveTextContent('COMPLETED_WITH_ERRORS');
    expect(screen.getByText(/Estado no relógio:/)).toHaveTextContent('EM ANDAMENTO');
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('BROKER_MESSAGE broker-message-1: não iniciado');
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('CREATED');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('QUEUED');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('TIMED_OUT');
    expect(screen.getByText(/Estado no relógio:/)).toHaveTextContent('COMPLETED_WITH_ERRORS');
    fireEvent.click(screen.getByRole('button',{name:/Abrir inspeção/}));
    expect(screen.getByLabelText('Métricas de mensageria')).toHaveTextContent('1 publicadas, 0 entregues');
  });
  it('mostra fan-out do Topic sem somar entregas ao relógio', () => {
    const topic = {...broker,execution:{...broker.execution,status:'COMPLETED',totalDurationMs:20},
      metrics:{...broker.metrics,totalDurationMs:20,messaging:[{connectionId:'cb',published:1,delivered:2,processed:2,pending:0}]},
      resourceStates:[{id:'delivery-1',kind:'DELIVERY',status:'COMPLETED'},{id:'delivery-2',kind:'DELIVERY',status:'COMPLETED'}],
      trace:[] } as unknown as SimulationResponseV2;
    render(<SimulationPlaybackV2 result={topic} onStatus={vi.fn()}/>);
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('delivery-1');
    expect(screen.getByLabelText('Recursos da execução')).toHaveTextContent('delivery-2');
    fireEvent.click(screen.getByRole('button',{name:/Abrir inspeção/}));
    expect(screen.getByLabelText('Métricas de mensageria')).toHaveTextContent('1 publicadas, 2 entregues');
    expect(screen.getByText(/Tempo total:/)).toHaveTextContent('20 ms');
  });
});
