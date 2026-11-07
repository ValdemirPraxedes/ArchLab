import {describe,expect,it,vi} from 'vitest';
import {render,screen} from '@testing-library/react';
import matrixJson from '../../../../docs/api/examples/v0.6/capability-matrix-v6.json?raw';
import type {BehaviorV41} from '../../api/archlabV1Client';
import {BehaviorEditorV41} from './BehaviorEditorV41';
import {DATA_ACTION_TARGETS_V6} from './dataCapabilitiesV6';
import {createArchitectureEdge,createArchitectureNode} from './model';

const matrix=JSON.parse(matrixJson) as {actions:Record<string,string[]>};
const resourceTypes=['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'] as const;

describe('matriz Action × tipo do editor = matriz normativa v0.6',()=>{
  it('tem exatamente as combinações de capability-matrix-v6.json',()=>{
    const editor=Object.fromEntries(Object.entries(DATA_ACTION_TARGETS_V6).map(([k,v])=>[k,[...(v ?? [])].sort()]));
    const normative=Object.fromEntries(Object.entries(matrix.actions).map(([k,v])=>[k,[...v].sort()]));
    expect(editor).toEqual(normative);
  });
  it.each(Object.keys(matrix.actions).filter(kind=>kind.startsWith('DB_')))(
    '%s só oferece conexões para tipos compatíveis',kind=>{
      const service=createArchitectureNode('SERVICE',{x:0,y:0});
      const targets=resourceTypes.map(type=>createArchitectureNode(type,{x:0,y:0}));
      const edges=targets.map(target=>{
        const edge=createArchitectureEdge(service.id,target.id,'TCP',1);
        edge.data!.integration={kind:'DATABASE_QUERY'};
        return edge;
      });
      const behavior={id:'receive',trigger:'REQUEST_RECEIVED',actions:[{id:'a',kind,connectionId:'',
        collection:'c',key:'k',field:'f',equals:'v',recordCount:1}]} as unknown as BehaviorV41;
      render(<BehaviorEditorV41 node={service} nodes={[service,...targets]} edges={edges} behaviors={[behavior]} onChange={vi.fn()} disabled={false}/>);
      const connection=screen.getByRole('combobox',{name:'Conexão da ação 1.1'});
      targets.forEach((target,index)=>{
        if (matrix.actions[kind].includes(target.data.componentType)) expect(connection).toHaveTextContent(edges[index].id);
        else expect(connection).not.toHaveTextContent(edges[index].id);
      });
    });
});
