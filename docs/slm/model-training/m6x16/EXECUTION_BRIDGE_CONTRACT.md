# M6.x-16 Execution Bridge Contract

## 1. Inputs
Required exact pins:
- `training_run_id`
- `run_fingerprint`
- `dataset_snapshot_ref/fingerprint`
- `model_id` + `model_revision`
- `tokenizer_id` + `tokenizer_revision`
- canonical training config hash
- job epoch / attempt
- output artifact root
- optional exact `resume_checkpoint_path`

## 2. Framework boundary
AEGIS Core emits canonical contracts only. The outbound adapter maps them to:
- `transformers.BitsAndBytesConfig`
- `transformers.AutoModelForCausalLM`
- `transformers.AutoTokenizer`
- `peft.prepare_model_for_kbit_training`
- `peft.LoraConfig`
- `trl.SFTConfig`
- `trl.SFTTrainer`

## 3. QLoRA defaults
Reference plan:
- 4-bit base model
- NF4 quantization
- double quantization enabled
- BF16 compute when supported by the preflight profile
- frozen quantized base
- LoRA adapter trainable parameters
- gradient checkpointing

The adapter records the actual dependency/runtime versions in the execution receipt.

## 4. Resume rule
`trainer.train(resume_from_checkpoint=<exact path>)` is allowed only after M6.x-15 resume identity/integrity gate passes. Config changes create a new Training Run.

## 5. Signal-safe stop
OS signal handler must not perform heavyweight checkpoint I/O. It only sets `stop_requested=true`. At the next Trainer callback boundary the adapter requests `should_save=true` and `should_training_stop=true`.

## 6. Progress evidence
Callback emits bounded metadata only:
`run_id, job_id, epoch, global_step, loss, learning_rate, checkpoint_ref, observed_at`.
Raw protected-eval data is never emitted.

## 7. Artifact seal
Expected adapter artifacts are enumerated and SHA-256 hashed. The final artifact fingerprint is computed from sorted relative paths + hashes. Missing required adapter artifacts block evaluation handoff.

## 8. Completion boundary
Execution receipt state ends at `COMPLETED_NOT_EVALUATED`. M6.x-14 evaluation and later Promotion Governance remain separate.
