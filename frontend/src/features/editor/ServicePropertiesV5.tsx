import type { ArchitectureEdge, ArchitectureNode } from './model';

type Props={node:ArchitectureNode;edges:ArchitectureEdge[];disabled:boolean;onChange:(patch:Partial<ArchitectureNode['data']>)=>void};
function expectedValue(text:string,operator:string):string|number|(string|number|boolean)[] {
  if(operator==='IN') return text.split(',').map(value=>value.trim()).filter(Boolean);
  if(['GT','GTE','LT','LTE'].includes(operator)) return Number(text);
  return text;
}
export function ServicePropertiesV5({node,edges,disabled,onChange}:Props) {
  const data=node.data;
  return <section aria-label="Configuração especializada">
    {data.componentType==='CACHE' && <label>TTL padrão (ms)<input type="number" min="0" aria-label="TTL padrão (ms)" disabled={disabled} value={data.defaultTtlMs ?? 0} onChange={event=>onChange({defaultTtlMs:Number(event.target.value)})}/></label>}
    {data.componentType==='API_GATEWAY' && <fieldset disabled={disabled}><legend>Rotas explícitas</legend>
      {(data.routes ?? []).map((route,index)=><div key={index}>
        <label>Operação {index+1}<input aria-label={`Operação da rota ${index+1}`} value={route.operation} onChange={event=>onChange({routes:data.routes?.map((item,i)=>i===index?{...item,operation:event.target.value}:item)})}/></label>
        <label>Conexão {index+1}<select aria-label={`Conexão da rota ${index+1}`} value={route.connectionId} onChange={event=>onChange({routes:data.routes?.map((item,i)=>i===index?{...item,connectionId:event.target.value}:item)})}><option value="">Selecione...</option>{edges.filter(edge=>edge.source===node.id).map(edge=><option key={edge.id} value={edge.id}>{edge.id}</option>)}</select></label>
        <button type="button" onClick={()=>onChange({routes:data.routes?.filter((_,i)=>i!==index)})}>Remover rota</button>
      </div>)}
      <button type="button" onClick={()=>onChange({routes:[...(data.routes ?? []),{operation:'GET /',connectionId:''}]})}>Adicionar rota</button>
    </fieldset>}
    {data.componentType==='SCHEDULER' && <fieldset disabled={disabled}><legend>Agendamentos virtuais</legend>
      {(data.schedules ?? []).map((schedule,index)=><div key={index}>
        <label>ID {index+1}<input aria-label={`ID do agendamento ${index+1}`} value={schedule.id} onChange={event=>onChange({schedules:data.schedules?.map((item,i)=>i===index?{...item,id:event.target.value}:item)})}/></label>
        {(['firstAtMs','intervalMs','occurrenceCount'] as const).map(field=><label key={field}>{field}<input type="number" min="0" aria-label={`${field} ${index+1}`} value={schedule[field]} onChange={event=>onChange({schedules:data.schedules?.map((item,i)=>i===index?{...item,[field]:Number(event.target.value)}:item)})}/></label>)}
        <label>Behavior {index+1}<select aria-label={`Behavior do agendamento ${index+1}`} value={schedule.behaviorId ?? ''} onChange={event=>onChange({schedules:data.schedules?.map((item,i)=>i===index?{...item,behaviorId:event.target.value || undefined}:item)})}><option value="">Selecione...</option>{(data.behaviors ?? []).filter(behavior=>behavior.trigger==='SCHEDULED_TRIGGER').map(behavior=><option key={behavior.id} value={behavior.id}>{behavior.id}</option>)}</select></label>
        <button type="button" onClick={()=>onChange({schedules:data.schedules?.filter((_,i)=>i!==index)})}>Remover agendamento</button>
      </div>)}
      <button type="button" onClick={()=>onChange({schedules:[...(data.schedules ?? []),{id:`schedule-${crypto.randomUUID().slice(0,8)}`,firstAtMs:0,intervalMs:1000,occurrenceCount:1}]})}>Adicionar agendamento</button>
    </fieldset>}
    {data.componentType==='RULES_ENGINE' && <fieldset disabled={disabled}><legend>Regras declarativas</legend>
      <label>Decisão padrão<select aria-label="Decisão padrão" value={data.defaultDecision ?? 'DENY'} onChange={event=>onChange({defaultDecision:event.target.value as 'ALLOW'|'DENY'|'REVIEW'})}>{['ALLOW','DENY','REVIEW'].map(value=><option key={value}>{value}</option>)}</select></label>
      {(data.rules ?? []).map((rule,index)=><div key={index}>
        {(['id','field'] as const).map(field=><label key={field}>{field}<input aria-label={`${field} da regra ${index+1}`} value={rule[field]} onChange={event=>onChange({rules:data.rules?.map((item,i)=>i===index?{...item,[field]:event.target.value}:item)})}/></label>)}
        <label>Operador<select aria-label={`Operador da regra ${index+1}`} value={rule.operator} onChange={event=>onChange({rules:data.rules?.map((item,i)=>i===index?{...item,operator:event.target.value as typeof rule.operator}:item)})}>{['EQ','NE','GT','GTE','LT','LTE','IN'].map(value=><option key={value}>{value}</option>)}</select></label>
        <label>Valor esperado<input aria-label={`Valor esperado ${index+1}`} value={Array.isArray(rule.expectedValue)?rule.expectedValue.join(', '):String(rule.expectedValue)} onChange={event=>onChange({rules:data.rules?.map((item,i)=>i===index?{...item,expectedValue:expectedValue(event.target.value,item.operator)}:item)})}/></label>
        <label>Prioridade<input type="number" min="0" max="10000" aria-label={`Prioridade ${index+1}`} value={rule.priority} onChange={event=>onChange({rules:data.rules?.map((item,i)=>i===index?{...item,priority:Number(event.target.value)}:item)})}/></label>
        <label>Decisão<select aria-label={`Decisão da regra ${index+1}`} value={rule.decision} onChange={event=>onChange({rules:data.rules?.map((item,i)=>i===index?{...item,decision:event.target.value as typeof rule.decision}:item)})}>{['ALLOW','DENY','REVIEW'].map(value=><option key={value}>{value}</option>)}</select></label>
        <button type="button" onClick={()=>onChange({rules:data.rules?.filter((_,i)=>i!==index)})}>Remover regra</button>
      </div>)}
      <button type="button" onClick={()=>onChange({rules:[...(data.rules ?? []),{id:`rule-${crypto.randomUUID().slice(0,8)}`,field:'',operator:'EQ',expectedValue:'',priority:0,decision:'DENY'}]})}>Adicionar regra</button>
    </fieldset>}
  </section>;
}
