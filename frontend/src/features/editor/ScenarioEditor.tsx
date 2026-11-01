import type { ArchitectureNode, ArchitectureEdge } from './model';
import type { ScenarioDraft } from './scenario';
import { reconcileScenario, scenarioIssues } from './scenario';

type Props = { nodes: ArchitectureNode[]; edges: ArchitectureEdge[]; value: ScenarioDraft; onChange: (value: ScenarioDraft) => void; disabled: boolean };
export function ScenarioEditor({ nodes, edges, value, onChange, disabled }: Props) {
  const scenario = reconcileScenario(value, nodes, edges);
  const clients = nodes.filter(n => n.data.componentType === 'CLIENT');
  const entryConnections = edges.filter(e => e.source === scenario.entryComponentId && nodes.some(n => n.id === e.target && n.data.componentType === 'API'));
  const visited = new Set<string>();
  let target = edges.find(e => e.id === scenario.entryConnectionId)?.target;
  while (target && !visited.has(target)) {
    visited.add(target);
    const nextId = scenario.apiExecutions.find(e => e.componentId === target)?.dependencyConnectionId;
    target = nextId ? edges.find(e => e.id === nextId && e.source === target)?.target : undefined;
  }
  const issues = scenarioIssues(scenario, nodes, edges);
  return <section className="scenario-editor" aria-label="Editor de cenário">
    <h3>Cenário de execução</h3>
    <p>Escolha explicitamente o caminho. APIs sem dependência respondem diretamente.</p>
    <label>Client inicial
      <select aria-label="Client inicial" disabled={disabled} value={scenario.entryComponentId}
        onChange={e => onChange({ entryComponentId: e.target.value, entryConnectionId: '', apiExecutions: [] })}>
        <option value="">Selecione...</option>
        {clients.map(n => <option key={n.id} value={n.id}>{n.data.displayName ?? n.data.label} · {n.id.slice(0, 8)}</option>)}
      </select>
    </label>
    <label>Conexão de entrada
      <select aria-label="Conexão de entrada" disabled={disabled || !scenario.entryComponentId} value={scenario.entryConnectionId}
        onChange={e => onChange({ ...scenario, entryConnectionId: e.target.value, apiExecutions: [] })}>
        <option value="">Selecione...</option>
        {entryConnections.map(e => <option key={e.id} value={e.id}>{nodes.find(n => n.id === e.target)?.data.displayName} · {e.id.slice(0, 8)}</option>)}
      </select>
    </label>
    {nodes.filter(n => n.data.componentType === 'API').map(api => {
      const active = visited.has(api.id);
      const options = edges.filter(e => e.source === api.id && nodes.some(n => n.id === e.target && ['API', 'DATABASE'].includes(n.data.componentType)));
      return <label key={api.id}>Dependência de {api.data.displayName ?? api.data.label}{!active && ' (inativa)'}
        <select aria-label={`Dependência de ${api.data.displayName ?? api.data.label}`} disabled={disabled || !active} value={scenario.apiExecutions.find(e => e.componentId === api.id)?.dependencyConnectionId ?? ''}
          onChange={e => onChange(reconcileScenario({ ...scenario, apiExecutions: [...scenario.apiExecutions.filter(item => item.componentId !== api.id), ...(e.target.value ? [{ componentId: api.id, dependencyConnectionId: e.target.value }] : [])] }, nodes, edges))}>
          <option value="">Responder diretamente</option>
          {options.map(edge => <option key={edge.id} value={edge.id}>{nodes.find(n => n.id === edge.target)?.data.displayName} · {edge.id.slice(0, 8)}</option>)}
        </select>
      </label>;
    })}
    <p aria-live="polite">{issues.length ? issues.join(' ') : 'Caminho selecionado localmente. Use Simular para executar no backend v2.'}</p>
  </section>;
}
