import fs from 'node:fs';

const policyUrl = new URL('../contracts/ci-fanout-policy.json', import.meta.url);

export function loadPolicy(readFile = (url) => fs.readFileSync(url, 'utf8')) {
  return JSON.parse(readFile(policyUrl));
}

function matchesPrefix(path, prefix) {
  return path === prefix || path.startsWith(`${prefix}/`);
}

function orderedUnique(values, order) {
  const unique = new Set(values);
  return order.filter((item) => unique.has(item));
}

function expandDependencies(seedGroups, policy) {
  const resolved = new Set(seedGroups);
  const queue = [...seedGroups];

  while (queue.length > 0) {
    const group = queue.shift();
    const dependencies = policy.dependencyClosure[group] ?? [];
    for (const dependency of dependencies) {
      if (!resolved.has(dependency)) {
        resolved.add(dependency);
        queue.push(dependency);
      }
    }
  }

  return resolved;
}

export function validatePolicy(policy) {
  const failures = [];
  const knownGroups = new Set(policy.groupOrder ?? []);

  if (!Number.isInteger(policy.maxHeavyweightGroupsPerSha) || policy.maxHeavyweightGroupsPerSha < 1) {
    failures.push('INVALID_MAX_HEAVYWEIGHT_GROUPS');
  }
  if (!knownGroups.has(policy.controlGroup)) {
    failures.push('CONTROL_GROUP_NOT_REGISTERED');
  }

  for (const rule of policy.pathRules ?? []) {
    if (!rule.prefix || !Array.isArray(rule.groups) || rule.groups.length === 0) {
      failures.push('INVALID_PATH_RULE');
      continue;
    }
    for (const group of rule.groups) {
      if (!knownGroups.has(group)) failures.push(`UNKNOWN_RULE_GROUP:${group}`);
    }
  }

  for (const [group, dependencies] of Object.entries(policy.dependencyClosure ?? {})) {
    if (!knownGroups.has(group)) failures.push(`UNKNOWN_DEPENDENCY_SOURCE:${group}`);
    for (const dependency of dependencies) {
      if (!knownGroups.has(dependency)) failures.push(`UNKNOWN_DEPENDENCY_TARGET:${dependency}`);
    }
  }

  return [...new Set(failures)].sort();
}

export function planFanout(changedPaths, policy = loadPolicy()) {
  const policyFailures = validatePolicy(policy);
  if (policyFailures.length > 0) {
    return {
      action: 'FAIL_CLOSED_INVALID_POLICY',
      budgetExceeded: false,
      groups: [],
      heavyweightGroups: [],
      policyFailures,
    };
  }

  const normalizedPaths = [...new Set(changedPaths.map((path) => path.trim()).filter(Boolean))].sort();
  const seeds = new Set([policy.controlGroup]);
  const matchedRules = [];

  for (const path of normalizedPaths) {
    for (const rule of policy.pathRules) {
      if (matchesPrefix(path, rule.prefix)) {
        matchedRules.push({ path, prefix: rule.prefix, groups: [...rule.groups] });
        for (const group of rule.groups) seeds.add(group);
      }
    }
  }

  const expanded = expandDependencies(seeds, policy);
  const groups = orderedUnique(expanded, policy.groupOrder);
  const heavyweightGroups = groups.filter((group) => group !== policy.controlGroup);
  const budgetExceeded = heavyweightGroups.length > policy.maxHeavyweightGroupsPerSha;

  return {
    action: budgetExceeded ? 'SPLIT_OR_ESCALATE' : 'PROCEED_BOUNDED',
    budgetExceeded,
    groups,
    heavyweightGroups,
    matchedRules,
    maxHeavyweightGroupsPerSha: policy.maxHeavyweightGroupsPerSha,
    paths: normalizedPaths,
    policyFailures: [],
  };
}

function printPlan(plan) {
  process.stdout.write(`${JSON.stringify(plan, null, 2)}\n`);
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const policy = loadPolicy();
  if (process.argv.includes('--self-check')) {
    const failures = validatePolicy(policy);
    if (failures.length > 0) {
      console.error(`CI_FANOUT_POLICY=FAIL ${failures.join(',')}`);
      process.exitCode = 1;
    } else {
      console.log(`CI_FANOUT_POLICY=PASS version=${policy.version}`);
    }
  } else {
    const paths = process.argv.slice(2).filter((arg) => arg !== '--json');
    printPlan(planFanout(paths, policy));
  }
}
