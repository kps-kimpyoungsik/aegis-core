# M6.x-15 Training Job Contract

## TrainingJobEnvelope
Required fields:
- `job_id`
- `training_run_id`
- `run_fingerprint`
- `epoch`
- `command_id`
- `attempt`
- `max_attempts`
- `adapter_tool_name = slm.local_training_adapter`
- `artifact_root`
- `resume_checkpoint_ref` optional
- `state = READY_FOR_RUNTIME_HANDOFF`

## Dispatch mapping
The SLM envelope maps to `RuntimeDispatchKernel.Command`:
- `executionId = job_id`
- `commandId = command_id`
- `epoch = epoch`
- `toolName = slm.local_training_adapter`

SLM does not implement duplicate-command rejection, active invocation exclusion or stale-epoch result correlation; those remain in `RuntimeDispatchKernel`.

## Resource admission
SLM emits required usage/budget observations but admission is evaluated by `RuntimeResourceControlKernel.ControlContext`.

## Resume eligibility
Resume requires all of:
- same `training_run_id`
- same `run_fingerprint`
- checkpoint `integrity_state = PASS`
- same execution epoch expected by recovery contract
- immutable dataset/model/tokenizer/config identity unchanged

Any training config mutation requires a new Training Run identity.

## Retry classification
- `INTERRUPTED`, transient runtime/storage → `RETRY_SAME_RUN` if runtime-kernel recovery accepts.
- `OOM` requiring configuration change → `NEW_RUN_REQUIRED`.
- policy/identity/checkpoint-integrity/NaN-Inf → `BLOCK`.
- attempt >= max_attempts → `EXHAUSTED`.

Retry classification is advisory evidence. Scheduling/retry execution belongs to runtime-kernel.

## Heartbeat
Progress evidence includes:
`job_id`, `run_fingerprint`, `epoch`, `sequence`, `global_step`, `checkpoint_ref`, `state`, `observed_at`.

A stale heartbeat generates recovery evidence; it does not independently acquire a new lease.

## Completion receipt
Receipt fingerprint binds:
`job_id + training_run_id + run_fingerprint + final_checkpoint_hash + evaluation_handoff_ref`.

State: `COMPLETED_NOT_PROMOTED`.
