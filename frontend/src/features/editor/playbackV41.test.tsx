import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import type {SimulationResponseV2} from '../../api/archlabV2Client';
import {SimulationPlaybackV2} from './SimulationPlaybackV2';

const result={
  execution:{id:'exec-1',mode:'UNIFIED',status:'COMPLETED_WITH_ERRORS',mainStatus:'COMPLETED',responseStatusCode:201,
    requestId:'request-1',entryComponentId:'browser',entryConnectionId:'entry',totalDurationMs:30},
  initialState:{components:{},requests:{},exchanges:{}},
  trace:[
    {type:'ACTION_EVENT',sequence:1,timestampMs:5,action:'COMPLETED',actionId:'respond',componentId:'orders',correlation:{operationId:'exchange-1',profile:'HTTP_REST'}},
    {type:'STATE_TRANSITION',sequence:2,timestampMs:30,entityKind:'DELIVERY',entityId:'delivery-1',previousState:'PROCESSING',newState:'TIMED_OUT',cause:'TIMEOUT',correlation:{operationId:'delivery-1',parentOperationId:'exchange-1',profile:'QUEUE'}},
  ],timeline:[],messageRepresentations:[],resourceStates:[{id:'delivery-1',kind:'DELIVERY',status:'TIMED_OUT'}],
  metrics:{totalDurationMs:30,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]},
} as unknown as SimulationResponseV2;

describe('reprodução mista v0.4.1',()=>{
  it('mostra HTTP 201 antes da falha derivada e correlaciona operações ao avançar',()=>{
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    expect(screen.getByText(/Operação principal:/)).toHaveTextContent('COMPLETED');
    expect(screen.getByText(/Conclusão global:/)).toHaveTextContent('COMPLETED_WITH_ERRORS');
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('Resposta HTTP: 201');
    // The correlated flow starts collapsed; expanding it shows the same events as before.
    fireEvent.click(screen.getByRole('button',{name:/Fluxo correlacionado/}));
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('exchange-1');
    expect(screen.getByLabelText('Fluxo correlacionado')).not.toHaveTextContent('delivery-1');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('delivery-1');
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('QUEUE');
  });
  it('fluxo correlacionado começa recolhido, mostra a contagem e expande/recolhe pelo teclado',()=>{
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    const section=screen.getByRole('region',{name:'Fluxo correlacionado'});
    const toggle=screen.getByRole('button',{name:/Fluxo correlacionado/});
    expect(toggle).toHaveAttribute('aria-expanded','false');
    expect(toggle).toHaveAttribute('aria-controls','playback-flow-events');
    expect(toggle).toHaveTextContent('(0 eventos)');
    expect(screen.queryByRole('list',{name:'Eventos do fluxo correlacionado'})).not.toBeInTheDocument();
    toggle.focus();
    expect(toggle).toHaveFocus();
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded','true');
    const list=screen.getByRole('list',{name:'Eventos do fluxo correlacionado'});
    expect(list).toHaveAttribute('id','playback-flow-events');
    expect(list).toHaveClass('playback-flow__events');
    expect(list).toHaveAttribute('tabindex','0');
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded','false');
    expect(section).not.toContainElement(screen.queryByRole('list',{name:'Eventos do fluxo correlacionado'}));
  });
  it('preserva a ordem e o conteúdo dos eventos e mantém a inspeção detalhada',()=>{
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    const toggle=screen.getByRole('button',{name:/Fluxo correlacionado/});
    expect(toggle).toHaveTextContent('(2 eventos)');
    fireEvent.click(toggle);
    const items=screen.getByRole('list',{name:'Eventos do fluxo correlacionado'}).querySelectorAll('li');
    expect([...items].map(item=>item.textContent)).toEqual([
      '#1 · 5 ms · ACTION_EVENT · operação exchange-1 · HTTP_REST',
      '#2 · 30 ms · STATE_TRANSITION · operação delivery-1 ← exchange-1 · QUEUE',
    ]);
    const inspect=screen.getByRole('button',{name:/Abrir inspeção/});
    expect(inspect).toHaveAttribute('aria-expanded','false');
    fireEvent.click(inspect);
    expect(screen.getByRole('button',{name:/Recolher inspeção/})).toHaveAttribute('aria-expanded','true');
  });
  it('o CSS limita a lista expandida a 160 px com rolagem interna e usa as variáveis de tema',async()=>{
    const {default:css}=await import('../../style.css?raw');
    const rule=/\.playback-flow__events\{([^}]*)\}/.exec(css.replace(/\s+/g,''))?.[1] ?? '';
    expect(rule).toContain('max-height:160px');
    expect(rule).toContain('overflow-y:auto');
    expect(rule).toContain('var(--border)');
    expect(css).toMatch(/\.playback-flow__title button:focus-visible/);
  });
});
