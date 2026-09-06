import fs from 'node:fs';

const contracts = [
  {
    id: 'AEGIS-CI-IMPACT-R1.0',
    path: '.github/workflows/r1.0-product-live-postgres-e2e.yml',
    required: [
      "pull_request:",
      "push:",
      "branches: [main]",
      "'product/**'",
      "'data-plane/**'",
      "'storage-adapters/**'",
      "'integration-gate/**'",
    ],
  },
  {
    id: 'AEGIS-CI-IMPACT-R1.8',
    path: '.github/workflows/r1.8-product-postgres-backup-restore.yml',
    required: [
      "pull_request:",
      "push:",
      "branches: [main]",
      "'product/**'",
      "'data-plane/**'",
      "'storage-adapters/**'",
      "'integration-gate/**'",
    ],
  },
];

export function validateWorkflowContract(text, required) {
  return required.filter((token) => !text.includes(token));
}

export function validateImpactContracts(readFile = (path) => fs.readFileSync(path, 'utf8')) {
  const failures = [];
  for (const contract of contracts) {
    let text;
    try {
      text = readFile(contract.path);
    } catch (error) {
      failures.push({ id: contract.id, path: contract.path, reason: `FILE_UNREADABLE ${error.message}` });
      continue;
    }
    const missing = validateWorkflowContract(text, contract.required);
    if (missing.length > 0) {
      failures.push({ id: contract.id, path: contract.path, reason: `MISSING_TRIGGER_CONTRACT ${missing.join(',')}` });
    }
  }
  return failures;
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const failures = validateImpactContracts();
  if (failures.length > 0) {
    for (const failure of failures) {
      console.error(`::error title=${failure.id}::${failure.path} ${failure.reason}`);
    }
    console.error(`CI_IMPACT_CONTRACT_GUARD=FAIL count=${failures.length}`);
    process.exitCode = 1;
  } else {
    console.log('CI_IMPACT_CONTRACT_GUARD=PASS contracts=2');
  }
}
