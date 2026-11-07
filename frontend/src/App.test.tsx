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
  it('agrupa os seis clientes e os bancos P0 sem oferecer DATABASE legado', () => {
    render(<App />);
    const clients = screen.getByRole('region', { name: 'Clientes' });
    expect(screen.queryByRole('button', { name: /^Client$/ })).not.toBeInTheDocument();
    for (const name of ['Web Browser', 'Mobile App', 'Desktop App', 'CLI', 'Bot', 'Embedded System']) {
      expect(clients).toContainElement(screen.getByRole('button', { name }));
      fireEvent.click(screen.getByRole('button', { name }));
    }
    expect(screen.getByRole('region', { name: 'Serviços' })).toContainElement(screen.getByRole('button', { name: 'Service' }));
    expect(screen.queryByRole('button',{name:'API'})).not.toBeInTheDocument();
    const data=screen.getByRole('region', { name: 'Dados' });
    for (const name of ['Relational Database','Document Database','Key-Value Store'])
      expect(data).toContainElement(screen.getByRole('button', { name }));
    expect(screen.queryByRole('button',{name:'Database'})).not.toBeInTheDocument();
    expect(screen.getByText(/6 componente\(s\)/)).toBeInTheDocument();
  });
  it('importa API histórica como SERVICE após validação e anuncia os IDs convertidos',async()=>{
    server.use(http.post('/api/v1/architectures/validate',async({request})=>{
      const body=await request.json() as {components:{id:string;type:string}[]};
      expect(body.components.find(component=>component.id==='old-api')?.type).toBe('SERVICE');
      return HttpResponse.json({valid:true,violations:[]});
    }));
    render(<App/>);
    const document={components:[{id:'old-api',type:'API',displayName:'Legacy',preProcessingMs:0,postProcessingMs:0,behaviors:[]}],connections:[]};
    const file=new File([JSON.stringify(document)],'architecture.json',{type:'application/json'});
    Object.defineProperty(file,'text',{value:async()=>JSON.stringify(document)});
    fireEvent.change(screen.getByLabelText('Arquivo de arquitetura'),{target:{files:[file]}});
    expect(await screen.findByText(/1 API convertida\(s\) para SERVICE/)).toHaveTextContent('old-api');
    expect(screen.getByRole('button',{name:'Nó: Legacy'})).toHaveAttribute('data-component-type','SERVICE');
  });
  it('mostra prévia de DATABASE legado e só altera o canvas após confirmação',async()=>{
    server.use(http.post('/api/v1/architectures/validate',()=>HttpResponse.json({valid:true,violations:[]})));
    render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Service'}));
    const document={components:[{id:'old-db',type:'DATABASE',displayName:'Legacy DB',processingMs:5}],connections:[]};
    const file=new File([JSON.stringify(document)],'legacy.json',{type:'application/json'});
    Object.defineProperty(file,'text',{value:async()=>JSON.stringify(document)});
    fireEvent.change(screen.getByLabelText('Arquivo de arquitetura'),{target:{files:[file]}});
    expect(await screen.findByRole('region',{name:'Prévia de migração DATABASE'})).toHaveTextContent('old-db');
    expect(screen.getByRole('button',{name:'Nó: Service 1'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Nó: Legacy DB'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Cancelar migração'}));
    expect(screen.getByRole('button',{name:'Nó: Service 1'})).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Arquivo de arquitetura'),{target:{files:[file]}});
    await screen.findByRole('region',{name:'Prévia de migração DATABASE'});
    fireEvent.click(screen.getByRole('button',{name:'Confirmar migração'}));
    expect(screen.getByRole('button',{name:'Nó: Legacy DB'})).toHaveAttribute('data-component-type','RELATIONAL_DATABASE');
  });
  it('mantém o canvas quando a arquitetura importada falha na validação',async()=>{
    server.use(http.post('/api/v1/architectures/validate',()=>HttpResponse.json({valid:false,violations:[{code:'INVALID_CONNECTION',path:'connections[0]',message:'Conexão inválida'}]})));
    render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Service'}));
    const document={components:[{id:'old-api',type:'API',displayName:'Legacy',behaviors:[]}],connections:[]};
    const file=new File([JSON.stringify(document)],'invalid.json',{type:'application/json'});
    Object.defineProperty(file,'text',{value:async()=>JSON.stringify(document)});
    fireEvent.change(screen.getByLabelText('Arquivo de arquitetura'),{target:{files:[file]}});
    expect(await screen.findByText(/Importação cancelada; o canvas original foi preservado/)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Nó: Service 1'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Nó: Legacy'})).not.toBeInTheDocument();
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
    fireEvent.click(screen.getByRole('button', { name: 'Service' }));
    fireEvent.click(screen.getByRole('button', { name: `Nó: ${name} 1` }));
    const target = screen.getByRole('combobox', { name: 'Destino' });
    fireEvent.change(target, { target: { value: (screen.getByRole('option', { name: /Service 1/ }) as HTMLOptionElement).value } });
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
    fireEvent.click(screen.getByRole('button', { name: 'Service' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Service 1' }));
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
    fireEvent.click(screen.getByRole('button', { name: 'Relational Database' }));
    expect(screen.getByTestId('flow')).toHaveTextContent('Nó: Web Browser');
    expect(screen.getByText(/3 componente\(s\)/)).toBeInTheDocument();
  });
  it('configura Service, conecta ao banco relacional e remove apenas a conexão', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Service' }));
    fireEvent.click(screen.getByRole('button', { name: 'Relational Database' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Service 1' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Pré-processamento (ms)' }), { target: { value: '30' } });
    expect(screen.getByRole('spinbutton', { name: 'Pré-processamento (ms)' })).toHaveValue(30);
    const target = screen.getByRole('combobox', { name: 'Destino' });
    const databaseOption = screen.getByRole('option', { name: /Relational Database/ });
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
    fireEvent.click(screen.getByRole('button', {name:'Service'}));
    fireEvent.click(screen.getByRole('button', {name:'Relational Database'}));
    fireEvent.click(screen.getByRole('button', {name:'Nó: Service 1'}));
    expect(screen.getByText(/Os tempos são aplicados imediatamente/)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', {name:'Pré-processamento (ms)'}), {target:{value:'30'}});
    expect(screen.getByText('Tempo aplicado imediatamente ao componente.')).toBeInTheDocument();
    const destination=screen.getByRole('combobox',{name:'Destino'});
    fireEvent.change(destination,{target:{value:(screen.getByRole('option',{name:/Relational Database/}) as HTMLOptionElement).value}});
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
    fireEvent.click(screen.getByRole('button', { name: 'Validar arquitetura' }));
    expect(await screen.findByText(/INVALID_DAG/)).toBeInTheDocument();
    expect(screen.getByText('connections[0]', { exact: false })).toBeInTheDocument();
  });
  it('mostra Problem Details de falhas HTTP v2', async () => {
    server.use(http.post('/api/v1/architectures/validate', () => HttpResponse.json({
      type: 'about:blank', title: 'Too Large', status: 413, code: 'PAYLOAD_TOO_LARGE', detail: 'Limite excedido',
    }, { status: 413 })));
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Validar arquitetura' }));
    expect(await screen.findByText(/PAYLOAD_TOO_LARGE/)).toBeInTheDocument();
    expect(screen.getByText(/HTTP 413: Limite excedido/)).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole('button', { name: 'Service' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Tema' }), { target: { value: 'dark' } });
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(screen.getByTestId('flow')).toHaveTextContent('Nó: Service 1');
  });

  it('preserva integração SOAP ao confirmar latência da conexão', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Web Browser' }));
    fireEvent.click(screen.getByRole('button', { name: 'Service' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Web Browser 1' }));
    const option = screen.getByRole('option', { name: /Service 1/ });
    fireEvent.change(screen.getByRole('combobox', { name: 'Destino' }), { target: { value: (option as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: REST' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Tipo de integração' }), { target: { value: 'SOAP_HTTP' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Operação SOAP' }), { target: { value: 'ConsultarSeguro' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar integração' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Editar latência (ms)' }), { target: { value: '55' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar alterações' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Service 1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Conexão: SOAP' }));
    expect(screen.getByRole('combobox', { name: 'Tipo de integração' })).toHaveValue('SOAP_HTTP');
    expect(screen.getByRole('textbox', { name: 'Operação SOAP' })).toHaveValue('ConsultarSeguro');
  });

  it('seleciona a entrada sem tipo global e envia UnifiedScenario com comportamento explícito', async () => {
    const inspect = vi.fn(async ({request}:{request:Request}) => {
      const body = await request.json() as {scenario:{entry:{componentId:string;connectionId:string};sessionSetup:unknown[]};architecture:{components:{type:string;behaviors?:{actions:{kind:string}[]}[]}[]}};
      expect(body.scenario).not.toHaveProperty('kind');
      expect(body.scenario.entry.componentId).toBeTruthy();
      expect(body.scenario.entry.connectionId).toBeTruthy();
      expect(body.scenario.sessionSetup).toEqual([]);
      expect(body.architecture.components.find(component=>component.type==='SERVICE')?.behaviors?.[0].actions[0].kind).toBe('RESPOND');
      return HttpResponse.json({valid:true,violations:[]});
    });
    server.use(http.post('/api/v1/scenarios/validate',inspect));
    render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Web Browser'}));
    fireEvent.click(screen.getByRole('button',{name:'Service'}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Web Browser 1'}));
    fireEvent.change(screen.getByRole('combobox',{name:'Destino'}),{target:{value:(screen.getByRole('option',{name:/Service 1/}) as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Conectar'}));
    fireEvent.click(screen.getByRole('button',{name:'Nó: Service 1'}));
    fireEvent.click(screen.getByRole('button',{name:'Adicionar comportamento'}));
    expect(screen.getByRole('combobox',{name:'Tipo da ação 1.1'})).toHaveValue('RESPOND');
    expect(screen.queryByRole('combobox',{name:'Tipo de cenário'})).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox',{name:'Componente inicial'}),{target:{value:(screen.getByRole('option',{name:/Web Browser 1/}) as HTMLOptionElement).value}});
    const connection=screen.getByRole('combobox',{name:'Conexão inicial'});
    fireEvent.change(connection,{target:{value:(connection.querySelector('option:not([value=""])') as HTMLOptionElement).value}});
    fireEvent.click(screen.getByRole('button',{name:'Validar cenário'}));
    expect(await screen.findByText(/Cenário válido pelo backend/)).toBeInTheDocument();
    expect(inspect).toHaveBeenCalledOnce();
  });
});
