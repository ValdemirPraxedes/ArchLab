import { dataFieldErrorV6, type DataLimitV6 } from './dataLimitsV6';

/** Numeric data property bounded by the OpenAPI contract, with an accessible per-field error. */
export function DataNumberFieldV6({field,value,limit,disabled,onChange}:{
  field:string; value:number|undefined; limit:DataLimitV6; disabled:boolean; onChange:(value:number)=>void;
}) {
  const error=dataFieldErrorV6(field,value,limit);
  const errorId=`data-field-${field}-error`;
  return <label>{field}
    <input aria-label={field} type="number" min={limit.min} max={limit.max} step="1" disabled={disabled}
      value={value ?? ''} aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined}
      onChange={event=>onChange(Number(event.target.value))}/>
    {error && <span id={errorId} className="field-error" role="alert">{error}</span>}
  </label>;
}
