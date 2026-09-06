# AEGIS Agent Entry Point

Canonical repository-wide agent/session constitution is `AGENTS.md`.

Any runtime, agent, automation, or integration that discovers this singular `AGENT.md` file MUST immediately load and obey, in order:

1. `AGENTS.md`
2. `docs/governance/SKILL_AUTO_INVOCATION_GUIDE.md`
3. `docs/governance/SESSION_HANDOFF_DISCOVERY_COMPLETION_GUIDE.md`
4. relevant canonical owner/domain/capability/contract registries and current Git evidence

This file is a compatibility pointer only. It MUST NOT define a second or conflicting constitution.

Skill invocation is autonomous: derive a task signature, discover Skill manifests/indexes, apply exclusion/trigger matching, quality/provenance ranking, required-tool and authority gates, load only selected `detailRef` content, invoke the smallest non-overlapping Skill set, then verify and audit the result.

If Skill discovery/catalog access is unavailable, record `SKILL_CATALOG_NOT_FOUND` or `SKILL_DISCOVERY_NOT_EXECUTED`; never infer that no Skill exists.
