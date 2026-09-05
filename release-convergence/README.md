# AEGIS Release Convergence

[AEGIS CONFLICT GUARD — ACTIVE]

Canonical owner for customer-open release convergence. This module does not own Runtime Kernel, Portable Brain, Data Plane, Harness, or Scaffold behavior; it only evaluates their release evidence and coordinates promotion/rollback decisions.

## GA gates

G0 Source/Architecture → G1 Clean Build → G2 Artifact/Supply Chain → G3 Security → G4 Data Safety → G5 Staging → G6 Reliability/Performance → G7 Rollback → G8 Production Approval.

All gates are fail-closed. `NOT_EXECUTED`, `BLOCKED`, or `FAIL` cannot promote production.
