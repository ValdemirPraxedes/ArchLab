import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import type {SimulationResponseV2} from '../../api/archlabV2Client';
import {SimulationPlaybackV2} from './SimulationPlaybackV2';

const v5Result={execution:{id:'exec',mode:'UNIFIED',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'req',entryComponentId:'client',entryConnectionId:'edge',totalDurationMs:10},initialState:{components:{},requests:{},exchanges:{}},trace:[
  {type:'ACTION_EVENT',sequence:1,timestampMs:5,actionId:'lookup',componentId:'service',operationId:'req',status:'COMPLETED',cause:'CACHE_GET'},
  {type:'ACTION_EVENT',sequence:2,timestampMs:6,actionId:'hit-response',componentId:'service',operationId:'req',status:'SKIPPED',cause:'BRANCH'},
],resourceOutcomes:[{actionId:'lookup',status:'SUCCEEDED',resultCode:'MISS',timestampMs:5,componentId:'cache',operationId:'req',details:{key:'orders'}}],timeline:[],messageRepresentations:[],resourceStates:[],metrics:{totalDurationMs:10,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]}} as unknown as SimulationResponseV2;

describe('reprodução v0.5',()=>{
  it('mostra resultado tipado e ramo SKIPPED no relógio virtual',()=>{
    const result=v5Result;
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Resultados dos recursos v0.5')).toHaveTextContent('MISS');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    fireEvent.click(screen.getByRole('button',{name:/Actions e ramos/}));
    expect(screen.getByLabelText('Actions e ramos v0.5')).toHaveTextContent('SKIPPED');
  });
  it('Actions e ramos começa recolhido, conta os registros e expande/recolhe pelo teclado',()=>{
    render(<SimulationPlaybackV2 result={v5Result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    const section=screen.getByRole('region',{name:'Actions e ramos v0.5'});
    const toggle=screen.getByRole('button',{name:/Actions e ramos/});
    expect(section).toHaveClass('playback-collapsible');
    expect(toggle).toHaveAttribute('aria-expanded','false');
    expect(toggle).toHaveAttribute('aria-controls','playback-action-events');
    expect(toggle).toHaveTextContent('Actions e ramos (1 registro)');
    expect(toggle.querySelector('svg')).not.toBeNull();
    expect(section).not.toHaveTextContent('lookup');
    toggle.focus();
    expect(toggle).toHaveFocus();
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded','true');
    const list=screen.getByRole('list',{name:'Registros de Actions e ramos'});
    expect(list).toHaveClass('playback-collapsible__list');
    expect(list).toHaveAttribute('tabindex','0');
    expect(list).toHaveTextContent('5 ms · lookup · COMPLETED');
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded','false');
    expect(screen.queryByRole('list',{name:'Registros de Actions e ramos'})).not.toBeInTheDocument();
  });
  it('atualiza contador e registros durante o playback, em ordem cronológica e com status',()=>{
    render(<SimulationPlaybackV2 result={v5Result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    expect(screen.queryByRole('region',{name:'Actions e ramos v0.5'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    fireEvent.click(screen.getByRole('button',{name:/Actions e ramos/}));
    expect(screen.getByRole('button',{name:/Actions e ramos/})).toHaveTextContent('(1 registro)');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    const toggle=screen.getByRole('button',{name:/Actions e ramos/});
    expect(toggle).toHaveTextContent('(2 registros)');
    expect(toggle).toHaveAttribute('aria-expanded','true');
    const items=[...screen.getByRole('list',{name:'Registros de Actions e ramos'}).querySelectorAll('li')].map(li=>li.textContent);
    expect(items).toEqual(['5 ms · lookup · COMPLETED','6 ms · hit-response · SKIPPED (ramo descartado)']);
  });
  it('preserva o botão Abrir inspeção junto do painel recolhido',()=>{
    render(<SimulationPlaybackV2 result={v5Result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByRole('button',{name:/Actions e ramos/})).toHaveAttribute('aria-expanded','false');
    const inspect=screen.getByRole('button',{name:/Abrir inspeção/});
    fireEvent.click(inspect);
    expect(screen.getByRole('button',{name:/Recolher inspeção/})).toHaveAttribute('aria-expanded','true');
    expect(screen.getByRole('button',{name:/Actions e ramos/})).toHaveAttribute('aria-expanded','false');
  });
});
