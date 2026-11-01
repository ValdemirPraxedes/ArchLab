import { afterEach, describe, expect, it, vi } from 'vitest';
import { initialTheme } from './useTheme';
afterEach(() => { localStorage.clear(); vi.unstubAllGlobals(); });
describe('preferência de tema', () => {
  it('prioriza escolha persistida', () => {
    localStorage.setItem('archlab-theme', 'dark');
    expect(initialTheme()).toBe('dark');
  });
  it('usa preferência do sistema sem valor salvo', () => {
    localStorage.clear();
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })));
    expect(initialTheme()).toBe('dark');
  });
  it('usa claro como fallback', () => {
    localStorage.clear();
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })));
    expect(initialTheme()).toBe('light');
  });
});
