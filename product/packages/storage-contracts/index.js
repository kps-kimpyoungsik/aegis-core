export const StorageErrorCodes = Object.freeze({VERSION_CONFLICT:"AEGIS-CONS-001",DUPLICATE_OPERATION:"AEGIS-CONS-002",OUTBOX_ATOMICITY_VIOLATION:"AEGIS-CONS-003",LEASE_LOST:"AEGIS-CONS-006",FENCING_TOKEN_STALE:"AEGIS-CONS-007",INVALID_CHECKPOINT:"AEGIS-CONS-008",PROJECTION_POSITION_REGRESSION:"AEGIS-CONS-013"});
export class AegisStorageError extends Error { constructor(code,message,details={}) { super(message); this.name="AegisStorageError"; this.code=code; this.details=details; } }
export function storageRecordKey(datasetId,recordId){ return `${datasetId}::${recordId}`; }
