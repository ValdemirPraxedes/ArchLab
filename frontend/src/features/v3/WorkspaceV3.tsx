import { PlaybackV3 } from './PlaybackV3';
import type { ProjectionV3 } from './PlaybackV3';
import { ArchlabV3HttpError } from '../../api/archlabV3Client';
import { problemDiagnosticsV3,requestKeyV3 } from './validationV3';
import { useValidationV3 } from './useValidationV3';
import { DiagnosticsPanelV3 } from './DiagnosticsPanelV3';
import { diagnosticTargetV3 } from './validationV3';
import type { DiagnosticV3 } from './validationV3';
import { ScenarioPanelV3 } from './ScenarioPanelV3';
import { referenceProjectV3 } from './referenceV3';
import { useSimulationV3 } from './useSimulationV3';
import { ExecutionToolbarV3 } from './ExecutionToolbarV3';
import { SemanticPanelV3 } from './SemanticPanelV3';
import { ConnectionsPanelV3 } from './ConnectionsPanelV3';
import { connectV3 } from './graphModel';
import { lazy, Suspense, useReducer, useRef, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { newProjectV3, projectReducer } from './project';
import type { ProjectAction } from './project';
import { historyReducerV3, historyV3 } from './history';
import { PropertiesPanelV3 } from './PropertiesPanelV3';
import { addComponentV3, moveComponentV3, removeComponentV3 } from './components';
import { COMPONENTS_V3 } from './catalog';
import { ComponentIconV3 } from './ComponentIconV3';
import { CanvasV3, DRAG_V3 } from './CanvasV3';
import type { ComponentTypeV3 } from './catalog';
const PlanEditorV3 = lazy(() => import('./PlanEditorV3').then(module => ({ default: module.PlanEditorV3 })));
export default function WorkspaceV3() {
  const [history, send] = useReducer(historyReducerV3, undefined, () => historyV3(newProjectV3()));
  const project = history.present;
  const validation=useValidationV3(project);
  const execution=useSimulationV3(project,validation.validateRequest),locked=execution.locked||validation.busy;
  const dispatch = (action: ProjectAction) => {if(!locked)send({ type: 'commit', project: projectReducer(project, action) });};
  const [projection,setProjection]=useState<ProjectionV3|null>(null);
  const activeProjection=projection?.result===execution.received?.result?projection:null;
  const [view, setView] = useState<'architecture'|'plans'|'scenario'>('architecture');
  const workspaceRef=useRef<HTMLElement>(null);
  const propertiesRef=useRef<HTMLElement>(null);
  const [planFocus,setPlanFocus]=useState<{planId?:string;stepId?:string;token:number}>({token:0});
  const [validationPlan,setValidationPlan]=useState('');
  const [selectedRelation, selectRelation] = useState<string | null>(null);
  const [selected, select] = useState<string | null>(null); const [feedback, setFeedback] = useState('');
  const component = project.request.architecture.components.find(c => c.id === selected);
  const add = (typeId: ComponentTypeV3, position?: { x: number; y: number }) => { const next = addComponentV3(project, typeId, position); dispatch({ type: 'replace', project: next }); select(next.request.architecture.components.at(-1)?.id ?? null); setFeedback('Componente adicionado.'); };
  const locate=(diagnostic:DiagnosticV3)=>{const target=diagnosticTargetV3(project.request,diagnostic);setView(target.view);if(target.componentId)select(target.componentId);if(target.relationId)selectRelation(target.relationId);if(target.planId)setPlanFocus(previous=>({planId:target.planId,stepId:target.stepId,token:previous.token+1}));setFeedback('Diagnóstico: '+diagnostic.violation.path);requestAnimationFrame(()=>{const matches=Array.from(workspaceRef.current?.querySelectorAll<HTMLElement>('[data-v3-path]')??[]).filter(element=>{const path=element.dataset.v3Path;return path&&(diagnostic.violation.path===path||diagnostic.violation.path.startsWith(path+'/'));}).sort((a,b)=>(b.dataset.v3Path?.length??0)-(a.dataset.v3Path?.length??0));const input=matches[0]?.querySelector<HTMLElement>('input:not(:disabled),select:not(:disabled),textarea:not(:disabled),button:not(:disabled)');if(input)input.focus();else propertiesRef.current?.focus();});};
  const invalidIds=new Set(validation.current?(validation.report?.diagnostics.flatMap(d=>{const id=diagnosticTargetV3(project.request,d).componentId;return id?[id]:[];})??[]):[]);
  return <section ref={workspaceRef} aria-label="Editor ArchLab v0.3" className="v3-workspace" aria-busy={execution.phase==='requesting'||validation.busy}>
    <header className="editor-toolbar"><h2>Projeto v0.3</h2><button disabled={locked} onClick={()=>{dispatch({type:'replace',project:referenceProjectV3()});select(null);selectRelation(null);setFeedback('CE-REF-01 carregado.');}}>Carregar CE-REF-01</button><button aria-pressed={view==='scenario'} onClick={()=>setView('scenario')}>Cenário</button><button aria-pressed={view==='architecture'} onClick={()=>setView('architecture')}>Arquitetura</button><button aria-pressed={view==='plans'} onClick={()=>setView('plans')}>ExecutionPlans</button><button disabled={locked||!history.past.length} onClick={() => send({ type: 'undo' })}>Desfazer</button><button disabled={locked||!history.future.length} onClick={() => send({ type: 'redo' })}>Refazer</button><label>Identificador da arquitetura <input disabled={locked} value={project.request.architecture.id} onChange={event => dispatch({ type: 'rename', name: event.target.value })} /></label></header><div className="editor-toolbar" role="group" aria-label="Validações v3"><button disabled={locked} onClick={()=>void validation.validate('architecture')}>Validar arquitetura v3</button><label>Plano para validação<select value={validationPlan||project.request.architecture.executionPlans[0]?.id||''} onChange={e=>setValidationPlan(e.target.value)} disabled={locked}>{project.request.architecture.executionPlans.map(p=><option key={p.id} value={p.id}>{p.id}</option>)}</select></label><button disabled={locked||!project.request.architecture.executionPlans.length} onClick={()=>void validation.validate('plan',validationPlan||undefined)}>Validar plano v3</button><button disabled={locked} onClick={()=>void validation.validate('scenario')}>Validar cenário v3</button></div><DiagnosticsPanelV3 report={validation.report} current={validation.current} locate={locate}/>{execution.error instanceof ArchlabV3HttpError&&execution.requested&&<DiagnosticsPanelV3 report={{requestKey:requestKeyV3(execution.requested),valid:false,diagnostics:problemDiagnosticsV3(execution.requested,execution.error,'scenario')}} current={requestKeyV3(execution.requested)===requestKeyV3(project.request)} locate={locate}/>}<ExecutionToolbarV3 phase={execution.phase} run={()=>void execution.run()} cancel={execution.cancel} reset={execution.reset}/><p role="status" className="action-feedback">{execution.message}</p>{execution.error&&<p role="alert">{execution.error.message}</p>}{execution.received&&<p className="action-feedback">Resultado recebido até {execution.received.result.global.observedUntilMs} ms · Principal: {execution.received.result.principal.status} · Global: {execution.received.result.global.status} · Resposta: {execution.received.result.principal.responseDurationMs??'—'} ms · Conclusão: {execution.received.result.global.completionDurationMs??'—'} ms</p>}
    <div className="editor-layout"><aside className="palette" aria-label="Componentes v3"><h2>Componentes</h2><p>Arraste ou clique para adicionar.</p>{COMPONENTS_V3.map(item => <button className="palette-item" key={item.typeId} disabled={locked} draggable={!locked} onDragStart={event => event.dataTransfer.setData(DRAG_V3, item.typeId)} onClick={() => add(item.typeId)}><ComponentIconV3 typeId={item.typeId} /> {item.label}</button>)}</aside>
      <div className="editor-main">{view==='scenario'?<ScenarioPanelV3 locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})} />:view==='plans'?<Suspense fallback={<p role="status">Carregando editor de planos…</p>}><PlanEditorV3 key={planFocus.token} initialPlanId={planFocus.planId} initialStepId={planFocus.stepId} locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})}/></Suspense>:<CanvasV3 snapshot={activeProjection?.snapshot} invalidIds={invalidIds} locked={locked} project={project} selected={selected} select={select} selectRelation={selectRelation} connect={(source,target)=>{try{dispatch({type:'replace',project:connectV3(project,source,target)});setFeedback('Conexão criada.');}catch(e){setFeedback(e instanceof Error?e.message:'Conexão inválida.');}}} add={add} move={(id, position) => dispatch({ type: 'replace', project: moveComponentV3(project, id, position) })} />}<p role="status" className="action-feedback">{feedback || 'Selecione um componente para configurar.'}</p><ul className="v3-node-list" aria-label="Componentes no projeto">{project.request.architecture.components.map(c => <li key={c.id}><button aria-pressed={selected === c.id} onClick={() => select(c.id)}>{c.displayName}</button></li>)}</ul></div>
      <aside ref={propertiesRef} tabIndex={-1} className="properties" aria-label="Propriedades v3"><h2>Propriedades</h2>{component ? <><h3>{component.displayName}</h3><PropertiesPanelV3 locked={locked} key={component.id + '-' + component.displayName} project={project} component={component} change={next => dispatch({ type: 'replace', project: next })} /><SemanticPanelV3 locked={locked} project={project} component={component} change={next=>dispatch({type:'replace',project:next})}/><p>{component.typeId}</p><button className="danger" disabled={locked} onClick={() => { try { dispatch({ type: 'replace', project: removeComponentV3(project, component.id) }); select(null); setFeedback('Componente removido.'); } catch (error) { setFeedback(error instanceof Error ? error.message : 'Não foi possível remover.'); } }}><Trash2 size={16} /> Remover componente</button></> : <p>Nenhum componente selecionado.</p>}<ConnectionsPanelV3 locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})} selected={selectedRelation} select={selectRelation}/></aside>
    </div>
    {execution.received&&<PlaybackV3 key={execution.received.result.metadata.executionId+'-'+execution.received.result.integrity.normalizedResultDigest} request={execution.received.request} result={execution.received.result} project={setProjection}/>}
  </section>;
}
