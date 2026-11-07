import type { BehaviorActionV41, BehaviorV41 } from '../../api/archlabV1Client';
import { useState } from 'react';
import type { ArchitectureEdge, ArchitectureNode } from './model';

const triggers = ['REQUEST_RECEIVED','MESSAGE_CONSUMED','WS_MESSAGE_RECEIVED','SESSION_OPENED','SCHEDULED_TRIGGER'] as const;
const kinds = ['PROCESS','CALL','PUBLISH','RESPOND','OPEN_SESSION','WS_SEND','CLOSE_SESSION','FORK','JOIN','BRANCH','CACHE_GET','CACHE_PUT','CACHE_INVALIDATE','OBJECT_GET','OBJECT_PUT','OBJECT_DELETE','SEND_NOTIFICATION','INDEX_DOCUMENT','SEARCH_DOCUMENTS','REGISTER_SERVICE','UNREGISTER_SERVICE','DISCOVER_SERVICE','EVALUATE_RULE','SCHEDULE_TRIGGER','TRANSFORM','DB_SELECT','DB_INSERT','DB_UPDATE','DB_DELETE','DB_GET','DB_PUT','DB_FIND','DB_BULK_INSERT','ETL_EXTRACT','ETL_TRANSFORM','ETL_LOAD'] as const;
type Kind = BehaviorActionV41['kind'];
const dataTargets:Partial<Record<Kind,readonly string[]>>={
  DB_SELECT:['RELATIONAL_DATABASE'], DB_INSERT:['RELATIONAL_DATABASE'],
  DB_UPDATE:['RELATIONAL_DATABASE','DOCUMENT_DATABASE'], DB_DELETE:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  DB_GET:['DOCUMENT_DATABASE','KEY_VALUE_STORE'], DB_PUT:['DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  DB_FIND:['DOCUMENT_DATABASE'], DB_BULK_INSERT:['RELATIONAL_DATABASE','DOCUMENT_DATABASE'],
  ETL_EXTRACT:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
  ETL_TRANSFORM:['DATA_TRANSFORMER'],
  ETL_LOAD:['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE'],
};
const resourceTargets:Partial<Record<Kind,string>>={CACHE_GET:'CACHE',CACHE_PUT:'CACHE',CACHE_INVALIDATE:'CACHE',OBJECT_GET:'OBJECT_STORAGE',OBJECT_PUT:'OBJECT_STORAGE',OBJECT_DELETE:'OBJECT_STORAGE',SEND_NOTIFICATION:'NOTIFICATION_SERVICE',INDEX_DOCUMENT:'SEARCH_ENGINE',SEARCH_DOCUMENTS:'SEARCH_ENGINE',REGISTER_SERVICE:'SERVICE_REGISTRY',UNREGISTER_SERVICE:'SERVICE_REGISTRY',DISCOVER_SERVICE:'SERVICE_REGISTRY',EVALUATE_RULE:'RULES_ENGINE'};
const actionFields:Partial<Record<Kind,readonly string[]>>={CACHE_GET:['key','outputRef'],CACHE_PUT:['key','valueMetadata','ttlMs','outputRef'],CACHE_INVALIDATE:['key','outputRef'],OBJECT_GET:['objectKey','outputRef'],OBJECT_PUT:['objectKey','sizeBytes','metadata','outputRef'],OBJECT_DELETE:['objectKey','outputRef'],SEND_NOTIFICATION:['channel','recipientAlias','durationMs','timeoutMs','simulatedResult','outputRef'],INDEX_DOCUMENT:['indexName','documentId','tags','metadata','outputRef'],SEARCH_DOCUMENTS:['indexName','tags','outputRef'],REGISTER_SERVICE:['serviceName','instanceId','targetComponentId','instanceStatus','outputRef'],UNREGISTER_SERVICE:['serviceName','instanceId','outputRef'],DISCOVER_SERVICE:['serviceName','outputRef'],EVALUATE_RULE:['outputRef'],TRANSFORM:['metadata','outputRef'],
  DB_SELECT:['collection','key','limit','outputRef'],DB_INSERT:['collection','key','field','equals','averageRecordBytes','outputRef'],
  DB_UPDATE:['collection','key','field','equals','outputRef'],DB_DELETE:['collection','key','outputRef'],
  DB_GET:['collection','key','outputRef'],DB_PUT:['collection','key','field','equals','outputRef'],
  DB_FIND:['collection','field','equals','limit','outputRef'],DB_BULK_INSERT:['collection','recordCount','averageRecordBytes','outputRef'],
  ETL_EXTRACT:['maxRecords','batchSize','outputRef'],ETL_TRANSFORM:['inputRef','transform','simulateFailure','outputRef'],
  ETL_LOAD:['inputRef','batchSize','outputRef']};
const numberFields=new Set(['ttlMs','sizeBytes','durationMs','timeoutMs','limit','recordCount','averageRecordBytes','maxRecords','batchSize']);
const objectFields=new Set(['metadata','tags']);
function JsonMapField({label,value,onChange}:{label:string;value:unknown;onChange:(value:Record<string,string>)=>void}) {
  const [draft,setDraft]=useState(JSON.stringify(value ?? {}));
  const [error,setError]=useState('');
  return <label>{label} (JSON)<textarea aria-label={label} value={draft} onChange={event=>setDraft(event.target.value)} onBlur={()=>{
    try {
      const parsed=JSON.parse(draft) as unknown;
      if(!parsed || typeof parsed!=='object' || Array.isArray(parsed) || Object.values(parsed).some(item=>typeof item!=='string')) throw new Error();
      onChange(parsed as Record<string,string>);setError('');
    } catch {setError('Informe um objeto JSON com valores de texto.');}
  }}/>{error && <small role="alert">{error}</small>}</label>;
}
function allowedTrigger(type:ArchitectureNode['data']['componentType'],trigger:string):boolean {
  if(type==='API') return trigger!=='SCHEDULED_TRIGGER';
  if(trigger==='SCHEDULED_TRIGGER') return ['SCHEDULER','WORKER','ORCHESTRATOR_SERVICE'].includes(type);
  if(trigger==='MESSAGE_CONSUMED') return ['SERVICE','WORKER','NOTIFICATION_SERVICE','ORCHESTRATOR_SERVICE','INTEGRATION_BUS'].includes(type);
  if(trigger==='WS_MESSAGE_RECEIVED'||trigger==='SESSION_OPENED') return type==='SERVICE';
  return type!=='WORKER';
}
function allowedKind(type:ArchitectureNode['data']['componentType'],trigger:string,kind:Kind):boolean {
  if(!allowedTrigger(type,trigger)) return false;
  if(type==='API') return ['PROCESS','CALL','PUBLISH','RESPOND','OPEN_SESSION','WS_SEND','CLOSE_SESSION','FORK','JOIN'].includes(kind);
  if(kind.startsWith('DB_')) return ['SERVICE','ORCHESTRATOR_SERVICE','WORKER','BFF','ETL_PIPELINE'].includes(type);
  if(kind.startsWith('ETL_')) return type==='ETL_PIPELINE' || kind==='ETL_TRANSFORM' && type==='DATA_TRANSFORMER';
  if(kind==='RESPOND' && trigger!=='REQUEST_RECEIVED') return false;
  if(kind==='SCHEDULE_TRIGGER') return type==='SCHEDULER';
  if(kind==='TRANSFORM') return type==='INTEGRATION_BUS';
  if(['OPEN_SESSION','WS_SEND','CLOSE_SESSION'].includes(kind)) return type==='SERVICE';
  if(type==='SERVICE'||type==='ORCHESTRATOR_SERVICE') return true;
  if(type==='BFF') return ['PROCESS','CALL','RESPOND','FORK','JOIN','BRANCH'].includes(kind) || Boolean(resourceTargets[kind]);
  if(type==='API_GATEWAY') return ['PROCESS','CALL','RESPOND','BRANCH'].includes(kind);
  if(type==='WORKER') return ['PROCESS','CALL','PUBLISH','FORK','JOIN','BRANCH'].includes(kind) || Boolean(resourceTargets[kind]);
  if(type==='SCHEDULER') return ['PROCESS','RESPOND'].includes(kind) || trigger==='SCHEDULED_TRIGGER' && ['CALL','PUBLISH'].includes(kind);
  if(type==='AUTHENTICATION_SERVICE') return ['PROCESS','EVALUATE_RULE','BRANCH','RESPOND'].includes(kind);
  if(type==='WEBHOOK_RECEIVER') return ['PROCESS','CALL','PUBLISH','RESPOND'].includes(kind);
  if(type==='INTEGRATION_BUS') return ['PROCESS','CALL','PUBLISH','RESPOND','BRANCH'].includes(kind);
  if(type==='NOTIFICATION_SERVICE') return ['PROCESS','RESPOND','PUBLISH'].includes(kind);
  return false;
}
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
    case 'BRANCH': return {id:actionId,kind,resultRef:'',cases:[{equals:'HIT',actionIds:[]},{equals:'MISS',actionIds:[]}]};
    case 'SCHEDULE_TRIGGER': return {id:actionId,kind,scheduleId:''};
    case 'TRANSFORM': return {id:actionId,kind,connectionId:connection,metadata:{}};
    case 'CACHE_GET': case 'CACHE_INVALIDATE': return {id:actionId,kind,connectionId:connection,key:''};
    case 'CACHE_PUT': return {id:actionId,kind,connectionId:connection,key:'',valueMetadata:'',ttlMs:0};
    case 'OBJECT_GET': case 'OBJECT_DELETE': return {id:actionId,kind,connectionId:connection,objectKey:''};
    case 'OBJECT_PUT': return {id:actionId,kind,connectionId:connection,objectKey:'',sizeBytes:0};
    case 'SEND_NOTIFICATION': return {id:actionId,kind,connectionId:connection,channel:'EMAIL',recipientAlias:'recipient',simulatedResult:'SENT'};
    case 'INDEX_DOCUMENT': return {id:actionId,kind,connectionId:connection,indexName:'index',documentId:'document'};
    case 'SEARCH_DOCUMENTS': return {id:actionId,kind,connectionId:connection,indexName:'index'};
    case 'REGISTER_SERVICE': return {id:actionId,kind,connectionId:connection,serviceName:'service',instanceId:'instance',targetComponentId:'',instanceStatus:'ACTIVE'};
    case 'UNREGISTER_SERVICE': return {id:actionId,kind,connectionId:connection,serviceName:'service',instanceId:'instance'};
    case 'DISCOVER_SERVICE': return {id:actionId,kind,connectionId:connection,serviceName:'service'};
    case 'EVALUATE_RULE': return {id:actionId,kind,connectionId:connection};
    case 'DB_SELECT': return {id:actionId,kind,connectionId:connection,collection:'default'};
    case 'DB_INSERT': case 'DB_UPDATE': case 'DB_DELETE': case 'DB_GET': case 'DB_PUT':
      return {id:actionId,kind,connectionId:connection,key:''};
    case 'DB_FIND': return {id:actionId,kind,connectionId:connection,field:'',equals:''};
    case 'DB_BULK_INSERT': return {id:actionId,kind,connectionId:connection,recordCount:1};
    case 'ETL_EXTRACT': return {id:actionId,kind,connectionId:connection,outputRef:id('batch')};
    case 'ETL_TRANSFORM': return {id:actionId,kind,inputRef:'',transform:'IDENTITY',connectionId:connection,simulateFailure:false};
    case 'ETL_LOAD': return {id:actionId,kind,connectionId:connection,inputRef:''};
    default: throw new Error(`Action ${kind} ainda não suportada pelo editor de comportamentos v0.5`);
  }
}
function compatible(kind: Kind, edge: ArchitectureEdge): boolean {
  const protocol=edge.data?.integration?.kind;
  if (kind==='CALL') return !!protocol && !['QUEUE','TOPIC','MQTT','WEBSOCKET'].includes(protocol);
  if (kind==='PUBLISH') return !!protocol && ['QUEUE','TOPIC','MQTT'].includes(protocol);
  if (kind==='OPEN_SESSION') return protocol==='WEBSOCKET';
  if (kind==='ETL_TRANSFORM') return !!protocol && !['QUEUE','TOPIC','MQTT','WEBSOCKET','DATABASE_QUERY'].includes(protocol);
  if (dataTargets[kind]) return protocol==='DATABASE_QUERY';
  return !!resourceTargets[kind] || kind==='TRANSFORM';
}
export function BehaviorEditorV41({node,nodes,edges,behaviors,onChange,disabled}: Props) {
  const outgoing=edges.filter(edge => edge.source===node.id);
  const changeBehavior=(index:number, behavior:BehaviorV41) => onChange(behaviors.map((item,i)=>i===index?behavior:item));
  const changeAction=(bi:number,ai:number,action:BehaviorActionV41) => changeBehavior(bi,{...behaviors[bi],actions:behaviors[bi].actions.map((item,i)=>i===ai?action:item)});
  return <section className="behavior-editor" aria-label="Comportamentos do serviço">
    <h3>Comportamentos</h3><p>Uma conexão só é acionada quando uma ação a utiliza.</p>
    {behaviors.map((behavior,bi) => <fieldset key={behavior.id} disabled={disabled}>
      <legend>{behavior.id}</legend>
      <label>ID do comportamento<input aria-label={`ID comportamento ${bi+1}`} value={behavior.id} onChange={e=>changeBehavior(bi,{...behavior,id:e.target.value})}/></label>
      <label>Gatilho<select aria-label={`Gatilho ${bi+1}`} value={behavior.trigger} onChange={e=>changeBehavior(bi,{...behavior,trigger:e.target.value as BehaviorV41['trigger'],match:undefined})}>
        {triggers.filter(trigger=>allowedTrigger(node.data.componentType,trigger)).map(trigger=><option key={trigger} value={trigger}>{trigger}</option>)}
      </select></label>
      {behavior.trigger==='REQUEST_RECEIVED' && <label>Operação correspondente<input aria-label={`Operação do gatilho ${bi+1}`} value={behavior.match?.operation ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{operation:e.target.value}:undefined})}/></label>}
      {behavior.trigger==='MESSAGE_CONSUMED' && <label>Destino correspondente<input aria-label={`Destino do gatilho ${bi+1}`} value={behavior.match?.destinationName ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{destinationName:e.target.value}:undefined})}/></label>}
      {(behavior.trigger==='WS_MESSAGE_RECEIVED'||behavior.trigger==='SESSION_OPENED') && <label>Caminho WebSocket<input aria-label={`Caminho do gatilho ${bi+1}`} value={behavior.match?.websocketPath ?? ''} placeholder="Opcional"
        onChange={e=>changeBehavior(bi,{...behavior,match:e.target.value?{websocketPath:e.target.value}:undefined})}/></label>}
      {behavior.actions.map((action,ai) => {
        const set=(patch:Record<string,unknown>)=>changeAction(bi,ai,{...action,...patch} as BehaviorActionV41);
        const choices=outgoing.filter(edge=>compatible(action.kind,edge) && (!resourceTargets[action.kind] || nodes.find(item=>item.id===edge.target)?.data.componentType===resourceTargets[action.kind]) && (!dataTargets[action.kind] || dataTargets[action.kind]?.includes(nodes.find(item=>item.id===edge.target)?.data.componentType ?? '')));
        return <fieldset key={action.id}><legend>Ação {ai+1}</legend>
          <label>ID da ação<input aria-label={`ID ação ${bi+1}.${ai+1}`} value={action.id} onChange={e=>set({id:e.target.value})}/></label>
          <label>Tipo<select aria-label={`Tipo da ação ${bi+1}.${ai+1}`} value={action.kind} onChange={e=>changeAction(bi,ai,defaults(e.target.value as Kind,outgoing.filter(edge=>compatible(e.target.value as Kind,edge) && (!resourceTargets[e.target.value as Kind] || nodes.find(item=>item.id===edge.target)?.data.componentType===resourceTargets[e.target.value as Kind]) && (!dataTargets[e.target.value as Kind] || dataTargets[e.target.value as Kind]?.includes(nodes.find(item=>item.id===edge.target)?.data.componentType ?? '')))))}>
            {kinds.filter(kind=>allowedKind(node.data.componentType,behavior.trigger,kind)).map(kind=><option key={kind} value={kind}>{kind}</option>)}
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
          {action.kind.startsWith('DB_') && <label>Timeout de dados (ms)<input type="number" min="0" aria-label={`Timeout de dados ${bi+1}.${ai+1}`} value={(action as {timeoutMs?:number}).timeoutMs ?? 0} onChange={e=>set({timeoutMs:Number(e.target.value)})}/></label>}
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
          {action.kind==='BRANCH' && <>
            <label>Resultado de referência<select aria-label={`Resultado do BRANCH ${bi+1}.${ai+1}`} value={action.resultRef} onChange={e=>set({resultRef:e.target.value})}><option value="">Selecione...</option>{behavior.actions.filter(item=>item.id!==action.id && 'outputRef' in item && item.outputRef).map(item=><option key={item.id} value={(item as {outputRef:string}).outputRef}>{(item as {outputRef:string}).outputRef}</option>)}</select></label>
            {action.cases.map((branch,index)=><div key={index}>
              <label>Resultado do caso {index+1}<input aria-label={`Resultado do caso ${index+1}`} value={branch.equals} onChange={e=>set({cases:action.cases.map((item,i)=>i===index?{...item,equals:e.target.value}:item)})}/></label>
              <label>Ações do caso {index+1}<select multiple aria-label={`Ações do caso ${index+1}`} value={branch.actionIds} onChange={e=>set({cases:action.cases.map((item,i)=>i===index?{...item,actionIds:Array.from(e.target.selectedOptions,option=>option.value)}:item)})}>{behavior.actions.filter(item=>item.id!==action.id).map(item=><option key={item.id} value={item.id}>{item.id}</option>)}</select></label>
              <button type="button" onClick={()=>set({cases:action.cases.filter((_,i)=>i!==index)})}>Remover caso</button>
            </div>)}
            <button type="button" onClick={()=>set({cases:[...action.cases,{equals:'',actionIds:[]}]})}>Adicionar caso</button>
            <label>Ações padrão<select multiple aria-label={`Ações padrão do BRANCH ${bi+1}.${ai+1}`} value={action.defaultActionIds ?? []} onChange={e=>set({defaultActionIds:Array.from(e.target.selectedOptions,option=>option.value)})}>{behavior.actions.filter(item=>item.id!==action.id).map(item=><option key={item.id} value={item.id}>{item.id}</option>)}</select></label>
          </>}
          {action.kind==='SCHEDULE_TRIGGER' && <label>Agendamento<select aria-label={`Agendamento ${bi+1}.${ai+1}`} value={action.scheduleId} onChange={e=>set({scheduleId:e.target.value})}><option value="">Selecione...</option>{(node.data.schedules ?? []).map(schedule=><option key={schedule.id} value={schedule.id}>{schedule.id}</option>)}</select></label>}
          {(actionFields[action.kind] ?? []).map(field=>{
            const value=(action as unknown as Record<string,unknown>)[field];
            if(field==='simulateFailure') return <label key={field}><input type="checkbox" aria-label={`Simular falha ${bi+1}.${ai+1}`} checked={Boolean(value)} onChange={e=>set({simulateFailure:e.target.checked})}/> Simular falha na transformação</label>;
            if (field==='channel' || field==='simulatedResult' || field==='instanceStatus') {
              const options=field==='channel'?['EMAIL','SMS','PUSH']:field==='simulatedResult'?['SENT','FAILED','TIMED_OUT']:['ACTIVE','INACTIVE'];
              return <label key={field}>{field}<select aria-label={`${field} ${bi+1}.${ai+1}`} value={String(value ?? options[0])} onChange={e=>set({[field]:e.target.value})}>{options.map(option=><option key={option}>{option}</option>)}</select></label>;
            }
            if(field==='targetComponentId') return <label key={field}>{field}<select aria-label={`${field} ${bi+1}.${ai+1}`} value={String(value ?? '')} onChange={e=>set({targetComponentId:e.target.value})}><option value="">Selecione...</option>{nodes.filter(item=>item.id!==node.id).map(item=><option key={item.id} value={item.id}>{item.data.label}</option>)}</select></label>;
            if(objectFields.has(field)) return <JsonMapField key={`${action.id}-${field}`} label={`${field} ${bi+1}.${ai+1}`} value={value} onChange={parsed=>set({[field]:parsed})}/>;
            return <label key={field}>{field}<input aria-label={`${field} ${bi+1}.${ai+1}`} type={numberFields.has(field)?'number':'text'} min={numberFields.has(field)?0:undefined} value={value === undefined?'':String(value)} onChange={e=>set({[field]:numberFields.has(field)?Number(e.target.value):e.target.value})}/></label>;
          })}
          <button type="button" onClick={()=>changeBehavior(bi,{...behavior,actions:behavior.actions.filter((_,i)=>i!==ai)})} disabled={behavior.actions.length===1}>Remover ação</button>
        </fieldset>;
      })}
      <button type="button" onClick={()=>changeBehavior(bi,{...behavior,actions:[...behavior.actions,defaults(node.data.componentType==='ETL_PIPELINE'?'ETL_EXTRACT':node.data.componentType==='DATA_TRANSFORMER'?'ETL_TRANSFORM':'PROCESS',outgoing)]})}>Adicionar ação</button>
      <button type="button" onClick={()=>onChange(behaviors.filter((_,i)=>i!==bi))}>Remover comportamento</button>
    </fieldset>)}
    <button type="button" disabled={disabled} onClick={()=>onChange([...behaviors,{id:id('behavior'),trigger:'REQUEST_RECEIVED',actions:[defaults(node.data.componentType==='ETL_PIPELINE'?'ETL_EXTRACT':node.data.componentType==='DATA_TRANSFORMER'?'ETL_TRANSFORM':'RESPOND',outgoing)]}])}>Adicionar comportamento</button>
  </section>;
}
