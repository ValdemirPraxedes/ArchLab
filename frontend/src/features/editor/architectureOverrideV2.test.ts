import { describe, expect, it } from 'vitest';
import type { ArchitectureNode, ArchitectureEdge } from './model';
import { toArchitectureV2 } from './architectureV2';

const nodes = [
  {id:'client',data:{componentType:'CLIENT',label:'Client'},position:{x:0,y:0}},
  {id:'api-a',data:{componentType:'API',label:'API A',preProcessingMs:0,postProcessingMs:0},position:{x:0,y:0}},
  {id:'api-c',data:{componentType:'API',label:'API C',preProcessingMs:0,postProcessingMs:0},position:{x:0,y:0}},
  {id:'api-b',data:{componentType:'API',label:'API B',preProcessingMs:20,postProcessingMs:10},position:{x:0,y:0}},
  {id:'db',data:{componentType:'DATABASE',label:'DB',processingMs:3},position:{x:0,y:0}},
] as ArchitectureNode[];
const edge = (id:string,source:string,target:string,pre?:number,post?:number):ArchitectureEdge => ({
  id,source,target,data:{protocol:target==='db'?'TCP':'REST',latencyMs:5,...(pre === undefined ? {} : {apiProcessingOverride:{preProcessingMs:pre,postProcessingMs:post!}})},
});
describe('RF-044 serialização de processamento por conexão', () => {
  it('mantém defaults e overrides diferentes em duas entradas da mesma API',()=>{
    const result=toArchitectureV2(nodes,[edge('a-b','api-a','api-b',40,15),edge('c-b','api-c','api-b',10,5),edge('client-b','client','api-b')]);
    expect(result.connections[0].apiProcessingOverride).toEqual({preProcessingMs:40,postProcessingMs:15});
    expect(result.connections[1].apiProcessingOverride).toEqual({preProcessingMs:10,postProcessingMs:5});
    expect(result.connections[2].apiProcessingOverride).toBeUndefined();
    expect(result.components.find(n=>n.id==='api-b')).toMatchObject({preProcessingMs:20,postProcessingMs:10});
  });
  it('permite valores zero, mas rejeita negativo e override em Database',()=>{
    expect(toArchitectureV2(nodes,[edge('a-b','api-a','api-b',0,0)]).connections[0].apiProcessingOverride).toEqual({preProcessingMs:0,postProcessingMs:0});
    expect(()=>toArchitectureV2(nodes,[edge('a-b','api-a','api-b',-1,0)])).toThrow();
    expect(()=>toArchitectureV2(nodes,[edge('a-b','api-a','api-b',1,1_000_000_001)])).toThrow();
    expect(()=>toArchitectureV2(nodes,[edge('a-db','api-a','db',5,6)])).toThrow();
  });
});
