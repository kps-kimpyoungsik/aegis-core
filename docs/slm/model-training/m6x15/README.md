# M6.x-15 — Local Training Adapter Runtime / Resume-Safe Job Orchestration

Status: `IMPLEMENTED_REFERENCE_NOT_PRODUCTION`

## Goal
Convert an M6.x-14 training/evaluation contract into a resume-safe local training job envelope without creating a second scheduler, resource manager, recovery engine, rollback engine or audit store.

## Flow
`TrainingRunManifest → TrainingJobEnvelope → RuntimeDispatchKernel HANDOFF → RuntimeResourceControlKernel HANDOFF → Training Adapter Invocation → Heartbeat/Progress Evidence → RuntimeRecoveryReplayKernel HANDOFF → Checkpoint Resume → Completion Receipt → Evaluation Trigger Handoff`.

## Runtime-kernel ownership reuse
- Dispatch/idempotent command correlation: `RuntimeDispatchKernel`.
- Admission/backpressure/budget: `RuntimeResourceControlKernel`.
- Checkpoint replay/resume: `RuntimeRecoveryReplayKernel`.
- State transitions: `RuntimeExecutionStateKernel`.
- Rollback: `RuntimeRollbackKernel`.
- Audit/trace: `RuntimeTraceAuditKernel`.

## SLM-owned contracts
- exact training job identity
- training adapter tool name
- artifact directory layout
- progress heartbeat payload
- resume eligibility validation
- bounded retry classification
- deterministic completion receipt
- evaluation trigger handoff

## Hard boundaries
- OOM that requires micro-batch/config changes creates a NEW Training Run.
- Interrupted execution may resume only with the same run fingerprint and an integrity-PASS checkpoint.
- Completion receipt is idempotent evidence, not promotion.
- Stale heartbeat is evidence for runtime recovery; it is not self-retry authority.
- GPU resource locks and scheduler leases remain runtime-kernel responsibilities.
