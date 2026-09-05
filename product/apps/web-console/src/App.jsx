import { projectRuntimeSnapshot } from "./projection.js";

const releaseRows = [
  ["Repository / contracts", "PASS"],
  ["Storage reference runtime", "PASS"],
  ["Harness runtime", "PASS"],
  ["Portable Brain", "PASS"],
  ["Application runtime", "PASS"],
  ["API / Worker / Daemon", "PASS"],
  ["React build", "NOT_EXECUTED"],
  ["Physical PostgreSQL", "NOT_EXECUTED"],
];

const demo = projectRuntimeSnapshot({ tasks: [], events: [] });

export function App() {
  return (
    <main className="shell">
      <header>
        <p className="eyebrow">AEGIS Operations</p>
        <h1>Operational Control Plane</h1>
        <p>Canonical runtime state is projected here; the React SPA is never the state owner.</p>
      </header>
      <section aria-labelledby="runtime-status">
        <h2 id="runtime-status">Runtime snapshot</h2>
        <div className="grid">
          <article className="card"><span>Tasks</span><strong>{demo.totals.tasks}</strong></article>
          <article className="card"><span>Active</span><strong>{demo.totals.active}</strong></article>
          <article className="card"><span>Failures</span><strong>{demo.totals.failures}</strong></article>
          <article className="card"><span>Events</span><strong>{demo.totals.events}</strong></article>
        </div>
      </section>
      <section aria-labelledby="controls"><h2 id="controls">Operator controls</h2><p>{demo.controls.join(" · ")}</p></section>
      <section aria-labelledby="release-status">
        <h2 id="release-status">R0.7 delivery gates</h2>
        <div className="grid">{releaseRows.map(([label, status]) => <article className="card" key={label}><span>{label}</span><strong>{status}</strong></article>)}</div>
      </section>
    </main>
  );
}
