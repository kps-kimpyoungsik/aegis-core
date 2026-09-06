# Adaptive Error Intelligence Skill v0.1

Status: CANDIDATE / NOT CANONICAL UNTIL VALIDATED
Owner candidate: Portable Brain procedural intelligence; domain execution remains with each canonical owner.

## 1. Purpose

This skill prevents shallow error handling. It must not stop at the immediately visible message, failed file, or last failing step. It reconstructs the state before failure, traverses structural and temporal relationships, distinguishes primary from secondary failures, and produces a bounded prevention candidate tied to evidence.

Canonical loop:

```text
OBSERVE FAILURE
→ RECONSTRUCT BASELINE
→ CLASSIFY COMPLEXITY/IMPACT
→ EXPAND RELATIONSHIPS
→ LOCALIZE
→ BUILD CAUSAL HYPOTHESES
→ VERIFY
→ ROUTE TO CANONICAL OWNER
→ DESIGN RECURRENCE PREVENTION
→ REGRESSION TEST
→ CAPTURE EXPERIENCE
→ PROMOTE/REJECT LEARNING
```

The skill is compatible with AEGIS Evidence Before Belief, Change Impact Graph, Work Provenance, Failure Memory, Learnable Scaffold, Self-Harness, protected trust boundaries, and fail-closed promotion.

## 2. Non-negotiable invariants

- A surface error is evidence, not root cause.
- Do not create a global rule from one anecdotal failure.
- Do not retry deterministic failures as a permanent fix.
- Do not let secondary failures mask the primary failure.
- Do not inspect only changed files when incoming/outgoing dependencies can be resolved.
- Do not use LLM inference where deterministic parsing, graph traversal, test evidence, or data-flow analysis can answer first.
- Do not let self-learning modify protected trust surfaces.
- Do not promote a prevention rule without target improvement and held-out regression evidence.
- Preserve provenance for every learned rule, validator, scaffold, and rejected candidate.

## 3. Adaptive classification vector

Every incident is classified before deciding how deeply to investigate.

```yaml
error_profile:
  surface_severity: INFO|WARN|ERROR|CRITICAL
  complexity: SIMPLE|COMPOUND|SYSTEMIC|UNKNOWN
  impact_radius: LOCAL|MODULE|DOMAIN|CROSS_DOMAIN|RELEASE|PRODUCTION
  dependency_depth: D0|D1|D2|D3_PLUS|UNKNOWN
  persistence: EPHEMERAL|RETRY_PERSISTENT|REVISION_PERSISTENT|STATE_PERSISTENT|HISTORICAL_PATTERN
  relationship_density: ISOLATED|SPARSE|CONNECTED|HUB|UNKNOWN
  recurrence: FIRST_OBSERVED|SIMILAR_HISTORY|REPEATED|CLUSTERED
  security_risk: NONE|LOW|MEDIUM|HIGH|CRITICAL
  recoverability: REVERSIBLE|RECOVERABLE_WITH_STATE|IRREVERSIBLE_RISK|UNKNOWN
  evidence_confidence: LOW|MEDIUM|HIGH|VERIFIER_CONFIRMED
```

`SIMPLE` is allowed only when the error is deterministically localized, low-impact, dependency-bounded, and no prior contradictory history exists. Otherwise investigation escalates.

## 4. Investigation depth policy

### L0 — Surface triage
Read the notification, error code, failing test/job, timestamp, revision, environment, and direct changed paths.

### L1 — Temporal reconstruction
Recover before/after state, base/head revisions, recent related commits, previous attempts, environment drift, dependency/version drift, and known passing baseline.

### L2 — Structural expansion
Build an Impact Set using imports, calls, implements/extends, reads/writes, schemas, configuration, tests, workflows, owners, events, deployments, and historical failure relationships.

Use bounded graph traversal rather than unrestricted context expansion.

### L3 — Control/data-flow localization
When structure is insufficient, inspect execution paths, source-to-sink data flow, def-use chains, transaction boundaries, event propagation, and state transitions.

Preferred deterministic techniques when available:
- AST/CST and symbol graph
- call graph / control-flow graph
- static or dynamic program slicing
- def-use/data-flow analysis
- taint/path queries for security-relevant flows
- coverage mapping

### L4 — Historical/provenance correlation
Retrieve failure memory and related episodes by exact signature, structural similarity, semantic similarity, revision family, component owner, and causal mechanism. Compare successful and failed prior repairs.

### L5 — Statistical/runtime localization
When telemetry exists, correlate errors with latency, resource, queue, dependency, and deployment changes. Candidate techniques include EWMA/CUSUM for change detection, robust z-score/quantile deviation, Isolation Forest for multivariate anomaly candidates, and trace/log clustering. These produce evidence candidates, not automatic root-cause truth.

### L6 — Research augmentation
If local evidence is insufficient, search standards, official documentation, papers, security research, production postmortems, and current engineering practice. External knowledge must remain distinct from repository evidence.

### L7 — LLM causal synthesis
The LLM combines the minimum evidence package into ranked causal hypotheses. It must state evidence, counter-evidence, unknowns, and a falsification test for each major hypothesis.

## 5. Relationship-aware localization algorithms

### 5.1 Weighted impact graph

Represent repository/runtime entities as nodes and relationships as typed edges.

Example edges:

```text
CALLS, IMPORTS, READS, WRITES, CONFIGURES, TESTS,
DEPENDS_ON, PRODUCES, CONSUMES, DEPLOYS, MIGRATES,
OWNS, DERIVED_FROM, FAILED_WITH, VALIDATED_BY
```

Traversal priority is not pure graph distance. Rank candidate nodes conceptually by:

```text
ImpactScore =
  ChangeProximity
× EdgeCriticality
× FailureCorrelation
× RuntimeReachability
× HistoricalRecurrence
× SecurityWeight
× OwnershipRelevance
× Freshness
- ExplorationCost
```

Stop expansion when the evidence budget is exhausted, confidence converges, or a hard authority boundary is reached.

### 5.2 Spectrum-based fault localization
When test coverage and pass/fail matrices are available, deterministic suspiciousness metrics such as Ochiai or Tarantula may rank executed statements/functions. They are localization signals only and must be combined with structural and causal evidence.

### 5.3 Delta debugging
For large change sets or configurations, use bounded `ddmin`-style reduction to find a minimal failure-inducing subset when execution cost and safety permit.

### 5.4 Program slicing
Use backward slicing from an observed bad value/state and forward slicing from changed definitions or external inputs to narrow causal paths.

### 5.5 Failure fingerprinting
Create layered fingerprints:

```text
exact = normalized error + verifier + revision context
structural = component + execution path + causal behavior
semantic = embedding/LLM representation of mechanism
```

Use exact/hash or MinHash/SimHash-style matching before semantic similarity. Do not cluster solely by identical final error labels.

## 6. AI/LLM fault-finding mechanism

The LLM is a routing and causal-reasoning layer over deterministic evidence, not the only debugger.

```text
Incident
→ Evidence collector
→ Structural/temporal retriever
→ Deterministic localizers
→ Candidate fusion
→ LLM localizer
→ LLM causal suggester
→ bounded fix/prevention candidate
→ deterministic/independent verifier
```

Recommended reasoning roles are logically separated even if one model executes them:

- Localizer: where and through which relationships can the failure originate?
- Causal analyst: what mechanism explains the evidence and what evidence would falsify it?
- Prevention designer: what smallest reusable control prevents recurrence?
- Verifier/Judge: independent or deterministic confirmation; not controlled by the prevention designer.

Multi-agent execution is optional and only justified for cross-domain or parallelizable investigations. Simple bounded failures should remain single-agent/program first.

## 7. Research-derived design hooks

These are external research inputs, not AEGIS constitutional truth. Adopt only after local validation.

- ARISE (2026), "A Repository-level Graph Representation and Toolset for Agentic Fault Localization and Program Repair": multi-granularity repository graph plus statement-level def-use/data-flow slicing; useful as evidence for making data-flow slicing a first-class query primitive.
  https://arxiv.org/abs/2605.03117
- SGAgent (2026), "Suggestion-Guided LLM-Based Multi-Agent Framework for Repository-Level Software Repair": localize → suggest → fix separation and repository KG tooling; supports separating localization from repair generation.
  https://arxiv.org/abs/2602.23647
- CoSIL (2025), "Software Issue Localization via LLM-Driven Code Repository Graph Searching": iterative call-graph search with context pruning; supports bounded multi-hop structural retrieval.
  https://arxiv.org/abs/2503.22424
- KGCompass (2025), "Enhancing Repository-Level Software Repair via Repository-Aware Knowledge Graphs": repository-aware KG and path-guided repair; supports issue/PR/code relationship traversal and incremental graph updates.
  https://arxiv.org/abs/2503.21710
- GitHub CodeQL: code-as-data, data-flow/path queries, security/error scanning, and custom framework modeling; candidate deterministic analyzer for supported languages and workflows.
  https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning

No paper result is promoted directly to a canonical AEGIS rule. Research produces `Candidate Technique`; representative AEGIS workloads and regression gates decide adoption.

## 8. Evidence package

```yaml
error_evidence_package:
  incident_id:
  observed_at:
  baseline_revision:
  failing_revision:
  changed_paths:
  surface_errors:
  primary_failure_candidate:
  secondary_failures:
  execution_paths:
  impacted_nodes:
  dependency_paths:
  dataflow_paths:
  historical_matches:
  runtime_correlations:
  external_research_refs:
  evidence_confidence:
  unknowns:
```

## 9. Causal hypothesis contract

```yaml
causal_hypothesis:
  id:
  mechanism:
  explains:
  evidence_for:
  evidence_against:
  affected_scope:
  recurrence_condition:
  falsification_test:
  confidence:
```

A repair must not be proposed as canonical until the hypothesis is testable.

## 10. Prevention ladder

Prefer the smallest durable level that can deterministically prevent recurrence:

```text
Lesson
→ Skill/Scaffold
→ Typed Rule
→ Validator
→ Test/Negative Corpus
→ Shared Library
→ CI/Policy Gate
→ Service/System
```

Examples:
- repeated stale-base failure → exact-head/stale-base validator
- repeated schema consumer drift → dependency-impact contract guard
- repeated retry storm → retry taxonomy + circuit breaker
- repeated cross-tenant access regression → DB-boundary negative corpus / policy validator

## 11. Autonomous learning loop

Each execution records:

```yaml
error_experience:
  task_signature:
  error_profile:
  evidence_used:
  analysis_path:
  tools_used:
  hypotheses:
  selected_fix:
  validation:
  false_positives:
  false_negatives:
  latency:
  cost:
  recurrence_result:
  human_corrections:
  lessons:
```

Promotion stages:

```text
Episode
→ Failure Pattern Candidate
→ Clustered Mechanism
→ Candidate Lesson
→ Candidate Skill/Rule/Validator
→ Shadow Evaluation
→ Target + Held-out Regression
→ Promote / Reject / Defer
```

Rejected candidates are stored with rejection reason to prevent repeated bad proposals.

## 12. Self-awareness triggers

The skill must autonomously recognize when its own current analysis strategy is insufficient.

Escalation triggers include:
- confidence remains low after the current depth
- failure crosses more than one canonical domain
- multiple independent symptoms share a revision/time window
- high-centrality/hub dependency is involved
- security/authority/data-integrity boundary is touched
- previously fixed signature recurs
- fix requires broader changes than the original Impact Set predicted
- validation passes target test but fails held-out behavior
- new model/tool/parser/version changes the analysis environment

Self-review output:

```yaml
analysis_self_assessment:
  coverage_sufficient:
  missing_evidence:
  unexplored_relationships:
  suspected_blind_spots:
  next_depth:
  cost_budget:
  escalation_required:
```

## 13. Self-improvement boundary

The skill may adapt:
- retrieval order
- graph expansion depth
- localizer selection
- evidence weighting
- tool ordering
- context packaging
- retry/stop strategy
- research routing

It may not autonomously modify:
- Constitution
- Root Authority
- independent verifier
- audit ledger
- security policy
- secret boundary
- production permission
- promotion criteria root

Every strategy change is a versioned candidate and must preserve passing behavior.

## 14. Quality metrics

Track at least:
- root-cause precision / verified-cause rate
- file/function/line localization recall where ground truth exists
- mean evidence size
- graph hops explored
- false-positive rate
- recurrence rate after fix
- regression rate caused by fix
- time/cost to verified cause
- repeated manual reasoning eliminated
- candidate-rule acceptance/rejection rate
- rollback rate

Optimize the vector, not a single score.

## 15. Completion states

```text
OBSERVED
TRIAGED
STRUCTURALLY_EXPANDED
ROOT_CAUSE_CANDIDATE
ROOT_CAUSE_CONFIRMED
PREVENTION_CANDIDATE
VALIDATION_PENDING
RECURRENCE_TESTED
RECURRENCE_PREVENTED
LEARNING_CANDIDATE
PROMOTED / REJECTED / DEFERRED
```

`RECURRENCE_PREVENTED` requires reproducible validation; one successful retry is insufficient.

## 16. Invocation checklist

1. Refresh exact latest source/baseline when repository work is involved.
2. Build error profile.
3. Search exact/structural failure memory.
4. Reconstruct before/after state.
5. Build bounded Impact Set.
6. Traverse control/data/dependency paths as needed.
7. Separate primary/secondary failures.
8. Rank and falsify causal hypotheses.
9. Resolve canonical owner.
10. Select minimum durable prevention level.
11. Validate target and held-out behavior.
12. Record episode, failure memory, rejected hypotheses, and recurrence test.
13. Review whether a repeated stable mechanism should become a deterministic asset.

## 17. Master directive

AEGIS shall treat error analysis as a relationship- and evidence-driven adaptive capability. Immediate symptoms are only entry points. The system must reconstruct previous measured state, traverse relevant temporal/structural/data/control/dependency/provenance relationships, reuse prior failure knowledge, and progressively escalate from low-cost deterministic analysis to graph, statistical, research, and LLM reasoning only as needed. Repeated causal mechanisms are generalized into reusable skills, rules, validators, tests, libraries, or gates only after representative and held-out verification. Learning changes the evolvable analysis strategy, never the protected trust boundary.
