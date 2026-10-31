import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('@xyflow/react', async () => {
  const React = await import('react');
  return {
    ReactFlowProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    ReactFlow: ({ nodes, edges, onNodeClick, onEdgeClick }: {
      nodes: { id: string; data: { label: string } }[];
      edges: { id: string; label?: React.ReactNode }[];
      onNodeClick: (event: unknown, node: unknown) => void;
      onEdgeClick: (event: unknown, edge: unknown) => void;
    }) => (
      <div data-testid="flow">
        {nodes.map((node) => <button key={node.id} onClick={() => onNodeClick(null, node)}>Nó: {node.data.label}</button>)}
        {edges.map((edge) => <button key={edge.id} onClick={() => onEdgeClick(null, edge)}>Conexão: {edge.label}</button>)}
      </div>
    ),
    Background: () => null, Controls: () => null, MiniMap: () => null,
    Handle: () => null, Position: { Left: 'left', Right: 'right' },
    useReactFlow: () => ({ screenToFlowPosition: (position: { x: number; y: number }) => position }),
    useNodesState: (initial: unknown[]) => {
      const [nodes, setNodes] = React.useState(initial);
      return [nodes, setNodes, () => undefined];
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
  it('adiciona várias instâncias mantendo tipos', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Client' }));
    fireEvent.click(screen.getByRole('button', { name: 'Client' }));
    fireEvent.click(screen.getByRole('button', { name: 'Database' }));
    expect(screen.getByTestId('flow')).toHaveTextContent('Nó: Client');
    expect(screen.getByText(/3 componente\(s\)/)).toBeInTheDocument();
  });
  it('configura API, conecta ao Database e remove apenas a conexão', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'API' }));
    fireEvent.click(screen.getByRole('button', { name: 'Database' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API' }));
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
  it('exibe violações locais sem chamar a API', async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Validar arquitetura' }));
    expect(await screen.findByText(/Arquitetura inválida/)).toBeInTheDocument();
    expect(screen.getAllByText(/MISSING_COMPONENT/).length).toBeGreaterThan(0);
  });

  it('não inicia execução quando faltam componentes', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    expect(screen.getByText(/Execução:/)).toHaveTextContent('ERROR');
    expect(screen.getByText(/Arquitetura inválida/)).toBeInTheDocument();
  });

  it('simula e bloqueia edição até reiniciar', async () => {
    render(<App />);
    for (const type of ['Client', 'API', 'Database']) fireEvent.click(screen.getByRole('button', { name: type }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Client' }));
    const clientDestination = screen.getByRole('combobox', { name: 'Destino' });
    fireEvent.change(clientDestination, { target: { value: (screen.getByRole('option', { name: /API/ }) as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: API' }));
    const apiDestination = screen.getByRole('combobox', { name: 'Destino' });
    fireEvent.change(apiDestination, { target: { value: (screen.getByRole('option', { name: /Database/ }) as HTMLOptionElement).value } });
    fireEvent.click(screen.getByRole('button', { name: 'Conectar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }));
    await waitFor(() => expect(screen.getByText(/Execução:/)).toHaveTextContent('COMPLETED'));
    expect(screen.getByText(/Execução:/)).toHaveTextContent('260 ms');
    expect(screen.getByRole('button', { name: 'Client' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar' }));
    expect(screen.getByRole('button', { name: 'Client' })).toBeEnabled();
    expect(screen.getByText(/3 componente\(s\)/)).toBeInTheDocument();
  });


  it('permite nomes duplicados sem alterar identidade e rejeita nome vazio', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Client' }));
    fireEvent.click(screen.getByRole('button', { name: 'Client' }));
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Client 1' }));
    const name = screen.getByRole('textbox', { name: 'Nome de apresentação' });
    fireEvent.change(name, { target: { value: 'Duplicado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar nome' }));
    expect(screen.getByText('Nó: Duplicado')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Nó: Client 2' }));
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

});
