import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasV3 } from './CanvasV3';
import { addComponentV3 } from './components';
import { COMPONENTS_V3 } from './catalog';
import { newProjectV3 } from './project';
describe('real React Flow v3 node rendering', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('renders the six Lucide nodes inside the real provider without inventing runtime state', () => {
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
    let project = newProjectV3(); for (const type of COMPONENTS_V3) project = addComponentV3(project, type.typeId);
    const noop = () => undefined; render(<CanvasV3 project={project} selected={null} select={noop} add={noop} move={noop} />);
    for (const type of COMPONENTS_V3) expect(screen.getByText(type.label + ' 1')).toBeInTheDocument();
    expect(screen.getAllByText('Sem estado observado')).toHaveLength(6);
  });
});
