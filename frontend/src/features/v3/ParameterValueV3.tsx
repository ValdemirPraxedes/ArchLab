import { useState } from 'react';
import type { PropertyV3 } from './types';
import { decodePropertyV3 } from './propertyModel';
export function ParameterValueV3({value,change}:{value:PropertyV3;change:(value:PropertyV3)=>void}) {
  const [draft,setDraft]=useState(JSON.stringify(value,null,2)),[error,setError]=useState('');
  const defaults:Record<PropertyV3['kind'],PropertyV3>={STRING:{kind:'STRING',value:''},INTEGER:{kind:'INTEGER',value:0},DECIMAL:{kind:'DECIMAL',value:'0.0'},BOOLEAN:{kind:'BOOLEAN',value:true},ENUM:{kind:'ENUM',value:''},REFERENCE:{kind:'REFERENCE',targetId:'id',expectedTypeId:'ENDPOINT'},LIST:{kind:'LIST',values:[]},OBJECT:{kind:'OBJECT',fields:{}}};
  return <div><label>Tipo do parâmetro<select value={value.kind} onChange={e=>{change(defaults[e.target.value as PropertyV3['kind']]);setError('');}}>{Object.keys(defaults).map(kind=><option key={kind}>{kind}</option>)}</select></label>
    {value.kind==='BOOLEAN'?<label><input type="checkbox" checked={value.value} onChange={e=>change({...value,value:e.target.checked})}/>Valor booleano</label>:value.kind==='LIST'||value.kind==='OBJECT'||value.kind==='REFERENCE'?<><label>Valor tipado JSON<textarea rows={8} value={draft} onChange={e=>setDraft(e.target.value)} spellCheck={false}/></label><button onClick={()=>{try{const parsed:unknown=JSON.parse(draft);change(decodePropertyV3(parsed));setError('');}catch(e){setError(e instanceof Error?e.message:'JSON inválido.');}}}>Aplicar valor tipado</button><p>Somente dados JSON tipados. Nenhum código é executado.</p></>:<label>Valor do parâmetro<input defaultValue={String(value.value)} onBlur={e=>{try{const candidate={...value,value:value.kind==='INTEGER'?Number(e.target.value):e.target.value};if(value.kind==='INTEGER'&&!e.target.value.trim())throw new Error('Inteiro obrigatório.');change(decodePropertyV3(candidate));setError('');}catch(e){setError(e instanceof Error?e.message:'Valor inválido.');}}}/></label>}{error&&<p role="alert">{error}</p>}
  </div>;
}
