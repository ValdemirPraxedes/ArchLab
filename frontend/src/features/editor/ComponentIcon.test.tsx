import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ComponentIcon } from './ComponentIcon';

describe('Lucide nos componentes', () => {
  it.each(['CLIENT','API','DATABASE'] as const)('renderiza %s com SVG decorativo e sem alterar o rótulo acessível', (type) => {
    const {container}=render(<div><ComponentIcon type={type}/><span>{type}</span></div>);
    const icon=container.querySelector('.component-icon svg');
    expect(icon).not.toBeNull();
    expect(icon?.tagName.toLowerCase()).toBe('svg');
    expect(screen.getByText(type)).toBeInTheDocument();
    expect(icon?.closest('[aria-hidden="true"]')).not.toBeNull();
  });
});
