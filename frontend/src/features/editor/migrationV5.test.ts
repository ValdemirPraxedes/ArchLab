import {describe,expect,it} from 'vitest';
import {prepareArchitectureImportV5} from './migrationV5';
import {toArchitectureV1} from './architectureV1';

describe('importação explícita API → SERVICE',()=>{
  const document={components:[
    {id:'client',displayName:'Client',type:'WEB_BROWSER'},
    {id:'api-one',displayName:'Orders',type:'API',preProcessingMs:4,postProcessingMs:6,behaviors:[{id:'reply',trigger:'REQUEST_RECEIVED',actions:[{id:'respond',kind:'RESPOND',statusCode:200}]}]},
    {id:'gateway',displayName:'Gateway',type:'API_GATEWAY',behaviors:[],routes:[]},
  ],connections:[{id:'entry',sourceId:'client',targetId:'api-one',latencyMs:10,integration:{kind:'HTTP_REST',request:{method:'GET',path:'/orders'},response:{statusCode:200}}}]};
  it('preserva IDs, Behaviors, conexões e API_GATEWAY sem alterar o original',()=>{
    const original=structuredClone(document);
    const result=prepareArchitectureImportV5(document);
    expect(result.convertedIds).toEqual(['api-one']);
    expect(result.architecture.components[1]).toMatchObject({id:'api-one',type:'SERVICE',behaviors:document.components[1].behaviors});
    expect(result.architecture.components[2].type).toBe('API_GATEWAY');
    expect(result.architecture.connections).toEqual(document.connections);
    expect(toArchitectureV1(result.nodes,result.edges)).toEqual(result.architecture);
    expect(document).toEqual(original);
  });
  it('rejeita referências inválidas sem mutação parcial',()=>{
    const invalid={...document,connections:[{...document.connections[0],targetId:'missing'}]};
    const original=structuredClone(invalid);
    expect(()=>prepareArchitectureImportV5(invalid)).toThrow(/connections\[0\]/);
    expect(invalid).toEqual(original);
  });
});
