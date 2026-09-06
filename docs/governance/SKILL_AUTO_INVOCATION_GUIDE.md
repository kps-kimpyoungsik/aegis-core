# AEGIS Autonomous Skill Invocation Guide

## Status
ACTIVE_GOVERNANCE

## Purpose
This guide defines how every AEGIS session, agent, automation, and runtime discovers, selects, loads, invokes, verifies, and audits reusable Skills without hard-coding a specific model/runtime implementation.

The canonical runtime primitives already exist in `portable-brain`: `SkillAssetKernel` defines the Skill Manifest contract and trigger/exclusion selection, while `RetrievalKernel` performs selective Memory/Knowledge/Skill retrieval under a context budget. This guide makes those capabilities mandatory operating behavior for repository work.

## Core invariants

- `CONSTITUTION/POLICY/AUTHORITY > SKILL`.
- A Skill never overrides Root Authority, protected surfaces, security policy, verifier, promotion gate, or production permission.
- `EXCLUSION > TRIGGER`.
- `REQUIRED_TOOL_AVAILABLE + AUTHORITY_ALLOWED` are mandatory before invocation.
- Do not load every Skill. Load manifest/index metadata first; load `detailRef` only after trigger selection.
- Prefer existing reusable Skill/Asset before inventing a new procedure.
- A Skill result is execution assistance, not completion evidence.
- Candidate Skills are never silently treated as trusted canonical behavior.
- Deterministic repeated Skill work should evolve toward Validator/Library/Component/Service instead of remaining repeated LLM work.

## Canonical Skill Manifest

Use the existing `SkillAssetKernel.SkillManifest` fields as the canonical contract:

`skillId, purpose, triggers, exclusions, inputs, outputs, requiredTools, authority, estimatedContextCost, qualityTier, version, provenanceRef, detailRef`

Quality tiers are:

`CANDIDATE -> VALIDATED -> TRUSTED -> CANONICAL`

## Mandatory autonomous invocation loop

At task intake, continuation, wake-up, or before a substantial new subtask:

`TASK SIGNATURE -> CONSTITUTION/POLICY/AUTHORITY CHECK -> SKILL INDEX/MANIFEST DISCOVERY -> TRIGGER/EXCLUSION MATCH -> QUALITY/PROVENANCE CHECK -> TOOL/authority GATE -> CONTEXT BUDGET/RANK -> LOAD detailRef ONLY FOR SELECTED SKILLS -> INVOKE -> VERIFY OUTPUT -> AUDIT/PROVENANCE -> CONTINUE`

### 1. Task signature

Derive a compact task signature from the current goal, domain, failure fingerprint, capability, risk, required tools, expected output, and current lifecycle state.

### 2. Skill discovery

Search the current canonical Skill registry/index/manifests and existing Skill assets before creating a new procedure. If a physical catalog/index exists, inspect only manifest/index metadata first.

If the catalog is unavailable in the active canonical source/runtime, record `SKILL_CATALOG_NOT_FOUND` or `SKILL_DISCOVERY_NOT_EXECUTED`; do not pretend that no Skill exists. Fall back to already verified repository procedures and continue fail-closed where authority/risk requires it.

### 3. Trigger and exclusion

A Skill is eligible only when at least one trigger matches the task signature and no exclusion matches. An exclusion always suppresses invocation even if a trigger also matches.

### 4. Quality and provenance

Prefer higher quality tier, then lower estimated context cost, then stable skill identity, consistent with `SkillAssetKernel.selectTriggered`.

Operational use rules:

- `CANONICAL`: may be selected automatically within its declared authority and tool contract.
- `TRUSTED`: may be selected automatically, but preserve version/provenance in evidence.
- `VALIDATED`: may be selected automatically for bounded reversible work; high-risk use still requires the normal authority/security gate.
- `CANDIDATE`: advisory/evaluation only by default. It must not silently change canonical policy, security, production, or promotion decisions. Candidate evaluation requires explicit evidence and regression comparison before promotion.

### 5. Required tools and authority

Before loading or invoking the detailed Skill procedure, verify every declared `requiredTools` dependency is available and the declared `authority` is compatible with the current session/tool authority.

If a required tool is unavailable: `SKILL_BLOCKED_TOOL`.

If authority is insufficient or ambiguous: `SKILL_BLOCKED_AUTHORITY` and fail closed for mutations/high-risk actions.

Never inherit authority from another Skill, agent, dependency, relation, branch, issue, or parent workstream.

### 6. Selective detail loading

Manifest/index metadata is discovery context. `detailRef` is execution context. Load detailed Skill instructions only for selected Skills that pass trigger/exclusion, quality, tool, authority, and context-budget gates.

This preserves the AEGIS `Minimum Relevant Context` rule and prevents broadcast/context flooding.

### 7. Invocation and composition

Prefer the smallest set of Skills that covers the task. Do not invoke overlapping Skills merely because several triggers match.

Decision order:

`REUSE ONE SKILL -> COMPOSE NON-OVERLAPPING SKILLS -> ADAPT EXISTING SKILL -> HANDOFF -> CREATE CANDIDATE SKILL`

If two selected Skills conflict in authority, output contract, state ownership, or side effects, stop with `SKILL_CONFLICT` and resolve canonical ownership before execution.

### 8. Verification

After Skill invocation, verify its outputs using the task's normal verifier/validator/evidence requirements. Skill invocation does not waive tests, security gates, exact-head validation, acceptance criteria, rollback, or completion audit.

Record at minimum:

- task signature;
- selected Skill IDs/versions/quality tiers;
- trigger and exclusion decision;
- required-tool availability;
- authority decision;
- loaded `detailRef` values;
- context cost/budget when available;
- execution result (`EXECUTED/PASS`, `EXECUTED/FAIL`, `NOT_EXECUTED`, `BLOCKED`);
- verifier/evidence references;
- provenance and rollback point when applicable.

## Skill creation/evolution gate

Do not create a new Skill because one session used a useful prompt once.

`Repeated Work -> Failure/Success Pattern -> Existing Skill/Asset Search -> Stable? -> Candidate Skill/Scaffold -> Representative Evaluation -> Target + Held-out Regression -> Promote/Reject`

Repeated deterministic behavior should be implemented as software assets such as policy, validator, library, component, or service while retaining provenance back to the original Skill/experience.

## Session-level autonomous behavior

Every substantive session MUST autonomously perform Skill discovery when a reusable procedure could materially improve correctness, reliability, security, recovery, maintainability, or repeated execution efficiency. The user does not need to name a Skill explicitly.

A session MUST also avoid unnecessary Skill invocation for trivial/simple work where direct execution is safer and cheaper.

Before a new implementation path is invented, the session should be able to state:

`Skill Discovery: MATCHED / NO_MATCH_AFTER_DISCOVERY / CATALOG_NOT_FOUND / BLOCKED`

and, for every selected Skill:

`Skill ID | Version | Quality | Trigger | Required Tools | Authority | Invocation State | Evidence`

## Canonical operating loop

`OBSERVE -> UNDERSTAND -> RETRIEVE(MEMORY/KNOWLEDGE/SKILL) -> SELECT -> AUTHORITY/TOOL GATE -> PLAN -> INVOKE/EXECUTE -> VERIFY -> COMPARE -> REMEMBER -> PROMOTE/REJECT/ROLLBACK -> ADAPT`
