import { useReducer } from 'react';
import { newProjectV3, projectReducer } from './project';
export default function WorkspaceV3() {
  const [project, dispatch] = useReducer(projectReducer, undefined, newProjectV3);
  return <section aria-label="Editor ArchLab v0.3" className="v3-workspace">
    <header className="editor-toolbar"><h2>Projeto v0.3</h2><label>Identificador da arquitetura <input value={project.request.architecture.id} onChange={event => dispatch({ type: 'rename', name: event.target.value })} /></label></header>
    <p role="status">Projeto v3 independente. Contrato API v3 integrado.</p>
  </section>;
}
