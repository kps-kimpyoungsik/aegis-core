# AEGIS SLM Ownership Boundaries

## MODEL
Owns model behavior, adapter configuration, training policy, checkpoint/model evaluation semantics.

## NODE
Owns local worker, GPU/CPU/RAM resource observation, runtime execution, OOM/restart/recovery mechanics.

## KNOWLEDGE
Owns source/evidence/RAG knowledge, provenance, freshness, domain knowledge coverage.

## ASSET
Owns prompts, skills, tool schemas, workflow assets, validation assets.

## Cross-owner rules
- NODE OOM is not MODEL forgetting.
- KNOWLEDGE retrieval miss is not automatic MODEL retraining.
- ASSET/tool schema failure is not automatic MODEL training data.
- Cross-owner evidence may share a parent incident but creates owner-specific child proposals.
- Session ownership never overrides architecture ownership.
