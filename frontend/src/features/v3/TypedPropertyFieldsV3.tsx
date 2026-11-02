import { useId, useState } from 'react';
import type { PropertiesV3, PropertyV3 } from './types';
import type { RuleV3 } from './propertyModel';
import { setPropertyV3 } from './propertyModel';
interface ReferenceOption { id: string; label: string; typeId: string }
function NumberField({ label, value, rule, change, disabled }: { label: string; value: number; rule: RuleV3; change: (value: number) => void; disabled: boolean }) {
  const [draft, setDraft] = useState(String(value)), [error, setError] = useState(''); const id = useId();
  return <div><label htmlFor={id}>{label}</label><input id={id} type="number" step="1" min={rule.min ?? 0} max={rule.max ?? 1e12} disabled={disabled} value={draft} aria-invalid={!!error} aria-describedby={error ? id + '-error' : undefined} onChange={e => { setDraft(e.target.value); setError(''); }} onBlur={() => { const number = Number(draft); if (!draft.trim() || !Number.isSafeInteger(number) || number < (rule.min ?? 0) || number > (rule.max ?? 1e12)) setError('Informe um inteiro entre ' + (rule.min ?? 0) + ' e ' + (rule.max ?? 1e12) + '.'); else change(number); }} />{error && <p id={id + '-error'} role="alert">{error}</p>}</div>;
}
export function TypedPropertyFieldsV3({ properties, rules, options, change, disabled = false }: { properties: PropertiesV3; rules: Record<string, RuleV3>; options: ReferenceOption[]; change: (value: PropertiesV3) => void; disabled?: boolean }) {
  const apply = (name: string, value?: PropertyV3) => change(setPropertyV3(properties, name, value));
  return <>{Object.entries(rules).map(([name, rule]) => { const value = properties[name]; const targets = options.filter(o => o.typeId === rule.expectedTypeId);
    return <fieldset key={name} disabled={disabled}><legend>{name}</legend><label><input type="checkbox" checked={value !== undefined} onChange={e => apply(name, !e.target.checked ? undefined : rule.kind === 'BOOLEAN' ? { kind: 'BOOLEAN', value: true } : rule.kind === 'INTEGER' ? { kind: 'INTEGER', value: rule.min ?? 0 } : rule.kind === 'LIST' ? { kind: 'LIST', values: [] } : { kind: 'REFERENCE', targetId: targets[0]?.id ?? '', expectedTypeId: rule.expectedTypeId ?? 'ENDPOINT' })} /> Configurar {name}</label>
      {value?.kind === 'INTEGER' && <NumberField key={String(value.value)} label={name} value={value.value} rule={rule} change={n => apply(name, { kind: 'INTEGER', value: n })} disabled={disabled} />}
      {value?.kind === 'BOOLEAN' && <label><input type="checkbox" checked={value.value} onChange={e => apply(name, { kind: 'BOOLEAN', value: e.target.checked })} /> {name}</label>}
      {value?.kind === 'REFERENCE' && <label>{name}<select value={value.targetId} onChange={e => apply(name, { ...value, targetId: e.target.value })}><option value="">Selecione uma referência</option>{targets.map(o => <option key={o.id} value={o.id}>{o.label} ({o.id})</option>)}</select></label>}
      {value?.kind === 'LIST' && <label>{name}<select multiple value={value.values.flatMap(v => v.kind === 'REFERENCE' ? [v.targetId] : [])} onChange={e => apply(name, { kind: 'LIST', values: Array.from(e.target.selectedOptions).map(o => ({ kind: 'REFERENCE', targetId: o.value, expectedTypeId: rule.expectedTypeId ?? 'MESSAGE_QUEUE' })) })}>{targets.map(o => <option key={o.id} value={o.id}>{o.label} ({o.id})</option>)}</select></label>}
      {rule.kind === 'REFERENCE' && !targets.length && <p>Configure primeiro um {rule.expectedTypeId} compatível.</p>}
    </fieldset>;
  })}{Object.keys(properties).filter(name => !rules[name]).map(name => <p role="alert" key={name}>Propriedade não suportada pelo catálogo: {name}. O valor foi preservado para diagnóstico.</p>)}</>;
}
