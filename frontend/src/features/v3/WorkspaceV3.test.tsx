import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import WorkspaceV3 from './WorkspaceV3';
describe('v3 workspace', () => { it('edits an independent project with a labelled input', () => { render(<WorkspaceV3 />); const input = screen.getByLabelText('Identificador da arquitetura'); fireEvent.change(input, { target: { value: 'my-v3' } }); expect(input).toHaveValue('my-v3'); expect(screen.getByRole('region', { name: 'Editor ArchLab v0.3' })).toBeInTheDocument(); }); });
