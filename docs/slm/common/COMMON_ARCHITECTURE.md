# AEGIS SLM Common Architecture

## Stable invariants
1. Common Core / Contract first.
2. NODE / MODEL / KNOWLEDGE / ASSET remain isolated owners.
3. One capability → one canonical owner.
4. One contract → one canonical definition.
5. Dataset snapshots are immutable.
6. Generated learning material is Candidate evidence, never automatic Golden.
7. Protected Eval raw content is never used for training, prompt tuning, HPO, or material generation.
8. Training input requires exact Dataset Snapshot, Model, Tokenizer, Config, Seed, and Environment fingerprints.
9. Evaluation must separate task quality, regression, forgetting, and False PASS.
10. Promotion recommendation is not promotion execution.

## Hexagonal boundary
`Inbound → Input Port → Application UseCase → Domain → Output Port → Adapter`

Vendor/framework specifics such as TRL, Transformers, PEFT, bitsandbytes, CUDA, or model-family target modules stay in outbound adapters.
