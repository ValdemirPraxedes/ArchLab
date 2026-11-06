import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import {createArchitectureEdge,createArchitectureNode,allowedProtocols} from './model';
import {toArchitectureV1} from './architectureV1';
import {ServicePropertiesV5} from './ServicePropertiesV5';
import {BehaviorEditorV41} from './BehaviorEditorV41';

describe('editor de serviços v0.5',()=>{
  it('serializa rotas, agendamentos, regras e TTL por tipo',()=>{
    const gateway=createArchitectureNode('API_GATEWAY',{x:0,y:0});
    gateway.data.routes=[{operation:'GET /orders',connectionId:'route'}];
    const scheduler=createArchitectureNode('SCHEDULER',{x:0,y:0});
    scheduler.data.schedules=[{id:'daily',firstAtMs:10,intervalMs:20,occurrenceCount:2}];
    const rules=createArchitectureNode('RULES_ENGINE',{x:0,y:0});
    rules.data.rules=[{id:'r1',field:'lookup',operator:'EQ',expectedValue:'HIT',priority:1,decision:'ALLOW'}];
    const cache=createArchitectureNode('CACHE',{x:0,y:0});
    cache.data.defaultTtlMs=500;
    const components=toArchitectureV1([gateway,scheduler,rules,cache],[]).components;
    expect(components.find(item=>item.type==='API_GATEWAY')).toMatchObject({routes:gateway.data.routes});
    expect(components.find(item=>item.type==='SCHEDULER')).toMatchObject({schedules:scheduler.data.schedules});
    expect(components.find(item=>item.type==='RULES_ENGINE')).toMatchObject({rules:rules.data.rules});
    expect(components.find(item=>item.type==='CACHE')).toMatchObject({defaultTtlMs:500});
  });
  it('edita rota e TTL em painéis contextuais',()=>{
    const cache=createArchitectureNode('CACHE',{x:0,y:0});
    const onChange=vi.fn();
    const {rerender}=render(<ServicePropertiesV5 node={cache} edges={[]} disabled={false} onChange={onChange}/>);
    fireEvent.change(screen.getByRole('spinbutton',{name:'TTL padrão (ms)'}),{target:{value:'250'}});
    expect(onChange).toHaveBeenCalledWith({defaultTtlMs:250});
    const gateway=createArchitectureNode('API_GATEWAY',{x:0,y:0});
    rerender(<ServicePropertiesV5 node={gateway} edges={[]} disabled={false} onChange={onChange}/>);
    expect(screen.queryByRole('spinbutton',{name:'TTL padrão (ms)'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Adicionar rota'}));
    expect(onChange).toHaveBeenCalledWith({routes:[{operation:'GET /',connectionId:''}]});
  });
  it('permite Cache GET e BRANCH com referência de resultado',()=>{
    const service=createArchitectureNode('SERVICE',{x:0,y:0});
    const cache=createArchitectureNode('CACHE',{x:0,y:0});
    const edge=createArchitectureEdge(service.id,cache.id,'REST',0);
    edge.data!.integration={kind:'HTTP_REST',request:{method:'GET',path:'/'},response:{statusCode:200}};
    expect(allowedProtocols('SERVICE','CACHE')).toContain('REST');
    const behavior={id:'request',trigger:'REQUEST_RECEIVED' as const,actions:[
      {id:'lookup',kind:'CACHE_GET' as const,connectionId:edge.id,key:'orders',outputRef:'cache-result'},
      {id:'branch',kind:'BRANCH' as const,dependsOn:['lookup'],resultRef:'cache-result',cases:[{equals:'HIT',actionIds:[]},{equals:'MISS',actionIds:[]}]},
    ]};
    const onChange=vi.fn();
    render(<BehaviorEditorV41 node={service} nodes={[service,cache]} edges={[edge]} behaviors={[behavior]} onChange={onChange} disabled={false}/>);
    expect(screen.getByRole('combobox',{name:'Resultado do BRANCH 1.2'})).toHaveValue('cache-result');
    expect(screen.getByRole('combobox',{name:'Conexão da ação 1.1'})).toHaveTextContent(edge.id);
    fireEvent.change(screen.getByRole('textbox',{name:'key 1.1'}),{target:{value:'new-key'}});
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({actions:expect.arrayContaining([expect.objectContaining({kind:'CACHE_GET',key:'new-key'})])})]));
  });
});
