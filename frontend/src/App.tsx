import ArchitectureEditor from './features/editor/ArchitectureEditor';

export default function App() {
  return (
    <main className="workspace">
      <header className="workspace-header">
        <div>
          <p className="eyebrow">ArchLab · MVP v0.1</p>
          <h1>Laboratório de arquiteturas</h1>
        </div>
        <span className="workspace-status">Editor visual · MVP v0.1</span>
      </header>
      <ArchitectureEditor />
    </main>
  );
}
