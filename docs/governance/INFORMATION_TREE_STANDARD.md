# AEGIS Information Tree Standard v0.2

Status: CANDIDATE / repository-wide governed-information organization rule

## Purpose
All newly created Skill/knowledge/procedural/resource/page information assets must be routed through a bounded hierarchy before creation. The hierarchy prevents flat-folder sprawl, arbitrary nesting, duplicate ownership, context explosion, and semantic drift.

This taxonomy applies to governed information assets. Application source/package trees, framework conventions, generated build output, and vendor layouts are not forcibly reshaped into this taxonomy unless explicitly adopted by their canonical owner.

## Canonical five-depth tree
`skills` is depth 0. Managed folders below it use exactly five semantic levels:

```text
skills/
  {field}/             # L1 분야
    {area}/            # L2 영역
      {relation}/      # L3 주 관계/맥락
        {module}/      # L4 모듈/Capability
          {unit}/      # L5 service-* | resource-* | page-*
```

Example:
`portable-brain/skills/software-engineering/operations/failure-analysis/adaptive-error-intelligence/service-analysis/`

## Depth compression rule
Do not create L6+ directories for semantic decomposition. When more detail is required, integrate it at L5 using files, manifests, tags, indexes, relations, aliases, and descriptors. Split a L5 unit only when it has a distinct canonical owner or independently managed lifecycle; the split becomes another sibling L5 unit, never a deeper tree.

## L5 unit kinds
- `service-*`: executable capability, workflow, adapter, agent service, validator, engine.
- `resource-*`: dataset, policy, schema, evidence pack, model, configuration, reference corpus.
- `page-*`: UI/UX page, operational view, dashboard, documentation surface.

## Mandatory creation gate
Before any managed information is created:
1. classify Field, Area, Relation, Module, UnitKind and UnitName;
2. resolve canonical owner and search existing matching/related nodes;
3. classify action as REUSE / ADAPT / COMPOSE / HANDOFF / MERGE-SUPERSEDE / CREATE;
4. validate the canonical path through `InformationTreeGovernanceKernel` or an equivalent deterministic validator;
5. reject blank, traversal, arbitrary-depth, forbidden-bucket, or unclassified paths;
6. attach a stable asset ID, owner, provenance, and relation metadata;
7. validate that manifest classification exactly matches the physical path;
8. validate that the asset ID is unique within the managed root;
9. create information only after the route and semantics are valid;
10. index the asset and preserve aliases/supersession links when moving or merging.

`UNCLASSIFIED` is a temporary intake state, not an allowed promoted storage location.

## Manifest semantic contract
Every promoted managed unit must expose at least:

```yaml
id: stable canonical identity
classification:
  field:
  area:
  relation:
  module:
  unit:
  semanticDepth: 5
owner: canonical responsibility owner
relations: []
provenance: {}
```

The manifest is invalid when its classification disagrees with its directory path, when owner/provenance are absent, or when the same canonical ID appears in more than one location.

## Relationship rule
Folder location expresses primary ownership/classification only. Cross-cutting relationships are represented as metadata edges, not duplicated folders or copied assets. Recommended edge types include `DEPENDS_ON`, `USES`, `PRODUCES`, `CONSUMES`, `VALIDATES`, `VALIDATES_WITH`, `RECOVERS`, `SECURES`, `OWNS`, `RELATED_TO`, `SUPERSEDES`, and `DERIVED_FROM`.

## Identity and duplication rule
One canonical asset ID has one canonical physical location. A second context must reference the canonical asset through relation/index/alias metadata rather than copying the asset. Copies created only to make discovery easier are prohibited; improve retrieval/indexing instead.

## Naming
Use lowercase kebab-case. Names must be stable semantic names, not dates, session names, ticket numbers, or model/provider names unless those are the actual domain object. Avoid `misc`, `temp`, `new`, `other`, `unknown`, and person/session-specific buckets.

## Root metadata exception
Only taxonomy/index/governance metadata may exist directly under `skills/`, e.g. `README.md`, `taxonomy.json`, `INDEX.json`. Executable or procedural skill content must live in the five-depth tree.

## Lifecycle
`INTAKE -> CLASSIFY -> SEARCH/DEDUPE -> ROUTE -> CREATE/REUSE -> VALIDATE -> INDEX -> EVOLVE -> MERGE/SUPERSEDE`.

When sibling units become too fragmented, consolidate by canonical responsibility and preserve aliases/provenance. When a unit becomes too broad, split into sibling L5 units by owner/lifecycle, never by arbitrary deeper nesting.

## Branch/work artifact hygiene
Information governance also applies to temporary work artifacts:
- one purpose should converge on one active working branch;
- the active branch must be linked to an Issue or Pull Request when repository work is durable;
- experimental branches must be deleted after their useful changes converge or are rejected;
- duplicate-purpose branches are governance debt and must not become a hidden second source of truth;
- branch cleanup is an operational action and must respect repository authority and open-PR references.

## Validation layers
Use layered validation rather than folder-shape checks alone:
1. syntactic path validation;
2. semantic manifest/path consistency;
3. canonical ID uniqueness;
4. owner/provenance presence;
5. relation syntax and reference integrity where resolvable;
6. duplicate-content/symbol checks where applicable;
7. runtime/domain verification for executable assets.

## Invariants
- No promoted unclassified information.
- No semantic nesting beyond five levels below the managed root.
- No duplicated asset solely to express a relationship.
- No duplicate canonical asset ID.
- No new folder when metadata/edge/index can express the distinction.
- No `misc` dumping ground.
- No path traversal or unstable session/date hierarchy.
- No information creation before route/dedup/owner checks.
- No deletion during consolidation without alias/provenance/supersession evidence.
- No assumption that this taxonomy should override source-language/package conventions outside governed information roots.
