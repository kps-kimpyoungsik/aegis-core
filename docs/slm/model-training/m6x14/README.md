# M6.x-14 — Local Training Execution / Checkpoint & Evaluation Evidence

Status: `IMPLEMENTED_REFERENCE_NOT_PRODUCTION`

## Goal
Turn the M6.x-13 `READY_NOT_STARTED` Training Run Manifest into an execution-safe, resume-safe and evaluation-gated flow without duplicating canonical runtime ownership.

## Flow
`TrainingRunManifest → ResourcePreflight → ExecutionPlan → CheckpointIntegrity → TaskEvaluation → RegressionCheck → ForgettingCheck → FalsePassVerifier → ObservedLearningUtility → PromotionRecommendationHandoff`.

## Owner resolution
- MODEL: adapter/checkpoint semantics, evaluation semantics.
- NODE: GPU/RAM/disk/runtime/OOM/heartbeat/recovery; canonical owner is `runtime-kernel`.
- KNOWLEDGE: evidence/domain coverage; canonical owner is `portable-brain`.
- ASSET: prompt/skill/tool/workflow schemas.
- Dataset lifecycle remains `data-plane` owned.

## Non-duplication rule
This package does not implement a second scheduler, retry engine, resource lock, recovery kernel, rollback kernel or audit store. The reference orchestrator only emits explicit HANDOFF requests for those responsibilities.

## Promotion boundary
`RECOMMEND_PROMOTION` is evidence only. It never mutates the model registry or production route.
