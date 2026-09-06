import fs from "node:fs";

const ACTIVE_STATES = new Set(["ACTIVE", "ACTIVE_CANDIDATE", "READY_FOR_INTEGRATION"]);

function readJson(url) {
  return JSON.parse(fs.readFileSync(url, "utf8"));
}

function pathOverlaps(left, right) {
  const a = left.endsWith("/") ? left : `${left}/`;
  const b = right.endsWith("/") ? right : `${right}/`;
  return left === right || a.startsWith(b) || b.startsWith(a);
}

export function validateControlPlane({ ownership, domains, capabilities, ledger }) {
  const ownerIds = new Set(ownership.responsibilities.map((item) => item.id));
  const domainIds = new Set();
  for (const domain of domains.domains) {
    if (domainIds.has(domain.id)) throw new Error(`AEGIS-WS-001 DUPLICATE_DOMAIN ${domain.id}`);
    domainIds.add(domain.id);
    for (const owner of domain.ownerResponsibilities ?? []) {
      if (!ownerIds.has(owner)) throw new Error(`AEGIS-WS-002 UNKNOWN_DOMAIN_OWNER ${domain.id}:${owner}`);
    }
  }

  const capabilityById = new Map();
  for (const capability of capabilities.capabilities) {
    if (capabilityById.has(capability.id)) throw new Error(`AEGIS-WS-003 DUPLICATE_CAPABILITY ${capability.id}`);
    if (!domainIds.has(capability.domain)) throw new Error(`AEGIS-WS-004 UNKNOWN_CAPABILITY_DOMAIN ${capability.id}:${capability.domain}`);
    if (capability.ownerResponsibility !== null && !ownerIds.has(capability.ownerResponsibility)) {
      throw new Error(`AEGIS-WS-005 UNKNOWN_CAPABILITY_OWNER ${capability.id}:${capability.ownerResponsibility}`);
    }
    capabilityById.set(capability.id, capability);
  }

  const workstreamIds = new Set();
  const active = [];
  for (const workstream of ledger.workstreams) {
    if (workstreamIds.has(workstream.id)) throw new Error(`AEGIS-WS-006 DUPLICATE_WORKSTREAM ${workstream.id}`);
    workstreamIds.add(workstream.id);
    if (workstream.ownerResponsibility !== null && !ownerIds.has(workstream.ownerResponsibility)) {
      throw new Error(`AEGIS-WS-007 UNKNOWN_WORKSTREAM_OWNER ${workstream.id}:${workstream.ownerResponsibility}`);
    }
    for (const capabilityId of workstream.capabilityIds ?? []) {
      const capability = capabilityById.get(capabilityId);
      if (!capability) throw new Error(`AEGIS-WS-008 UNKNOWN_WORKSTREAM_CAPABILITY ${workstream.id}:${capabilityId}`);
      if (workstream.ownerResponsibility !== null && capability.ownerResponsibility !== null && workstream.ownerResponsibility !== capability.ownerResponsibility) {
        throw new Error(`AEGIS-WS-009 OWNER_CAPABILITY_MISMATCH ${workstream.id}:${capabilityId}`);
      }
    }
    if (ACTIVE_STATES.has(workstream.state)) active.push(workstream);
  }

  for (let i = 0; i < active.length; i += 1) {
    for (let j = i + 1; j < active.length; j += 1) {
      const left = active[i];
      const right = active[j];
      const shared = (left.capabilityIds ?? []).filter((id) => (right.capabilityIds ?? []).includes(id));
      if (shared.length > 0) throw new Error(`AEGIS-WS-010 ACTIVE_CAPABILITY_COLLISION ${left.id}:${right.id}:${shared.join(",")}`);
      const overlap = (left.touchPaths ?? []).find((a) => (right.touchPaths ?? []).some((b) => pathOverlaps(a, b)));
      if (overlap) throw new Error(`AEGIS-WS-011 ACTIVE_PATH_COLLISION ${left.id}:${right.id}:${overlap}`);
    }
  }

  return {
    owners: ownerIds.size,
    domains: domainIds.size,
    capabilities: capabilityById.size,
    workstreams: workstreamIds.size,
    activeWorkstreams: active.length,
  };
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const base = new URL("../contracts/", import.meta.url);
  const result = validateControlPlane({
    ownership: readJson(new URL("ownership-registry.json", base)),
    domains: readJson(new URL("domain-registry.json", base)),
    capabilities: readJson(new URL("capability-registry.json", base)),
    ledger: readJson(new URL("active-workstream-ledger.json", base)),
  });
  console.log(`workstream-collision-check PASS owners=${result.owners} domains=${result.domains} capabilities=${result.capabilities} workstreams=${result.workstreams} active=${result.activeWorkstreams}`);
}
