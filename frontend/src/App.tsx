import ArchitectureEditor from './features/editor/ArchitectureEditor';
import { useTheme } from './features/editor/useTheme';

export default function App() {
  const { theme, setTheme } = useTheme();
  return (
    <main className="workspace">
      <header className="workspace-header">
        <div>
          <p className="eyebrow">ArchLab · MVP v0.1</p>
          <h1>Laboratório de arquiteturas</h1>
        </div>
        <div className="workspace-actions"><span className="workspace-status">Editor visual · MVP v0.1</span><label htmlFor="archlab-theme">Tema</label><select id="archlab-theme" aria-label="Tema" value={theme} onChange={(event) => setTheme(event.target.value as "light" | "dark")}><option value="light">Claro</option><option value="dark">Escuro</option></select></div>
      </header>
      <ArchitectureEditor />
    </main>
  );
}
