import test from 'node:test';
import assert from 'node:assert/strict';
import { createApiServer } from '../apps/api-server/index.js';
import { projectRuntimeSnapshot } from '../apps/web-console/src/projection.js';
import { createTaskExecutionUseCase } from '@aegis/application-runtime';
import { createMemoryItem } from '@aegis/memory-runtime';
import { MemoryKinds } from '@aegis/memory-contracts';
import { AuthorityDecisions } from '@aegis/harness-contracts';
import {
  createExecutionContext,
  createAuthorityGuard,
  createBudgetGuard,
  createToolRuntime,
  createTraceCollector,
  createProvenanceCollector,
  executeHarness,
} from '@aegis/harness-runtime';

const E2E_TENANT = 'tenant:e2e';
const executorAuthenticator = async () => ({ id:'principal:e2e-operator', tenantId:E2E_TENANT, roles:['TASK_EXECUTOR'] });
const tenantHeaders = (extra = {}) => ({ 'x-aegis-tenant':E2E_TENANT, ...extra });

async function withServer(options, fn) {
  const server = createApiServer(options);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise((resolve) => server.close(resolve)); }
}

function makeHarness({ deny = false } = {}) {
  return async ({ task, pointContext }) => {
    const context = createExecutionContext({
      executionId: `${task.id}:exec`, taskId: task.id, harnessId: 'r0.9-e2e', harnessVersion: '0.9.0', authorityRef: 'authority:r0.9', provenanceRef: `prov:${task.id}`,
    });
    const trace = createTraceCollector();
    const authorityGuard = createAuthorityGuard(() => deny ? AuthorityDecisions.DENY : AuthorityDecisions.ALLOW);
    const budgetGuard = createBudgetGuard({ maxToolCalls: 1 });
    const contracts = new Map([['echo', { sideEffect: 'READ_ONLY', execute: async (input) => ({ echoed: input }) }]]);
    const toolRuntime = createToolRuntime({ contracts, authorityGuard, budgetGuard, trace });
    const provenanceCollector = createProvenanceCollector({ taskRef: task.id, harnessVersion: '0.9.0', policyVersion: 'r0.9', provenanceRef: `prov:${task.id}` });
    for (const item of pointContext) provenanceCollector.add('memory', item.id);
    return executeHarness({
      context,
      contextEntries: pointContext.map((item) => ({ id: item.id, sourceRef: item.provenance.sourceRef, role: 'MEMORY', executableInstructionAllowed: false })),
      authorityGuard,
      toolRuntime,
      toolRequest: { toolId: 'echo', input: { taskId: task.id } },
      validators: [async (value) => ({ pass: value?.status === 'SUCCESS', reason: 'tool result required' })],
      provenanceCollector,
      trace,
    });
  };
}

const reusableMemory = createMemoryItem({
  id: 'mem:release', kind: MemoryKinds.EPISODIC, scope: 'PROJECT', key: 'release', quality: 0.9, freshness: 1,
  content: { lesson: 'reuse verified release path' }, provenance: { sourceRef: 'episode:r0.7', sourceType: 'PAST_SUCCESS' },
});

test('E2E success: tenant-bound authenticated API -> application -> harness -> episode/event snapshot -> React projection', async () => {
  const runtime = createTaskExecutionUseCase({ runHarness: makeHarness(), memoryItems: [reusableMemory], retrievalPolicy: { maxItems: 1 } });
  await withServer({ executeTask: runtime.executeTask, authenticateRequest:executorAuthenticator, getSnapshot: runtime.getSnapshot, idFactory: () => 'e2e-success' }, async (base) => {
    const create = await fetch(`${base}/v1/tasks`, { method:'POST', headers:tenantHeaders({'content-type':'application/json'}), body:JSON.stringify({ goal:'ship e2e', owner:'ops', responsibility:'release', retrievalQuery:{ key:'release' } }) });
    assert.equal(create.status, 202);
    const result = await create.json();
    assert.equal(result.status, 'COMPLETED');
    assert.equal(result.pointContext.length, 1);
    assert.equal(result.memory.kind, 'EPISODIC');
    assert.equal(result.harnessResult.provenance.memory[0], 'mem:release');

    const snapshotResponse = await fetch(`${base}/v1/runtime/snapshot`, { headers:tenantHeaders() });
    assert.equal(snapshotResponse.status, 200);
    const snapshot = await snapshotResponse.json();
    assert.equal(snapshot.tasks[0].status, 'COMPLETED');
    assert.equal(snapshot.memories[0].kind, 'EPISODIC');
    assert.ok(snapshot.events.some((event) => event.type === 'TASK_COMPLETED'));
    const projection = projectRuntimeSnapshot(snapshot);
    assert.equal(projection.totals.tasks, 1);
    assert.equal(projection.totals.failures, 0);
  });
});

test('E2E failure: tenant-bound authenticated API -> authority deny -> FAILED + failure memory -> React failure projection', async () => {
  const runtime = createTaskExecutionUseCase({ runHarness: makeHarness({ deny: true }) });
  await withServer({ executeTask: runtime.executeTask, authenticateRequest:executorAuthenticator, getSnapshot: runtime.getSnapshot, idFactory: () => 'e2e-failure' }, async (base) => {
    const create = await fetch(`${base}/v1/tasks`, { method:'POST', headers:tenantHeaders({'content-type':'application/json'}), body:JSON.stringify({ goal:'deny unsafe execution', owner:'ops', responsibility:'release' }) });
    assert.equal(create.status, 422);
    const result = await create.json();
    assert.equal(result.status, 'FAILED');
    assert.equal(result.memory.kind, 'FAILURE');
    assert.equal(result.memory.content.code, 'AEGIS-HARNESS-001');

    const snapshotResponse = await fetch(`${base}/v1/runtime/snapshot`, { headers:tenantHeaders() });
    assert.equal(snapshotResponse.status, 200);
    const snapshot = await snapshotResponse.json();
    assert.equal(snapshot.tasks[0].status, 'FAILED');
    assert.equal(snapshot.memories[0].kind, 'FAILURE');
    assert.ok(snapshot.events.some((event) => event.type === 'FAILURE'));
    const projection = projectRuntimeSnapshot(snapshot);
    assert.equal(projection.totals.failures, 1);
  });
});
