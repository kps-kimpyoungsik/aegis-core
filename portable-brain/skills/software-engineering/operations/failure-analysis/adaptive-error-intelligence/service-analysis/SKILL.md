# Adaptive Error Intelligence Skill v0.2

Status: CANDIDATE / executable Portable Brain skill
Canonical classification: `software-engineering / operations / failure-analysis / adaptive-error-intelligence / service-analysis`

## Purpose
Diagnose and prevent software/runtime/CI/data/security/release failures without stopping at the visible error. Reconstruct prior state, traverse dependency/control/data/provenance relationships, prefer deterministic evidence, escalate analysis depth only when needed, and learn reusable prevention mechanisms from verified experience.

## Canonical loop
`OBSERVE -> RECONSTRUCT -> CLASSIFY -> RELATIONSHIP EXPANSION -> LOCALIZE -> CAUSAL HYPOTHESIS -> FALSIFY/VERIFY -> OWNER ROUTE -> PREVENT -> REGRESSION -> EXPERIENCE -> PROMOTE/REJECT LEARNING`

## Adaptive investigation depth
- L0 surface evidence
- L1 temporal/baseline reconstruction
- L2 dependency/impact graph
- L3 control/data-flow and program slicing
- L4 failure/provenance history
- L5 telemetry/statistical correlation
- L6 external standards/research
- L7 LLM causal synthesis

Use the minimum depth that yields sufficient evidence. Security, authority, cross-domain, high-centrality, repeated, or low-confidence incidents escalate automatically.

## Deterministic-first mechanisms
Prefer exact/structural failure fingerprints, AST/symbol/call graphs, def-use/data-flow, path/taint analysis, program slicing, coverage/SBFL, delta debugging, state-machine checks, and runtime telemetry before generative reasoning. LLM output is hypothesis/evidence synthesis, not verifier truth.

## Learning loop
`Episode -> Failure Pattern -> Clustered Mechanism -> Candidate Lesson/Rule/Validator -> Shadow Evaluation -> Target + Held-out Regression -> PROMOTE/REJECT/DEFER`.

A single failure never creates a global rule. Rejected proposals remain provenance. Protected Constitution/Authority/Verifier/Audit/Security/Secrets/Promotion/Production roots are not self-modifiable.

## Information-tree rule
Any new knowledge, policy, service, resource, or page produced by this skill must first pass `InformationTreeGovernanceKernel`. Do not create L6+ semantic directories: merge detail into the L5 unit through files/metadata/relations or create a sibling L5 unit with distinct owner/lifecycle.

## Research hooks
External research (e.g. repository graphs, program slicing, issue localization, CodeQL path/data-flow analysis) is advisory until representative AEGIS validation. Research cannot directly become canonical policy.

## Completion
`RECURRENCE_PREVENTED` requires reproducible target and held-out validation. A retry success, platform recovery, or isolated PASS is insufficient.
