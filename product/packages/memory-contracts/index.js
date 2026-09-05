export const MemoryKinds = Object.freeze({ WORKING:"WORKING", EPISODIC:"EPISODIC", FAILURE:"FAILURE", CANDIDATE_LESSON:"CANDIDATE_LESSON", SEMANTIC:"SEMANTIC", LOCAL:"LOCAL", PROCEDURAL:"PROCEDURAL" });
export const MemoryStates = Object.freeze({ ACTIVE:"ACTIVE", CHALLENGED:"CHALLENGED", RETRACTED:"RETRACTED", SUPERSEDED:"SUPERSEDED", STAGED:"STAGED" });
export const MemoryErrorCodes = Object.freeze({ INVALID_MEMORY:"AEGIS-MEM-001", PROMOTION_DENIED:"AEGIS-MEM-002", PROVENANCE_REQUIRED:"AEGIS-MEM-003", SCOPE_ESCALATION:"AEGIS-MEM-004", RETRACTED_MEMORY:"AEGIS-MEM-005" });
export class AegisMemoryError extends Error { constructor(code,message,details={}) { super(message); this.name="AegisMemoryError"; this.code=code; this.details=details; } }
