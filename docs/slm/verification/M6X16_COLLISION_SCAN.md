# M6.x-16 Collision / Ownership Scan

Result: `NO_CANONICAL_OWNER_OVERRIDE`

## Reuse / handoff
- Scheduling / duplicate dispatch / active invocation / epoch correlation → `RuntimeDispatchKernel`
- Resource admission / backpressure / execution budgets → `RuntimeResourceControlKernel`
- Checkpoint resume/replay / duplicate effect suppression → `RuntimeRecoveryReplayKernel`
- Runtime rollback → `RuntimeRollbackKernel`
- Canonical trace/audit → `RuntimeTraceAuditKernel`
- Dataset lifecycle → `data-plane`
- Knowledge/evidence retrieval → `portable-brain`
- Production approval/release → `product/release-convergence`

## M6.x-16 owned surface only
- exact TrainingRun/Job → TRL/PEFT argument translation
- exact model/tokenizer revision loading
- QLoRA adapter bridge
- safe-stop Trainer callback contract
- adapter artifact manifest/hash
- framework dependency-version capture
- execution receipt + evaluation handoff

## Explicit non-ownership
No scheduler, queue, lease, resource lock, recovery engine, retry engine, checkpoint store, rollback executor, dataset registry, knowledge store, model promotion registry or production router is introduced.

## Main-branch drift control
The previous SLM integration branch was found 44 commits behind `main`; M6.x-16 is therefore rebuilt on the then-current canonical main before integration.
