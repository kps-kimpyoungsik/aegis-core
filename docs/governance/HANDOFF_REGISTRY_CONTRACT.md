# AEGIS Canonical Handoff Registry Contract

## Status
CANDIDATE

## Purpose
`product/contracts/handoff-registry.json` is the deterministic first-hop index for cross-session handoff discovery. It complements, and does not replace, ownership/domain/capability/workstream registries, Failure Memory, GitHub Issues/PRs, workflow evidence, or the repository-root `AGENTS.md`.

## Discovery order

`DOMAIN -> OWNER_RESPONSIBILITY -> CAPABILITY -> FAILURE_FINGERPRINT -> WORKSTREAM/PR -> ISSUE_REF -> FALLBACK_GITHUB_SEARCH`

A zero registry match is `SEARCH_NOT_FOUND_REQUIRES_FALLBACK`, never proof of no error.

## Identity model

- `id`: stable AEGIS work-item identity.
- `failureFingerprint`: stable causal family identity unless later evidence proves a split/merge.
- `issueRef`: mutable concrete work-instance reference; never the permanent domain identity.
- `domain`: must exist in `domain-registry.json`.
- `ownerResponsibility`: must exist in `ownership-registry.json`.
- `capabilities`: every value must exist in `capability-registry.json`.
- `blockedBy`: references other handoff item IDs and must never self-reference.

## Completion model

`HANDOFF_CREATED != COMPLETED`.

Completion is audited outside the registry and requires acceptance criteria, exact-state validation/verifier evidence, no unresolved blocker, resolved canonical ownership, required regression/security/release evidence, provenance, and rollback data where applicable.

## Retry model

Retry decisions are explicit per item. Deterministic failure requires a relevant fix before rerun. External blockers suppress retry until external-state change and then require a cheap canary. Dependency blockers remain blocked until dependency evidence changes. Blind/infinite retry is prohibited.

## Deterministic resolver

`product/tools/handoff-discovery.mjs` validates registry referential integrity and resolves active work without requiring a known Issue number.

Examples:

```bash
node product/tools/handoff-discovery.mjs --domain=data-plane
node product/tools/handoff-discovery.mjs --owner=responsibility
node product/tools/handoff-discovery.mjs --fingerprint=billing-lock
node product/tools/handoff-discovery.mjs --query=workflow-fanout
```

If no match is found, callers MUST execute the fallback discovery sequence from `docs/governance/SESSION_HANDOFF_DISCOVERY_COMPLETION_GUIDE.md`.

## Promotion gate

The registry remains `CANDIDATE` until deterministic product tests and exact-head CI execute successfully. GitHub Actions pre-run account/billing failures are `NOT_EXECUTED` evidence and cannot promote this contract.
