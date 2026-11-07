import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import {BehaviorEditorV41} from './BehaviorEditorV41';
import {createArchitectureNode,createArchitectureEdge} from './model';

describe('painel contextual de comportamentos',()=>{
  it('filtra conexão de publicação e oferece destinos do Broker',()=>{
    const api=createArchitectureNode('API',{x:0,y:0});
    const db=createArchitectureNode('DATABASE',{x:0,y:0});
    const broker=createArchitectureNode('BROKER',{x:0,y:0});
    broker.data.destinations=[{name:'orders',kind:'QUEUE'}];
    const databaseEdge=createArchitectureEdge(api.id,db.id,'TCP',0);
    const queueEdge=createArchitectureEdge(api.id,broker.id,'QUEUE',0);
    databaseEdge.data!.integration={kind:'DATABASE_QUERY'};
    queueEdge.data!.integration={kind:'QUEUE',queueName:'orders'};
    const behavior={id:'receive',trigger:'REQUEST_RECEIVED' as const,actions:[{id:'publish',kind:'PUBLISH' as const,connectionId:queueEdge.id,destinationName:'orders'}]};
    const onChange=vi.fn();
    render(<BehaviorEditorV41 node={api} nodes={[api,db,broker]} edges={[databaseEdge,queueEdge]} behaviors={[behavior]} onChange={onChange} disabled={false}/>);
    const connection=screen.getByRole('combobox',{name:'Conexão da ação 1.1'});
    expect(connection).toHaveTextContent(queueEdge.id);
    expect(connection).not.toHaveTextContent(databaseEdge.id);
    expect(screen.getByRole('option',{name:'orders'})).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox',{name:'Destino da ação 1.1'}),{target:{value:'orders'}});
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({actions:[expect.objectContaining({kind:'PUBLISH',destinationName:'orders'})]})]));
  });
  it('troca ação e mostra somente os campos aplicáveis',()=>{
    const api=createArchitectureNode('API',{x:0,y:0});
    const behavior={id:'receive',trigger:'REQUEST_RECEIVED' as const,actions:[{id:'respond',kind:'RESPOND' as const,statusCode:200}]};
    const onChange=vi.fn();
    render(<BehaviorEditorV41 node={api} nodes={[api]} edges={[]} behaviors={[behavior]} onChange={onChange} disabled={false}/>);
    expect(screen.getByRole('spinbutton',{name:'Status 1.1'})).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox',{name:'Tipo da ação 1.1'}),{target:{value:'PROCESS'}});
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({actions:[expect.objectContaining({kind:'PROCESS',durationMs:0})]})]));
  });
  it('oferece DB_SELECT apenas para conexão relacional compatível',()=>{
    const service=createArchitectureNode('SERVICE',{x:0,y:0});
    const relational=createArchitectureNode('RELATIONAL_DATABASE',{x:0,y:0});
    const kv=createArchitectureNode('KEY_VALUE_STORE',{x:0,y:0});
    const first=createArchitectureEdge(service.id,relational.id,'TCP',1);
    const second=createArchitectureEdge(service.id,kv.id,'TCP',1);
    first.data!.integration={kind:'DATABASE_QUERY'};
    second.data!.integration={kind:'DATABASE_QUERY'};
    const behavior={id:'receive',trigger:'REQUEST_RECEIVED' as const,actions:[{id:'select',kind:'DB_SELECT' as const,connectionId:first.id,collection:'orders'}]};
    render(<BehaviorEditorV41 node={service} nodes={[service,relational,kv]} edges={[first,second]} behaviors={[behavior]} onChange={vi.fn()} disabled={false}/>);
    const connection=screen.getByRole('combobox',{name:'Conexão da ação 1.1'});
    expect(connection).toHaveTextContent(first.id);
    expect(connection).not.toHaveTextContent(second.id);
  });
});
