# Pre-Commit Anti-Duplication Gate

Canonical rule: repository writes must reuse the existing ownership, workstream-collision, and duplicate-public-symbol validators before impact-specific verification.

Execution order:

1. refresh `origin/main` and reject stale/unreconciled heads;
2. compute the changed-path impact set;
3. run `ownership-check.mjs`;
4. run `workstream-collision-check.mjs`;
5. run `duplicate-check.mjs`;
6. execute impact-specific product/runtime/data/storage/integration gates;
7. promote only exact-head evidence.

This document does not create another duplicate detector. It records the required composition of existing Canonical assets.
