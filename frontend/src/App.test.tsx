import { render, screen, fireEvent } from '@testing-library/react';
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
    fireEvent.click(screen.getByRole('button', { name: 'Remover selecionado' }));
    expect(screen.getByText(/0 conexão\(ões\)/)).toBeInTheDocument();
    expect(screen.getByText(/2 componente\(s\)/)).toBeInTheDocument();
  });
});
