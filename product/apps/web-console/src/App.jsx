import React from "react";

const releaseRows = [
  ["Repository bootstrap", "PASS"],
  ["Ownership gate", "PASS"],
  ["Contract gate", "PASS"],
  ["Architecture gate", "PASS"],
  ["React build", "NOT_EXECUTED"],
  ["Physical storage", "NOT_EXECUTED"],
];

export function App() {
  return (
    <main className="shell">
      <header>
        <p className="eyebrow">AEGIS Operations</p>
        <h1>Release Convergence</h1>
        <p>Canonical runtime state is projected here; the SPA is not the state owner.</p>
      </header>
      <section aria-labelledby="release-status">
        <h2 id="release-status">R0.2 delivery gates</h2>
        <div className="grid">
          {releaseRows.map(([label, status]) => (
            <article className="card" key={label}>
              <span>{label}</span>
              <strong>{status}</strong>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
