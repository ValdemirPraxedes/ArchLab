import { ScenarioPanelV3 } from './ScenarioPanelV3';
import { referenceProjectV3 } from './referenceV3';
import { useSimulationV3 } from './useSimulationV3';
import { ExecutionToolbarV3 } from './ExecutionToolbarV3';
import { SemanticPanelV3 } from './SemanticPanelV3';
import { ConnectionsPanelV3 } from './ConnectionsPanelV3';
import { connectV3 } from './graphModel';
import { lazy, Suspense, useReducer, useState } from 'react';
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
  const execution=useSimulationV3(project),locked=execution.locked;
  const dispatch = (action: ProjectAction) => {if(!locked)send({ type: 'commit', project: projectReducer(project, action) });};
  const [view, setView] = useState<'architecture'|'plans'|'scenario'>('architecture');
  const [selectedRelation, selectRelation] = useState<string | null>(null);
  const [selected, select] = useState<string | null>(null); const [feedback, setFeedback] = useState('');
  const component = project.request.architecture.components.find(c => c.id === selected);
  const add = (typeId: ComponentTypeV3, position?: { x: number; y: number }) => { const next = addComponentV3(project, typeId, position); dispatch({ type: 'replace', project: next }); select(next.request.architecture.components.at(-1)?.id ?? null); setFeedback('Componente adicionado.'); };
  return <section aria-label="Editor ArchLab v0.3" className="v3-workspace" aria-busy={execution.phase==='requesting'}>
    <header className="editor-toolbar"><h2>Projeto v0.3</h2><button disabled={locked} onClick={()=>{dispatch({type:'replace',project:referenceProjectV3()});select(null);selectRelation(null);setFeedback('CE-REF-01 carregado.');}}>Carregar CE-REF-01</button><button aria-pressed={view==='scenario'} onClick={()=>setView('scenario')}>Cenário</button><button aria-pressed={view==='architecture'} onClick={()=>setView('architecture')}>Arquitetura</button><button aria-pressed={view==='plans'} onClick={()=>setView('plans')}>ExecutionPlans</button><button disabled={locked||!history.past.length} onClick={() => send({ type: 'undo' })}>Desfazer</button><button disabled={locked||!history.future.length} onClick={() => send({ type: 'redo' })}>Refazer</button><label>Identificador da arquitetura <input disabled={locked} value={project.request.architecture.id} onChange={event => dispatch({ type: 'rename', name: event.target.value })} /></label></header><ExecutionToolbarV3 phase={execution.phase} run={()=>void execution.run()} cancel={execution.cancel} reset={execution.reset}/><p role="status" className="action-feedback">{execution.message}</p>{execution.error&&<p role="alert">{execution.error.message}</p>}{execution.received&&<p className="action-feedback">Principal: {execution.received.result.principal.status} · Global: {execution.received.result.global.status} · Resposta: {execution.received.result.principal.responseDurationMs??'—'} ms · Conclusão: {execution.received.result.global.completionDurationMs??'—'} ms</p>}
    <div className="editor-layout"><aside className="palette" aria-label="Componentes v3"><h2>Componentes</h2><p>Arraste ou clique para adicionar.</p>{COMPONENTS_V3.map(item => <button className="palette-item" key={item.typeId} disabled={locked} draggable={!locked} onDragStart={event => event.dataTransfer.setData(DRAG_V3, item.typeId)} onClick={() => add(item.typeId)}><ComponentIconV3 typeId={item.typeId} /> {item.label}</button>)}</aside>
      <div className="editor-main">{view==='scenario'?<ScenarioPanelV3 locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})} />:view==='plans'?<Suspense fallback={<p role="status">Carregando editor de planos…</p>}><PlanEditorV3 locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})}/></Suspense>:<CanvasV3 locked={locked} project={project} selected={selected} select={select} selectRelation={selectRelation} connect={(source,target)=>{try{dispatch({type:'replace',project:connectV3(project,source,target)});setFeedback('Conexão criada.');}catch(e){setFeedback(e instanceof Error?e.message:'Conexão inválida.');}}} add={add} move={(id, position) => dispatch({ type: 'replace', project: moveComponentV3(project, id, position) })} />}<p role="status" className="action-feedback">{feedback || 'Selecione um componente para configurar.'}</p><ul className="v3-node-list" aria-label="Componentes no projeto">{project.request.architecture.components.map(c => <li key={c.id}><button aria-pressed={selected === c.id} onClick={() => select(c.id)}>{c.displayName}</button></li>)}</ul></div>
      <aside className="properties" aria-label="Propriedades v3"><h2>Propriedades</h2>{component ? <><h3>{component.displayName}</h3><PropertiesPanelV3 locked={locked} key={component.id + '-' + component.displayName} project={project} component={component} change={next => dispatch({ type: 'replace', project: next })} /><SemanticPanelV3 locked={locked} project={project} component={component} change={next=>dispatch({type:'replace',project:next})}/><p>{component.typeId}</p><button className="danger" disabled={locked} onClick={() => { try { dispatch({ type: 'replace', project: removeComponentV3(project, component.id) }); select(null); setFeedback('Componente removido.'); } catch (error) { setFeedback(error instanceof Error ? error.message : 'Não foi possível remover.'); } }}><Trash2 size={16} /> Remover componente</button></> : <p>Nenhum componente selecionado.</p>}<ConnectionsPanelV3 locked={locked} project={project} change={next=>dispatch({type:'replace',project:next})} selected={selectedRelation} select={selectRelation}/></aside>
    </div>
  </section>;
}
