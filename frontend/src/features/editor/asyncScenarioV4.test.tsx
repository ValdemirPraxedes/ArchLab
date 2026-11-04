import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AsyncScenarioEditor } from './AsyncScenarioEditor';
import { createArchitectureNode, createArchitectureEdge } from './model';
import { emptyAsyncScenario, toArchitectureV1, toAsyncRequestV1 } from './architectureV1';

describe('cenários assíncronos v0.4', () => {
  it('seleciona sessão WebSocket Client/API e envia discriminador', () => {
    const client = createArchitectureNode('WEB_BROWSER',{x:0,y:0});
    const api = createArchitectureNode('API',{x:1,y:0});
    const edge = createArchitectureEdge(client.id,api.id,'WEBSOCKET',10);
    edge.data!.integration = {kind:'WEBSOCKET',path:'/events',connectTimeoutMs:5000,idleTimeoutMs:null,closeMode:'AUTOMATIC',messages:[{sender:'SOURCE',intervalMs:5}]};
    const onChange = vi.fn();
    render(<AsyncScenarioEditor kind="WEBSOCKET_SESSION" nodes={[client,api]} edges={[edge]} value={emptyAsyncScenario()} onChange={onChange} disabled={false}/>);
    fireEvent.change(screen.getByRole('combobox',{name:'Origem inicial'}),{target:{value:client.id}});
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({entryComponentId:client.id}));
    const request = toAsyncRequestV1(toArchitectureV1([client,api],[edge]),'WEBSOCKET_SESSION',
      {...emptyAsyncScenario(),entryComponentId:client.id,entryConnectionId:edge.id});
    expect(request.scenario).toEqual({kind:'WEBSOCKET_SESSION',entryComponentId:client.id,entryConnectionId:edge.id});
  });
  it('filtra destinos do Broker pelo perfil e envia publishCount', () => {
    const client = createArchitectureNode('BOT',{x:0,y:0});
    const broker = createArchitectureNode('BROKER',{x:1,y:0});
    broker.data.destinations = [{name:'orders',kind:'QUEUE'},{name:'events',kind:'TOPIC'}];
    const edge = createArchitectureEdge(client.id,broker.id,'QUEUE',10);
    edge.data!.integration = {kind:'QUEUE',queueName:'orders'};
    const onChange = vi.fn();
    const value = {...emptyAsyncScenario(),entryComponentId:client.id,entryConnectionId:edge.id};
    render(<AsyncScenarioEditor kind="MESSAGE_PUBLISH" nodes={[client,broker]} edges={[edge]} value={value} onChange={onChange} disabled={false}/>);
    expect(screen.getByRole('option',{name:'orders'})).toBeInTheDocument();
    expect(screen.queryByRole('option',{name:'events'})).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox',{name:'Destino do Broker'}),{target:{value:'orders'}});
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({destinationName:'orders'}));
    const request = toAsyncRequestV1(toArchitectureV1([client,broker],[edge]),'MESSAGE_PUBLISH',
      {...value,destinationName:'orders',publishCount:3});
    expect(request.scenario).toMatchObject({kind:'MESSAGE_PUBLISH',destinationName:'orders',publishCount:3});
  });
});
