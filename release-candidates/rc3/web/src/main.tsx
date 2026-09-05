import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Health = { status: string; version: string };

function App() {
  const health: Health = { status: 'BASELINE', version: '0.1.0-rc1' };
  return (
    <main className="shell">
      <header><p className="eyebrow">AEGIS-CLI R0</p><h1>Operations Control Plane</h1></header>
      <section className="grid" aria-label="system status">
        <article><h2>Runtime</h2><strong>{health.status}</strong><p>{health.version}</p></article>
        <article><h2>Release Gate</h2><strong>RC-1 / RC-2</strong><p>Physical baseline</p></article>
        <article><h2>Trust</h2><strong>Fail Closed</strong><p>Evidence before promotion</p></article>
      </section>
      <section className="timeline"><h2>Canonical lifecycle</h2><p>Observe → Understand → Measure/Retrieve → Plan → Execute → Verify → Compare → Remember → Adapt</p></section>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
