# AEGIS P4-04 Live Storage Adapter Candidate

This module is an Adaptive Edge implementation. It must not own domain meaning or canonical dataset authority.

## Scope

- `StorageAdapterContracts`: technology-neutral record/projection/lease contracts.
- `JdbcRecordStoreAdapter`: JDBC binding with prepared statements, idempotency metadata and optimistic version checks.
- `RespRedisRuntimeAdapter`: minimal RESP binding for rebuildable projection and lease/fencing metadata.

## Trust boundary

PostgreSQL may persist canonical records only when the dataset registry grants the corresponding write authority. Redis is never a canonical source of truth. Projection entries carry `sourceVersion`, and lease grants carry fencing tokens.

## Verification

`./verify.sh` compiles with Java 21, `-Xlint:all -Werror`, runs deterministic contract tests, packages a JAR, executes `jdeps`, and emits SHA-256 evidence.

The current candidate passed 13/13 local contract assertions. Real PostgreSQL, Redis, Redis Sentinel/Cluster, migration, backup/restore and failover tests are not executed and therefore cannot be promoted as physically verified.
