# AEGIS Information Tree Standard v0.1

Status: CANDIDATE / repository-wide information-organization rule

## Purpose
All newly created Skill/knowledge/procedural information assets must be routed through a bounded hierarchy before creation. The hierarchy prevents flat-folder sprawl, arbitrary nesting, duplicate ownership, and context explosion.

## Canonical five-depth tree
`skills` is depth 0. Managed folders below it use exactly five semantic levels:

```text
skills/
  {field}/             # L1 분야: software-engineering, security, data, ai, product...
    {area}/            # L2 영역: operations, backend, frontend, database, governance...
      {relation}/      # L3 관계: failure-analysis, dependency, recovery, integration...
        {module}/      # L4 모듈: adaptive-error-intelligence, tenant-isolation...
          {unit}/      # L5 단위: service-*, resource-*, page-*
```

Example:
`portable-brain/skills/software-engineering/operations/failure-analysis/adaptive-error-intelligence/service-analysis/`

## Depth compression rule
Do not create L6+ directories for semantic decomposition. When more detail is required, integrate it at L5 using files, manifests, tags, indexes, relations, aliases, and resource/page descriptors. Split a L5 unit only when it has a distinct canonical owner or independently deployable lifecycle; the split still becomes another sibling L5 unit, not a deeper tree.

## L5 unit kinds
- `service-*`: executable capability, workflow, adapter, agent service, validator, engine.
- `resource-*`: dataset, policy, schema, evidence pack, model, configuration, reference corpus.
- `page-*`: UI/UX page, operational view, dashboard, documentation surface.

## Mandatory creation gate
Before any managed information is created:
1. classify Field, Area, Relation, Module, UnitKind and UnitName;
2. resolve canonical owner and existing matching/related nodes;
3. classify action as REUSE / ADAPT / COMPOSE / HANDOFF / MERGE-SUPERSEDE / CREATE;
4. generate the canonical path through `InformationTreeGovernanceKernel` or an equivalent deterministic validator;
5. reject blank, traversal, arbitrary-depth, or unclassified paths;
6. attach provenance and relation metadata;
7. create information only after the route is valid.

`UNCLASSIFIED` is a temporary intake state, not an allowed promoted storage location.

## Relationship rule
Folder location expresses primary ownership/classification only. Cross-cutting relationships are represented as metadata edges, not duplicated folders or copied assets. Recommended edge types: `DEPENDS_ON`, `USES`, `PRODUCES`, `CONSUMES`, `VALIDATES`, `RECOVERS`, `SECURES`, `OWNS`, `RELATED_TO`, `SUPERSEDES`, `DERIVED_FROM`.

## Naming
Use lowercase kebab-case. Names must be stable semantic names, not dates, session names, ticket numbers, or model/provider names unless those are the actual domain object. Avoid `misc`, `temp`, `new`, `other`, and person/session-specific buckets.

## Root metadata exception
Only taxonomy/index/governance metadata may exist directly under `skills/`, e.g. `README.md`, `taxonomy.json`, `INDEX.json`. Executable or procedural skill content must live in the five-depth tree.

## Lifecycle
`INTAKE -> CLASSIFY -> ROUTE -> DEDUPE -> CREATE/REUSE -> VALIDATE -> INDEX -> EVOLVE -> MERGE/SUPERSEDE`.

When sibling units become too fragmented, consolidate by canonical responsibility and preserve aliases/provenance. When a unit becomes too broad, split into sibling L5 units by owner/lifecycle, never by arbitrary deeper nesting.

## Invariants
- No promoted unclassified information.
- No semantic nesting beyond five levels below the managed root.
- No duplicated asset solely to express a relationship.
- No new folder when metadata/edge/index can express the distinction.
- No `misc` dumping ground.
- No path traversal or unstable session/date hierarchy.
- No information creation before route/dedup/owner checks.
- No deletion during consolidation without alias/provenance/supersession evidence.
