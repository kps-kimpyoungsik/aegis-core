# AEGIS Agent Entry Point

Canonical repository-wide agent/session constitution is `AGENTS.md`.

Any runtime, agent, automation, or integration that discovers this singular `AGENT.md` file MUST immediately load and obey, in order:

1. `AGENTS.md`
2. `docs/governance/SKILL_AUTO_INVOCATION_GUIDE.md`
3. `docs/governance/EVIDENCE_COLLECTION_SCOPE_GUIDE.md`
4. `docs/governance/SESSION_HANDOFF_DISCOVERY_COMPLETION_GUIDE.md`
5. relevant canonical owner/domain/capability/contract registries, sanitized collection checkpoints, and current Git/source evidence

This file is a compatibility pointer only. It MUST NOT define a second or conflicting constitution.

Skill invocation is autonomous: derive a task signature, discover Skill manifests/indexes, apply exclusion/trigger matching, quality/provenance ranking, required-tool and authority gates, load only selected `detailRef` content, invoke the smallest non-overlapping Skill set, then verify and audit the result.

External evidence collection is scope-aware: declare collection boundaries, preserve collection time separately from source-event time, track pages/continuations/gaps/completeness, resume with overlap and idempotent dedupe, correlate occurrences into causal failure families, and cross-check downstream notifications against authoritative execution/provider state before action.

If Skill discovery/catalog access is unavailable, record `SKILL_CATALOG_NOT_FOUND` or `SKILL_DISCOVERY_NOT_EXECUTED`; never infer that no Skill exists. If evidence collection is partial or its exact scope cannot be reproduced, record the appropriate partial/unknown completeness state and do not advance a durable high-watermark.
