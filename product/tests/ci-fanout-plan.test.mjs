import test from 'node:test';
import assert from 'node:assert/strict';

import { loadPolicy, planFanout, validatePolicy } from '../tools/ci-fanout-plan.mjs';

const policy = loadPolicy();

test('canonical fan-out policy is structurally valid', () => {
  assert.deepEqual(validatePolicy(policy), []);
});

test('docs-only change stays on the cheap control plane', () => {
  const plan = planFanout(['docs/governance/example.md'], policy);
  assert.equal(plan.action, 'PROCEED_BOUNDED');
  assert.deepEqual(plan.groups, ['control-plane']);
  assert.deepEqual(plan.heavyweightGroups, []);
});

test('data-plane change expands required downstream groups without duplication', () => {
  const plan = planFanout([
    'data-plane/src/example.java',
    'data-plane/src/example.java',
  ], policy);
  assert.equal(plan.action, 'PROCEED_BOUNDED');
  assert.deepEqual(plan.groups, [
    'control-plane',
    'data-plane',
    'storage',
    'integration',
    'release',
  ]);
});

test('broad cross-domain change fails closed instead of dropping required groups', () => {
  const plan = planFanout([
    'product/packages/api-runtime/src/index.ts',
    'runtime-kernel/src/main/java/example/Runtime.java',
    'data-plane/src/main/java/example/Data.java',
  ], policy);
  assert.equal(plan.budgetExceeded, true);
  assert.equal(plan.action, 'SPLIT_OR_ESCALATE');
  assert.deepEqual(plan.heavyweightGroups, [
    'product',
    'runtime',
    'data-plane',
    'storage',
    'integration',
    'release',
  ]);
});

test('workflow change remains release-visible', () => {
  const plan = planFanout(['.github/workflows/p4-data-plane-verify.yml'], policy);
  assert.deepEqual(plan.groups, ['control-plane', 'release']);
});
