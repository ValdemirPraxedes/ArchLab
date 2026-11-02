import { useReducer, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { newProjectV3, projectReducer } from './project';
import { addComponentV3, moveComponentV3, removeComponentV3 } from './components';
import { COMPONENTS_V3 } from './catalog';
import { ComponentIconV3 } from './ComponentIconV3';
import { CanvasV3, DRAG_V3 } from './CanvasV3';
import type { ComponentTypeV3 } from './catalog';
export default function WorkspaceV3() {
  const [project, dispatch] = useReducer(projectReducer, undefined, newProjectV3);
  const [selected, select] = useState<string | null>(null); const [feedback, setFeedback] = useState('');
  const component = project.request.architecture.components.find(c => c.id === selected);
  const add = (typeId: ComponentTypeV3, position?: { x: number; y: number }) => { const next = addComponentV3(project, typeId, position); dispatch({ type: 'replace', project: next }); select(next.request.architecture.components.at(-1)?.id ?? null); setFeedback('Componente adicionado.'); };
  return <section aria-label="Editor ArchLab v0.3" className="v3-workspace">
    <header className="editor-toolbar"><h2>Projeto v0.3</h2><label>Identificador da arquitetura <input value={project.request.architecture.id} onChange={event => dispatch({ type: 'rename', name: event.target.value })} /></label></header>
    <div className="editor-layout"><aside className="palette" aria-label="Componentes v3"><h2>Componentes</h2><p>Arraste ou clique para adicionar.</p>{COMPONENTS_V3.map(item => <button className="palette-item" key={item.typeId} draggable onDragStart={event => event.dataTransfer.setData(DRAG_V3, item.typeId)} onClick={() => add(item.typeId)}><ComponentIconV3 typeId={item.typeId} /> {item.label}</button>)}</aside>
      <div className="editor-main"><CanvasV3 project={project} selected={selected} select={select} add={add} move={(id, position) => dispatch({ type: 'replace', project: moveComponentV3(project, id, position) })} /><p role="status" className="action-feedback">{feedback || 'Selecione um componente para configurar.'}</p><ul className="v3-node-list" aria-label="Componentes no projeto">{project.request.architecture.components.map(c => <li key={c.id}><button aria-pressed={selected === c.id} onClick={() => select(c.id)}>{c.displayName}</button></li>)}</ul></div>
      <aside className="properties" aria-label="Propriedades v3"><h2>Propriedades</h2>{component ? <><h3>{component.displayName}</h3><p>ID estável: {component.id}</p><p>{component.typeId}</p><button className="danger" onClick={() => { try { dispatch({ type: 'replace', project: removeComponentV3(project, component.id) }); select(null); setFeedback('Componente removido.'); } catch (error) { setFeedback(error instanceof Error ? error.message : 'Não foi possível remover.'); } }}><Trash2 size={16} /> Remover componente</button></> : <p>Nenhum componente selecionado.</p>}</aside>
    </div>
  </section>;
}
