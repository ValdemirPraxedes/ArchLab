import type { BehaviorActionV41, BehaviorV41 } from '../../api/archlabV1Client';
import type { ArchitectureEdge, ArchitectureNode } from './model';

const triggers = ['REQUEST_RECEIVED','MESSAGE_CONSUMED','WS_MESSAGE_RECEIVED','SESSION_OPENED'] as const;
const kinds = ['PROCESS','CALL','PUBLISH','RESPOND','OPEN_SESSION','WS_SEND','CLOSE_SESSION','FORK','JOIN'] as const;
type Kind = typeof kinds[number];
type Props = {node: ArchitectureNode; nodes: ArchitectureNode[]; edges: ArchitectureEdge[]; behaviors: BehaviorV41[];
  onChange: (value: BehaviorV41[]) => void; disabled: boolean};
const id = (prefix: string) => `${prefix}-${crypto.randomUUID().slice(0,8)}`;
function defaults(kind: Kind, edges: ArchitectureEdge[]): BehaviorActionV41 {
  const connection = edges[0]?.id ?? '';
  const actionId = id('action');
  switch (kind) {
    case 'PROCESS': return {id:actionId,kind,durationMs:0};
    case 'CALL': return {id:actionId,kind,connectionId:connection};
    case 'PUBLISH': return {id:actionId,kind,connectionId:connection,destinationName:''};
    case 'RESPOND': return {id:actionId,kind,statusCode:200};
    case 'OPEN_SESSION': return {id:actionId,kind,connectionId:connection,sessionAlias:id('session')};
    case 'WS_SEND': return {id:actionId,kind,sessionAlias:'',sender:'TARGET'};
    case 'CLOSE_SESSION': return {id:actionId,kind,sessionAlias:''};
    case 'FORK': case 'JOIN': return {id:actionId,kind,actionIds:[]};
  }
}
function compatible(kind: Kind, edge: ArchitectureEdge): boolean {
  const protocol=edge.data?.integration?.kind;
  if (kind==='CALL') return !!protocol && !['QUEUE','TOPIC','MQTT','WEBSOCKET'].includes(protocol);
  if (kind==='PUBLISH') return !!protocol && ['QUEUE','TOPIC','MQTT'].includes(protocol);
  return kind==='OPEN_SESSION' && protocol==='WEBSOCKET';
}
export function BehaviorEditorV41({node,nodes,edges,behaviors,onChange,disabled}: Props) {
  const outgoing=edges.filter(edge => edge.source===node.id);
  const changeBehavior=(index:number, behavior:BehaviorV41) => onChange(behaviors.map((item,i)=>i===index?behavior:item));
  const changeAction=(bi:number,ai:number,action:BehaviorActionV41) => changeBehavior(bi,{...behaviors[bi],actions:behaviors[bi].actions.map((item,i)=>i===ai?action:item)});
  return <section className="behavior-editor" aria-label="Comportamentos da API">
    <h3>Comportamentos</h3><p>Uma conexão só é acionada quando uma ação a utiliza.</p>
    {behaviors.map((behavior,bi) => <fieldset key={behavior.id} disabled={disabled}>
      <legend>{behavior.id}</legend>
      <label>ID do comportamento<input aria-label={`ID comportamento ${bi+1}`} value={behavior.id} onChange={e=>changeBehavior(bi,{...behavior,id:e.target.value})}/></label>
      <label>Gatilho<select aria-label={`Gatilho ${bi+1}`} value={behavior.trigger} onChange={e=>changeBehavior(bi,{...behavior,trigger:e.target.value as BehaviorV41['trigger'],match:undefined})}>
        {triggers.map(trigger=><option key={trigger} value={trigger}>{trigger}</option>)}
      </select></label>
      {behavior.trigger==='REQUEST_RECEIVED' && <label>Operação correspondente<input aria-label={`Operação do gatilho ${bi+1}`} value={behavior.match?.operation ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{operation:e.target.value}:undefined})}/></label>}
      {behavior.trigger==='MESSAGE_CONSUMED' && <label>Destino correspondente<input aria-label={`Destino do gatilho ${bi+1}`} value={behavior.match?.destinationName ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{destinationName:e.target.value}:undefined})}/></label>}
      {(behavior.trigger==='WS_MESSAGE_RECEIVED'||behavior.trigger==='SESSION_OPENED') && <label>Caminho WebSocket<input aria-label={`Caminho do gatilho ${bi+1}`} value={behavior.match?.websocketPath ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{websocketPath:e.target.value}:undefined})}/></label>}
      {behavior.actions.map((action,ai) => {
        const set=(patch:Record<string,unknown>)=>changeAction(bi,ai,{...action,...patch} as BehaviorActionV41);
        const choices=outgoing.filter(edge=>compatible(action.kind,edge));
        return <fieldset key={action.id}><legend>Ação {ai+1}</legend>
          <label>ID da ação<input aria-label={`ID ação ${bi+1}.${ai+1}`} value={action.id} onChange={e=>set({id:e.target.value})}/></label>
          <label>Tipo<select aria-label={`Tipo da ação ${bi+1}.${ai+1}`} value={action.kind} onChange={e=>changeAction(bi,ai,defaults(e.target.value as Kind,outgoing.filter(edge=>compatible(e.target.value as Kind,edge))))}>
            {kinds.map(kind=><option key={kind} value={kind}>{kind}</option>)}
          </select></label>
          <label>Após ações<select aria-label={`Dependências ${bi+1}.${ai+1}`} multiple value={action.dependsOn ?? []}
            onChange={e=>set({dependsOn:Array.from(e.target.selectedOptions,option=>option.value)})}>
            {behavior.actions.filter(item=>item.id!==action.id).map(item=><option key={item.id} value={item.id}>{item.id}</option>)}
          </select></label>
          {'connectionId' in action && <label>Conexão<select aria-label={`Conexão da ação ${bi+1}.${ai+1}`} value={action.connectionId} onChange={e=>set({connectionId:e.target.value})}>
            <option value="">Selecione...</option>{choices.map(edge=><option key={edge.id} value={edge.id}>{edge.id} · {edge.data?.integration?.kind}</option>)}
          </select></label>}
          {action.kind==='PROCESS' && <label>Duração (ms)<input type="number" min="0" aria-label={`Duração ${bi+1}.${ai+1}`} value={action.durationMs ?? 0} onChange={e=>set({durationMs:Number(e.target.value)})}/></label>}
          {action.kind==='CALL' && <label>Timeout (ms)<input type="number" min="0" aria-label={`Timeout ${bi+1}.${ai+1}`} value={action.timeoutMs ?? 5000} onChange={e=>set({timeoutMs:Number(e.target.value)})}/></label>}
          {action.kind==='PUBLISH' && <label>Destino<select aria-label={`Destino da ação ${bi+1}.${ai+1}`} value={action.destinationName} onChange={e=>set({destinationName:e.target.value})}>
            <option value="">Selecione...</option>{nodes.find(item=>item.id===edges.find(edge=>edge.id===action.connectionId)?.target)?.data.destinations?.map(destination=><option key={destination.name} value={destination.name}>{destination.name}</option>)}
          </select></label>}
          {action.kind==='RESPOND' && <label>Status HTTP<input type="number" min="100" max="599" aria-label={`Status ${bi+1}.${ai+1}`} value={action.statusCode ?? 200} onChange={e=>set({statusCode:Number(e.target.value)})}/></label>}
          {'sessionAlias' in action && <label>Alias da sessão<input aria-label={`Alias da ação ${bi+1}.${ai+1}`} value={action.sessionAlias ?? ''} onChange={e=>set({sessionAlias:e.target.value})}/></label>}
          {action.kind==='WS_SEND' && <><label>Emissor<select aria-label={`Emissor ${bi+1}.${ai+1}`} value={action.sender ?? 'TARGET'} onChange={e=>set({sender:e.target.value})}><option value="SOURCE">SOURCE</option><option value="TARGET">TARGET</option></select></label>
            <label>Mensagem<input aria-label={`Mensagem ${bi+1}.${ai+1}`} value={action.payload ?? ''} onChange={e=>set({payload:e.target.value})}/></label></>}
          {(action.kind==='FORK'||action.kind==='JOIN') && <label>Ações referenciadas<select multiple aria-label={`Ações referenciadas ${bi+1}.${ai+1}`} value={action.actionIds}
            onChange={e=>set({actionIds:Array.from(e.target.selectedOptions,option=>option.value)})}>
            {behavior.actions.filter(item=>item.id!==action.id).map(item=><option key={item.id} value={item.id}>{item.id}</option>)}
          </select></label>}
          <button type="button" onClick={()=>changeBehavior(bi,{...behavior,actions:behavior.actions.filter((_,i)=>i!==ai)})} disabled={behavior.actions.length===1}>Remover ação</button>
        </fieldset>;
      })}
      <button type="button" onClick={()=>changeBehavior(bi,{...behavior,actions:[...behavior.actions,defaults('PROCESS',outgoing)]})}>Adicionar ação</button>
      <button type="button" onClick={()=>onChange(behaviors.filter((_,i)=>i!==bi))}>Remover comportamento</button>
    </fieldset>)}
    <button type="button" disabled={disabled} onClick={()=>onChange([...behaviors,{id:id('behavior'),trigger:'REQUEST_RECEIVED',actions:[defaults('RESPOND',outgoing)]}])}>Adicionar comportamento</button>
  </section>;
}
