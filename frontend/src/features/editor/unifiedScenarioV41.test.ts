import {describe,expect,it} from 'vitest';
import type {ArchitectureV1} from '../../api/archlabV1Client';
import type {ArchitectureEdge,ArchitectureNode} from './model';
import {operationForEntry,toUnifiedRequestV41} from './unifiedScenarioV41';

const nodes = [
  {id:'client',data:{componentType:'WEB_BROWSER',label:'Browser'}},
  {id:'api',data:{componentType:'API',label:'API',behaviors:[{id:'handle',trigger:'REQUEST_RECEIVED',actions:[{id:'respond',kind:'RESPOND',statusCode:201}]}]}},
] as ArchitectureNode[];
const edges = [{id:'request',source:'client',target:'api',data:{protocol:'REST',latencyMs:2,
  integration:{kind:'HTTP_REST',request:{method:'POST',path:'/orders'},response:{statusCode:201}}}}] as ArchitectureEdge[];
const architecture = {components:[{id:'client',type:'WEB_BROWSER',displayName:'Browser'},
  {id:'api',type:'API',displayName:'API',behaviors:nodes[1].data.behaviors,preProcessingMs:0,postProcessingMs:0}],
  connections:[{id:'request',sourceId:'client',targetId:'api',latencyMs:2,integration:edges[0].data!.integration}]} as ArchitectureV1;

describe('UnifiedScenario v0.4.1',()=>{
  it('deriva a operação REST da conexão escolhida',()=>{
    expect(operationForEntry(edges[0])).toBe('POST /orders');
  });
  it('envia cenário sem tipo global e mantém comportamentos por API',()=>{
    const request=toUnifiedRequestV41(architecture,{componentId:'client',connectionId:'request',operation:'POST /orders'},[],nodes,edges);
    expect(request.scenario).toEqual({entry:{componentId:'client',connectionId:'request',operation:'POST /orders'},sessionSetup:[]});
    expect(request.architecture.components[1]).toMatchObject({behaviors:[{actions:[{kind:'RESPOND',statusCode:201}]}]});
  });
  it('aceita preparação WebSocket sem escolher outro cenário',()=>{
    const request=toUnifiedRequestV41(architecture,{componentId:'client',connectionId:'request',operation:''},
      [{connectionId:'socket',sessionAlias:'notifications',autoCloseAtEnd:true}],nodes,edges);
    expect(request.scenario).toMatchObject({entry:{connectionId:'request'},sessionSetup:[{sessionAlias:'notifications'}]});
  });
  it('rejeita conexão que não sai do componente inicial',()=>{
    expect(()=>toUnifiedRequestV41(architecture,{componentId:'api',connectionId:'request',operation:''},[],nodes,edges))
      .toThrow('Selecione uma conexão de saída');
  });
});
