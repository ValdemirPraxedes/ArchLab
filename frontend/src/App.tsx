import { lazy, Suspense, useEffect, useState } from 'react';
import ArchitectureEditor from './features/editor/ArchitectureEditor';
import { useTheme } from './features/editor/useTheme';

const WorkspaceV3 = lazy(() => import('./features/v3/WorkspaceV3'));
export default function App() {
  const { theme, setTheme } = useTheme();
  const [version, setVersion] = useState(() => window.location.hash === '#v3' ? 'v3' : 'legacy');
  const [visitedV3, setVisitedV3] = useState(() => window.location.hash === '#v3');
  useEffect(() => { const navigate = () => { const next = window.location.hash === '#v3' ? 'v3' : 'legacy'; setVersion(next); if (next === 'v3') setVisitedV3(true); }; window.addEventListener('hashchange', navigate); return () => window.removeEventListener('hashchange', navigate); }, []);
  return (
    <main className="workspace">
      <header className="workspace-header">
        <div>
          <p className="eyebrow">ArchLab · {version === 'v3' ? 'v0.3' : 'MVP v0.1'}</p>
          <h1>Laboratório de arquiteturas</h1>
        </div>
        <div className="workspace-actions"><label htmlFor="archlab-version">Versão</label><select id="archlab-version" value={version} onChange={event => { setVersion(event.target.value); if (event.target.value === 'v3') setVisitedV3(true); window.history.replaceState(null, '', event.target.value === 'v3' ? '#v3' : '#legacy'); }}><option value="legacy">v0.1 / v0.2</option><option value="v3">v0.3</option></select><span className="workspace-status">Editor visual · {version === 'v3' ? 'v0.3' : 'MVP v0.1'}</span><label htmlFor="archlab-theme">Tema</label><select id="archlab-theme" aria-label="Tema" value={theme} onChange={(event) => setTheme(event.target.value as "light" | "dark")}><option value="light">Claro</option><option value="dark">Escuro</option></select></div>
      </header>
      <div className="version-workspace" hidden={version !== 'legacy'}><ArchitectureEditor /></div>
      <div className="version-workspace" hidden={version !== 'v3'}>{visitedV3 && <Suspense fallback={<p role="status">Carregando editor v0.3…</p>}><WorkspaceV3 /></Suspense>}</div>
    </main>
  );
}
