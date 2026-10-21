import { describe, expect, it, vi } from 'vitest';
import { createArchitectureNode, isComponentType } from './model';

describe('modelo do editor', () => {
  it('aceita somente tipos conhecidos', () => {
    expect(isComponentType('CLIENT')).toBe(true);
    expect(isComponentType('API')).toBe(true);
    expect(isComponentType('DATABASE')).toBe(true);
    expect(isComponentType('CACHE')).toBe(false);
  });

  it('cria instâncias distintas com posição preservada', () => {
    const uuid = vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222');
    try {
      const a = createArchitectureNode('CLIENT', { x: 10, y: 20 });
      const b = createArchitectureNode('CLIENT', { x: 30, y: 40 });
      expect(a.id).not.toBe(b.id);
      expect(a.position).toEqual({ x: 10, y: 20 });
      expect(b.position).toEqual({ x: 30, y: 40 });
      expect(a.data.componentType).toBe('CLIENT');
    } finally {
      uuid.mockRestore();
    }
  });
});
