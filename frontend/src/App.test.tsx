import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';
import { http, HttpResponse } from 'msw';
import { server } from './test/server';

vi.mock('@xyflow/react', async () => {
  const React = await import('react');
  return {
    ReactFlowProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    ReactFlow: ({ nodes, edges, onNodeClick, onEdgeClick, onNodesChange }: {
      nodes: { id: string; position: { x: number; y: number }; data: { label: string; componentType: string } }[];
      edges: { id: string; label?: React.ReactNode }[];
      onNodeClick: (event: unknown, node: unknown) => void;
      onEdgeClick: (event: unknown, edge: unknown) => void;
      onNodesChange: (changes: unknown[]) => void;
    }) => (
      <div data-testid="flow">
        {nodes.map((node) => <React.Fragment key={node.id}><button data-position={`${node.position.x},${node.position.y}`} data-component-type={node.data.componentType} onClick={() => onNodeClick(null, node)}>Nó: {node.data.label}</button><button onClick={() => onNodesChange([{ id: node.id, type: 'position', position: { x: 77, y: 88 }, dragging: false }])}>Mover: {node.data.label}</button></React.Fragment>)}
        {edges.map((edge) => <button key={edge.id} onClick={() => onEdgeClick(null, edge)}>Conexão: {edge.label}</button>)}
      </div>
    ),
    Background: () => null, Controls: () => null, MiniMap: () => null,
    Handle: () => null, Position: { Left: 'left', Right: 'right' },
    useReactFlow: () => ({ screenToFlowPosition: (position: { x: number; y: number }) => position }),
    useNodesState: (initial: unknown[]) => {
      const [nodes, setNodes] = React.useState(initial as { id: string; position: { x: number; y: number } }[]);
      return [nodes, setNodes, (changes: { id: string; type: string; position?: { x: number; y: number } }[]) => setNodes((current: { id: string; position: { x: number; y: number } }[]) => current.map((node) => {
        const change = changes.find((item) => item.id === node.id && item.type === 'position');
        return change?.position ? { ...node, position: change.position } : node;
      }))];
    },
    useEdgesState: (initial: unknown[]) => {
      const [edges, setEdges] = React.useState(initial);
      return [edges, setEdges, () => undefined];
    },
  };
});

describe('Editor visual', () => {
  it('exibe paleta, canvas e propriedades', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Laboratório de arquiteturas' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Paleta de componentes' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Editor visual' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Propriedades' })).toBeInTheDocument();
  });
  it('agrupa os seis clientes, API e Database e adiciona por clique', () => {
    render(<App />);
    const clients = screen.getByRole('region', { name: 'Clientes' });
    expect(screen.queryByRole('button', { name: /^Client$/ })).not.toBeInTheDocument();
    for (const name of ['Web Browser', 'Mobile App', 'Desktop App', 'CLI', 'Bot', 'Embedded System']) {
      expect(clients).toContainElement(screen.getByRole('button', { name }));
      fireEvent.click(screen.getByRole('button', { name }));
    }
    expect(screen.getByRole('region', { name: 'Serviços' })).toContainElement(screen.getByRole('button', { name: 'API' }));
    expect(screen.getByRole('region', { name: 'Dados' })).toContainElement(screen.getByRole('button', { name: 'Database' }));
    expect(screen.getByText(/6 componente\(s\)/)).toBeInTheDocument();
  });
  it('adiciona cliente por arrastar e soltar no canvas', () => {
    render(<App />);
    const data = new Map<string, string>();
    const dataTransfer = {
      setData: (format: string, value: string) => data.set(format, value),
      getData: (format: string) => data.get(format) ?? '',
      effectAllowed: 'none', dropEffect: 'none',
    };
    const canvas = screen.getByLabelText('Canvas da arquitetura');
    vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) });
    fireEvent.dragStart(screen.getByRole('button', { name: 'Bot' }), { dataTransfer });
    fireEvent.drop(canvas, { dataTransfer, clientX: 100, clientY: 100 });
    expect(screen.getByRole('button', { name: 'Nó: Bot 1' })).toHaveAttribute('data-component-type', 'BOT');
  });
  it.each([
    ['Web Browser', 'WEB_BROWSER'], ['Mobile App', 'MOBILE_APP'], ['Desktop App', 'DESKTOP_APP'],
    ['CLI', 'CLI'], ['Bot', 'BOT'], ['Embedded System', 'EMBEDDED_SYSTEM'],
  ])('seleciona, move, renomeia e remove %s sem afetar outra instância', (name, type) => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name }));
    fireEvent.click(screen.getByRole('button', { name }));
    const first = screen.getByRole('button', { name: `Nó: ${name} 1` });
    expect(first).toHaveAttribute('data-component-type', type);
    fireEvent.click(screen.getByRole('button', { name: `Mover: ${name} 1` }));
    expect(first).toHaveAttribute('data-position', '77,88');
    fireEvent.click(first);
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome de apresentação' }), { target: { value: 'Renomeado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar nome' }));
    expect(screen.getByRole('button', { name: 'Nó: Renomeado' })).toHaveAttribute('data-component-type', type);
    expect(screen.getByRole('button', { name: `Nó: ${name} 2` })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remover selecionado' }));
    expect(screen.queryByRole('button', { name: 'Nó: Renomeado' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: `Nó: ${name} 2` })).toBeInTheDocument();
  });
  it.each(['Web Browser', 'Mobile App', 'Desktop App', 'CLI', 'Bot', 'Embedded System'])('conecta %s a API e edita REST/SOAP', (name) => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name }));
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: `Nó: ${name} 1` }));
    const target = screen.getByRole('combobox', { name: 'Destino' });
    fireEvent.change(target, { target: { value: (screen.getByRole('option', { name: /API 1/ }) as HTMLOptionElement).value } });
    expect(screen.getByRole('combobox', { name: 'Protocolo' })).toHaveTextContent('REST');
    expect(screen.getByRole('combobox', { name: 'Protocolo' })).toHaveTextContent('SOAP');
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    expect(screen.getByText(/1 conexão\(ões\)/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: REST' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Editar protocolo' }), { target: { value: 'SOAP' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar alterações' }));
    expect(screen.getByRole('button', { name: 'Conexão: SOAP' })).toBeInTheDocument();
  });
  it('fecha propriedades ao clicar fora e preserva seleção ao interagir no painel', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API 1' }));
    expect(screen.getByRole('textbox', { name: 'Nome de apresentação' })).toBeInTheDocument();
    fireEvent.pointerDown(screen.getByRole('textbox', { name: 'Nome de apresentação' }));
    expect(screen.getByRole('textbox', { name: 'Nome de apresentação' })).toBeInTheDocument();
    fireEvent.pointerDown(screen.getByRole('complementary', { name: 'Paleta de componentes' }));
    expect(screen.queryByRole('textbox', { name: 'Nome de apresentação' })).not.toBeInTheDocument();
  });
  it('adiciona várias instâncias mantendo tipos', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'Database' }));
    expect(screen.getByTestId('flow')).toHaveTextContent('Nó: Web Browser');
    expect(screen.getByText(/3 componente\(s\)/)).toBeInTheDocument();
  });
  it('configura API, conecta ao Database e remove apenas a conexão', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: 'Database' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API 1' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Pré-processamento (ms)' }), { target: { value: '30' } });
    expect(screen.getByRole('spinbutton', { name: 'Pré-processamento (ms)' })).toHaveValue(30);
    const target = screen.getByRole('combobox', { name: 'Destino' });
    const databaseOption = screen.getByRole('option', { name: /Database/ });
    fireEvent.change(target, { target: { value: (databaseOption as HTMLOptionElement).value } });
    expect(screen.getByRole('combobox', { name: 'Protocolo' })).toHaveValue('TCP');
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    expect(screen.getByText(/1 conexão\(ões\)/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: TCP' }));
    const editLatency = screen.getByRole('spinbutton', { name: 'Editar latência (ms)' });
    fireEvent.change(editLatency, { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar alterações' }));
    expect(editLatency).toHaveValue(0);
    fireEvent.change(editLatency, { target: { value: '10' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar alterações' }));
    expect(editLatency).toHaveValue(10);

    fireEvent.click(screen.getByRole('button', { name: 'Remover selecionado' }));
    expect(screen.getByText(/0 conexão\(ões\)/)).toBeInTheDocument();
    expect(screen.getByText(/2 componente\(s\)/)).toBeInTheDocument();
  });
  it('anuncia alterações aplicadas e descartadas com feedback acessível', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', {name:'API'}));
    fireEvent.click(screen.getByRole('button', {name:'Database'}));
    fireEvent.click(screen.getByRole('button', {name:'Nó: API 1'}));
    expect(screen.getByText(/Os tempos são aplicados imediatamente/)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', {name:'Pré-processamento (ms)'}), {target:{value:'30'}});
    expect(screen.getByText('Tempo aplicado imediatamente ao componente.')).toBeInTheDocument();
    const destination=screen.getByRole('combobox',{name:'Destino'});
    fireEvent.change(destination,{target:{value:(screen.getByRole('option',{name:/Database/}) as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Conectar'}));
    expect(screen.getByText('Conexão criada no canvas.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Conexão: TCP'}));
    fireEvent.click(screen.getByRole('button',{name:'Cancelar alterações'}));
    expect(screen.getByText('Alterações não aplicadas foram descartadas.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Confirmar alterações'}));
    expect(screen.getByText('Alterações da conexão aplicadas.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Remover selecionado'}));
    expect(screen.getByText('Elemento removido do canvas.')).toBeInTheDocument();
  });
  it('valida arquitetura com backend v2, preserva resultado valid:false e path', async () => {
    server.use(http.post('/api/v1/architectures/validate', async ({ request }) => {
      const body = await request.json() as { components: unknown[]; scenario?: unknown };
      expect(body.components).toEqual([]);
      expect(body).not.toHaveProperty('scenario');
      return HttpResponse.json({ valid: false, violations: [{ code: 'INVALID_DAG', path: 'connections[0]', message: 'Conexão inválida' }] });
    }));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Validar arquitetura v1' }));
    expect(await screen.findByText(/INVALID_DAG/)).toBeInTheDocument();
    expect(screen.getByText('connections[0]', { exact: false })).toBeInTheDocument();
  });
  it.each(['Validar arquitetura v1', 'Validar cenário v1'])('envia %s com tipo funcional CLIENT', async (action) => {
    const calls = vi.fn(async ({ request }: { request: Request }) => {
      const body = await request.json() as { components?: { type: string }[]; architecture?: { components: { type: string }[] } };
      expect((body.components ?? body.architecture?.components)?.[0].type).toBe('WEB_BROWSER');
      return HttpResponse.json({ valid: true, violations: [] });
    });
    server.use(
      http.post('/api/v1/architectures/validate', calls),
      http.post('/api/v1/scenarios/validate', calls),
    );
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: action }));
    expect(await screen.findByText(/v1 válid[ao] pelo backend/)).toBeInTheDocument();
    expect(calls).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Nó: Web Browser 1' })).toHaveAttribute('data-component-type', 'WEB_BROWSER');
  });
  it('valida cenário em rota separada sem executar o motor', async () => {
    server.use(http.post('/api/v1/scenarios/validate', async ({ request }) => {
      const body = await request.json() as { architecture: { components: unknown[] }; scenario: { apiExecutions: unknown[] } };
      expect(body.architecture.components).toEqual([]);
      expect(body.scenario.apiExecutions).toEqual([]);
      return HttpResponse.json({ valid: true, violations: [] });
    }));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Validar cenário v1' }));
    expect(await screen.findByText(/Cenário v1 válido pelo backend/)).toBeInTheDocument();
  });
  it('mostra Problem Details de falhas HTTP v2', async () => {
    server.use(http.post('/api/v1/architectures/validate', () => HttpResponse.json({
      type: 'about:blank', title: 'Too Large', status: 413, code: 'PAYLOAD_TOO_LARGE', detail: 'Limite excedido',
    }, { status: 413 })));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Validar arquitetura v1' }));
    expect(await screen.findByText(/PAYLOAD_TOO_LARGE/)).toBeInTheDocument();
    expect(screen.getByText(/HTTP 413: Limite excedido/)).toBeInTheDocument();
  });

  it('apresenta 422 de validação v2 sem executar o motor', async () => {
    server.use(http.post('/api/v1/scenarios/validate', () => HttpResponse.json({
      valid: false, violations: [{ code: 'INVALID_ENTRY_CONNECTION', path: 'scenario.entryConnectionId', message: 'Selecione uma conexão inicial' }],
    })));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(await screen.findByText(/INVALID_ENTRY_CONNECTION/)).toBeInTheDocument();
    expect(screen.getByText(/Execução:/)).toHaveTextContent('ERROR');
  });

  it('executa o cenário v2, preserva snapshot e bloqueia edição até reiniciar', async () => {
    server.use(http.post('/api/v1/scenarios/validate', () => HttpResponse.json({ valid: true, violations: [] })));
    server.use(http.post('/api/v1/simulations', async ({ request }) => {
      const input = await request.json() as { scenario: { entryComponentId: string; entryConnectionId: string }; architecture: { components: unknown[] } };
      expect(input.architecture.components).toHaveLength(2);
      expect(input.scenario.entryComponentId).not.toBe('');
      expect(input.scenario.entryConnectionId).not.toBe('');
      return HttpResponse.json({
        execution: { id: 'exec-v2', status: 'COMPLETED', requestId: 'req-v2', entryComponentId: input.scenario.entryComponentId, entryConnectionId: input.scenario.entryConnectionId, totalDurationMs: 260 },
        initialState: { components: {}, requests: {}, exchanges: {} },
        trace: [], timeline: [],
        metrics: { totalDurationMs: 260, processingByComponent: [], communicationByConnection: [], waitingByComponent: [], exchanges: [] },
        messageRepresentations: [],
      });
    }));
    render(<App />);
    for (const type of ['Web Browser', 'API']) fireEvent.click(screen.getByRole('button', { name: type }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Web Browser 1' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Destino' }), { target: { value: (screen.getByRole('option', { name: /API 1/ }) as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Client inicial' }), { target: { value: (screen.getByRole('option', { name: /Web Browser 1/ }) as HTMLOptionElement).value } });
    const start = screen.getByRole('combobox', { name: 'Conexão de entrada' });
    fireEvent.change(start, { target: { value: (start.querySelector('option:not([value=""])') as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(await screen.findByRole('region', { name: 'Reprodução v1' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Abrir inspeção/ })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('separator', { name: 'Redimensionar inspeção' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Abrir inspeção/ }));
    expect(screen.getByRole('separator', { name: 'Redimensionar inspeção' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('separator', { name: 'Redimensionar inspeção' }), { key: 'ArrowDown' });
    expect(screen.getByRole('separator', { name: 'Redimensionar inspeção' })).toHaveAttribute('aria-valuenow', '284');
    fireEvent.click(screen.getByRole('button', { name: /Recolher inspeção/ }));
    expect(screen.queryByRole('separator', { name: 'Redimensionar inspeção' })).not.toBeInTheDocument();
    expect(screen.getByText(/Execução:/)).toHaveTextContent('260 ms');
    expect(screen.getByRole('button', { name: 'Web Browser' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar' }));
    expect(screen.getByRole('button', { name: 'Web Browser' })).toBeEnabled();
    expect(screen.getByText(/2 componente\(s\)/)).toBeInTheDocument();
  });
  it.each(['Web Browser', 'Mobile App', 'Desktop App', 'CLI', 'Bot', 'Embedded System'])('simula %s → API → Database e correlaciona timeline', async (name) => {
    const protocol = name === 'CLI' ? 'SOAP' : 'REST';
    server.use(http.post('/api/v1/scenarios/validate', async ({ request }) => {
      const body = await request.json() as { architecture: { components: { id: string; type: string }[]; connections: { sourceId: string; targetId: string }[] }; scenario: { entryComponentId: string; entryConnectionId: string; apiExecutions: { componentId: string; dependencyConnectionId: string }[] } };
      const visualType = { 'Web Browser': 'WEB_BROWSER', 'Mobile App': 'MOBILE_APP',
        'Desktop App': 'DESKTOP_APP', CLI: 'CLI', Bot: 'BOT', 'Embedded System': 'EMBEDDED_SYSTEM' }[name];
      expect(body.architecture.components.map(component => component.type)).toEqual([visualType, 'API', 'DATABASE']);
      expect(body.scenario.entryComponentId).toBe(body.architecture.components[0].id);
      expect(body.scenario.apiExecutions).toHaveLength(1);
      expect(body.architecture.connections).toHaveLength(2);
      return HttpResponse.json({ valid: true, violations: [] });
    }));
    server.use(http.post('/api/v1/simulations', async ({ request }) => {
      const body = await request.json() as { architecture: { components: { id: string; type: string; displayName: string }[]; connections: { id: string; sourceId: string; targetId: string; integration: { kind: string } }[] }; scenario: { entryComponentId: string; entryConnectionId: string } };
      const [client, api, db] = body.architecture.components;
      const visualType = { 'Web Browser': 'WEB_BROWSER', 'Mobile App': 'MOBILE_APP',
        'Desktop App': 'DESKTOP_APP', CLI: 'CLI', Bot: 'BOT', 'Embedded System': 'EMBEDDED_SYSTEM' }[name];
      expect(client).toMatchObject({ type: visualType, displayName: `${name} 1` });
      expect(body.architecture.connections[0]).toMatchObject({ sourceId: client.id, targetId: api.id, integration: { kind: protocol === 'SOAP' ? 'SOAP_HTTP' : 'HTTP_REST' } });
      expect(body.architecture.connections[1]).toMatchObject({ sourceId: api.id, targetId: db.id });
      expect(body.scenario.entryComponentId).toBe(client.id);
      return HttpResponse.json({
        execution: { id: 'exec-v2', status: 'COMPLETED', requestId: 'req-v2', entryComponentId: client.id, entryConnectionId: body.scenario.entryConnectionId, totalDurationMs: 20 },
        initialState: { components: { [client.id]: 'IDLE', [api.id]: 'IDLE', [db.id]: 'IDLE' }, requests: {}, exchanges: {} },
        trace: [{ type: 'STATE_TRANSITION', sequence: 1, timestampMs: 20, entityKind: 'COMPONENT', entityId: client.id, previousState: 'IDLE', newState: 'COMPLETED', cause: 'RESPONSE_RECEIVED' }],
        timeline: [{ id: 'activity-1', kind: 'PROCESSING', componentId: api.id, requestId: 'req-v2', phase: 'API_PRE_PROCESSING', startMs: 0, endMs: 20, durationMs: 20 }],
        metrics: { totalDurationMs: 20, processingByComponent: [{ componentId: api.id, durationMs: 20 }], communicationByConnection: [], waitingByComponent: [], exchanges: [] },
        messageRepresentations: [],
      });
    }));
    render(<App />);
    for (const type of [name, 'API', 'Database']) fireEvent.click(screen.getByRole('button', { name: type }));
    fireEvent.click(screen.getByRole('button', { name: `Nó: ${name} 1` }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Destino' }), { target: { value: (screen.getByRole('option', { name: /API 1/ }) as HTMLOptionElement).value } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Protocolo' }), { target: { value: protocol } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API 1' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Destino' }), { target: { value: (screen.getByRole('option', { name: /Database 1/ }) as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Client inicial' }), { target: { value: (screen.getByRole('option', { name: new RegExp(`${name} 1`) }) as HTMLOptionElement).value } });
    const start = screen.getByRole('combobox', { name: 'Conexão de entrada' });
    fireEvent.change(start, { target: { value: (start.querySelector('option:not([value=""])') as HTMLOptionElement).value } });
    const dependency = screen.getByRole('combobox', { name: 'Dependência de API 1' });
    fireEvent.change(dependency, { target: { value: (dependency.querySelector('option:not([value=""])') as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(await screen.findByRole('region', { name: 'Reprodução v1' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `Nó: ${name} 1` })).toHaveAttribute('data-component-type');
    fireEvent.click(screen.getByRole('button', { name: /Abrir inspeção/ }));
    expect(screen.getByText('Timeline recebida do backend')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Análise de métricas v1' })).toHaveTextContent('20 ms');
  });

  it('permite nomes duplicados sem alterar identidade e rejeita nome vazio', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Web Browser 1' }));
    const name = screen.getByRole('textbox', { name: 'Nome de apresentação' });
    fireEvent.change(name, { target: { value: 'Duplicado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar nome' }));
    expect(screen.getByText('Nó: Duplicado')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Web Browser 2' }));
    fireEvent.change(name, { target: { value: 'Duplicado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar nome' }));
    expect(screen.getAllByText('Nó: Duplicado')).toHaveLength(2);
    fireEvent.change(name, { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar nome' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Nome obrigatório');
  });
  it('permite mudar tema sem alterar o canvas', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Tema' }), { target: { value: 'dark' } });
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(screen.getByTestId('flow')).toHaveTextContent('Nó: API 1');
  });

  it('exibe editor de cenário e não seleciona entrada automaticamente', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    expect(screen.getByRole('region', { name: 'Editor de cenário' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Client inicial' })).toHaveValue('');
    expect(screen.getByRole('combobox', { name: 'Conexão de entrada' })).toBeDisabled();
    const clientOption = screen.getByRole('option', { name: /Web Browser 1/ });
    fireEvent.change(screen.getByRole('combobox', { name: 'Client inicial' }), { target: { value: (clientOption as HTMLOptionElement).value } });
    expect(screen.getByRole('combobox', { name: 'Conexão de entrada' })).toBeEnabled();
    expect(screen.getByRole('combobox', { name: 'Conexão de entrada' })).toHaveValue('');
  });

  it('limpa erro de cenário v2 ao editar seleção', async () => {
    server.use(http.post('/api/v1/scenarios/validate', () => HttpResponse.json({
      valid: false, violations: [{ code: 'INVALID_ENTRY_CONNECTION', path: 'scenario.entryConnectionId', message: 'Entrada ausente' }],
    })));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(await screen.findByText(/INVALID_ENTRY_CONNECTION/)).toBeInTheDocument();
    const clientOption = screen.getByRole('option', { name: /Web Browser 1/ });
    fireEvent.change(screen.getByRole('combobox', { name: 'Client inicial' }), { target: { value: (clientOption as HTMLOptionElement).value } });
    expect(screen.getByText(/Execução:/)).toHaveTextContent('READY');
    expect(screen.queryByText(/INVALID_ENTRY_CONNECTION/)).not.toBeInTheDocument();
  });

  it('preserva integração SOAP ao confirmar latência da conexão', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Web Browser 1' }));
    const option = screen.getByRole('option', { name: /API 1/ });
    fireEvent.change(screen.getByRole('combobox', { name: 'Destino' }), { target: { value: (option as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: REST' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Tipo de integração' }), { target: { value: 'SOAP_HTTP' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Operação SOAP' }), { target: { value: 'ConsultarSeguro' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar integração' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Editar latência (ms)' }), { target: { value: '55' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar alterações' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API 1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: SOAP' }));
    expect(screen.getByRole('combobox', { name: 'Tipo de integração' })).toHaveValue('SOAP_HTTP');
    expect(screen.getByRole('textbox', { name: 'Operação SOAP' })).toHaveValue('ConsultarSeguro');
  });

  it('executa uma sessão WebSocket v0.4 pelo editor', async () => {
    server.use(http.post('/api/v1/scenarios/validate', async ({request}) => {
      const body = await request.json() as {scenario:{kind:string;entryComponentId:string;entryConnectionId:string};architecture:{connections:{integration:{kind:string}}[]}};
      expect(body.scenario.kind).toBe('WEBSOCKET_SESSION');
      expect(body.architecture.connections[0].integration.kind).toBe('WEBSOCKET');
      return HttpResponse.json({valid:true,violations:[]});
    }));
    server.use(http.post('/api/v1/simulations', async ({request}) => {
      const body = await request.json() as {scenario:{kind:string}};
      expect(body.scenario.kind).toBe('WEBSOCKET_SESSION');
      return HttpResponse.json({
        execution:{id:'exec-1',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'request-1',entryComponentId:'c',entryConnectionId:'e',totalDurationMs:20},
        initialState:{components:{},requests:{},exchanges:{}},trace:[],timeline:[],messageRepresentations:[],
        resourceStates:[{id:'session-1',kind:'WEBSOCKET_SESSION',status:'COMPLETED'}],
        metrics:{totalDurationMs:20,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[],
          websocket:[{connectionId:'e',sessionId:'session-1',messagesSent:1,messagesReceived:1,durationMs:20}]},
      });
    }));
    render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Web Browser'}));
    fireEvent.click(screen.getByRole('button',{name:'API'}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Web Browser 1'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Destino'}),{target:{value:(screen.getByRole('option',{name:/API 1/}) as HTMLOptionElement).value}});
    fireEvent.change(screen.getByRole('combobox',{name:'Protocolo'}),{target:{value:'WEBSOCKET'}});
    fireEvent.click(screen.getByRole('button',{name:'Conectar'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Tipo de cenário'}),{target:{value:'WEBSOCKET_SESSION'}});
    fireEvent.change(screen.getByRole('combobox',{name:'Origem inicial'}),{target:{value:(screen.getByRole('option',{name:/Web Browser 1/}) as HTMLOptionElement).value}});
    const entry = screen.getByRole('combobox',{name:'Conexão de entrada'});
    fireEvent.change(entry,{target:{value:(entry.querySelector('option:not([value=""])') as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Simular'}));
    expect(await screen.findByLabelText('Recursos da execução')).toHaveTextContent('WEBSOCKET_SESSION');
    expect(screen.getByText(/Conclusão global:/)).toHaveTextContent('COMPLETED');
  });
  it('publica em Queue com destino e assinatura configurados no Broker', async () => {
    const inspect = vi.fn(async ({request}:{request:Request}) => {
      const body = await request.json() as {architecture:{components:{type:string;destinations?:unknown[];subscriptions?:unknown[]}[];connections:{integration:{kind:string}}[]};scenario:{kind:string;destinationName:string;publishCount:number}};
      expect(body.scenario).toMatchObject({kind:'MESSAGE_PUBLISH',destinationName:'orders',publishCount:1});
      expect(body.architecture.components.find(c => c.type === 'BROKER')).toMatchObject({
        destinations:[{name:'orders',kind:'QUEUE'}],subscriptions:[expect.objectContaining({destinationName:'orders'})],
      });
      expect(body.architecture.connections.map(c => c.integration.kind)).toEqual(['QUEUE','QUEUE']);
      return HttpResponse.json({valid:true,violations:[]});
    });
    server.use(http.post('/api/v1/scenarios/validate',inspect));
    server.use(http.post('/api/v1/simulations',() => HttpResponse.json({
      execution:{id:'exec-1',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'publish-1',entryComponentId:'c',entryConnectionId:'e',totalDurationMs:20},
      initialState:{components:{},requests:{},exchanges:{}},trace:[],timeline:[],messageRepresentations:[],
      resourceStates:[{id:'broker-message-1',kind:'BROKER_MESSAGE',status:'COMPLETED'}],
      metrics:{totalDurationMs:20,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[],
        messaging:[{connectionId:'e',published:1,delivered:1,processed:1,pending:0}]},
    })));
    render(<App/>);
    for (const name of ['Bot','Broker','API']) fireEvent.click(screen.getByRole('button',{name}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Broker 1'}));
    fireEvent.click(screen.getByRole('button',{name:'Adicionar destino'}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Bot 1'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Destino'}),{target:{value:(screen.getByRole('option',{name:/Broker 1/}) as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Conectar'}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Broker 1'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Destino'}),{target:{value:(screen.getByRole('option',{name:/API 1/}) as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Conectar'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Destino da assinatura'}),{target:{value:'orders'}});
    const delivery = screen.getByRole('combobox',{name:'Conexão de entrega'});
    fireEvent.change(delivery,{target:{value:(delivery.querySelector('option:not([value=""])') as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Adicionar assinatura'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Tipo de cenário'}),{target:{value:'MESSAGE_PUBLISH'}});
    fireEvent.change(screen.getByRole('combobox',{name:'Origem inicial'}),{target:{value:(screen.getByRole('option',{name:/Bot 1/}) as HTMLOptionElement).value}});
    const entry = screen.getByRole('combobox',{name:'Conexão de entrada'});
    fireEvent.change(entry,{target:{value:(entry.querySelector('option:not([value=""])') as HTMLOptionElement).value}});
    fireEvent.change(screen.getByRole('combobox',{name:'Destino do Broker'}),{target:{value:'orders'}});
    fireEvent.click(screen.getByRole('button',{name:'Simular'}));
    expect(await screen.findByLabelText('Recursos da execução')).toHaveTextContent('BROKER_MESSAGE');
    expect(inspect).toHaveBeenCalledOnce();
  });

});
