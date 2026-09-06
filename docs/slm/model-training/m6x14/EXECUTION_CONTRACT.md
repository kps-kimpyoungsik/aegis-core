# M6.x-14 Execution Contract

## 1. Resource Preflight
Inputs: GPU presence, free/total VRAM, free disk, runtime compatibility, estimated peak VRAM.

States: `PASS`, `WARN_TIGHT_MEMORY`, `HOLD_INSUFFICIENT_VRAM`, `HOLD_INSUFFICIENT_DISK`, `HOLD_RUNTIME_MISMATCH`.

Preflight does not acquire resources. Resource acquisition is a HANDOFF to `runtime-kernel/RuntimeResourceControlKernel`.

## 2. Resume/Recovery
Resume is permitted only if `training_run_id`, `run_fingerprint`, dataset snapshot fingerprint, model/tokenizer fingerprints and canonical config hash are unchanged. A config change creates a new Training Run identity.

Interrupted recovery is a HANDOFF to `RuntimeRecoveryReplayKernel`; this contract only validates resume eligibility.

## 3. Training Health
Observe loss, learning rate, grad norm, NaN/Inf, OOM count, skipped batches, throughput, step latency and peak GPU memory.

States: `HEALTHY`, `WARN_PLATEAU`, `WARN_GRADIENT_SPIKE`, `FAIL_NAN_INF`, `FAIL_OOM_REPEATED`, `FAIL_NO_PROGRESS`.

## 4. Checkpoint Integrity
A checkpoint passes only when run fingerprint, adapter config/weight hashes, file manifest and step metadata are consistent. Checkpoint creation alone is never treated as evaluation success.

## 5. Evaluation Gate
Candidate and baseline use the same evaluation policy. Checks are performed in this order:
1. checkpoint integrity
2. task evaluation
3. critical regression
4. catastrophic forgetting
5. False PASS rate
6. observed learning utility
7. promotion recommendation handoff

## 6. Protected Eval
Protected-eval raw prompts/answers are not returned to the dynamic material generator. Only aggregate capability/failure signals may cross the boundary.

## 7. Promotion Boundary
The output is `READY_FOR_GOVERNANCE_REVIEW`; actual promotion/deployment is outside M6.x-14.
