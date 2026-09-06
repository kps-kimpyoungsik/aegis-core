import fs from "node:fs";

const ACTIVE_STATES = new Set(["ACTIVE", "ACTIVE_CANDIDATE", "READY_FOR_INTEGRATION"]);

function readJson(url) {
  return JSON.parse(fs.readFileSync(url, "utf8"));
}

function normalizePath(value) {
  return value.endsWith("/") ? value : `${value}/`;
}

export function pathOverlaps(left, right) {
  const a = normalizePath(left);
  const b = normalizePath(right);
  return left === right || a.startsWith(b) || b.startsWith(a);
}

function workstreamDomains(workstream, capabilityById) {
  return new Set(
    (workstream.capabilityIds ?? [])
      .map((id) => capabilityById.get(id)?.domain)
      .filter(Boolean),
  );
}

export function classifyWorkstreamOverlap(left, right, capabilityById = new Map()) {
  const sharedCapabilities = (left.capabilityIds ?? []).filter((id) =>
    (right.capabilityIds ?? []).includes(id),
  );
  const overlappingPaths = (left.touchPaths ?? []).filter((a) =>
    (right.touchPaths ?? []).some((b) => pathOverlaps(a, b)),
  );
  const sameOwner = left.ownerResponsibility === right.ownerResponsibility;
  const leftDomains = workstreamDomains(left, capabilityById);
  const rightDomains = workstreamDomains(right, capabilityById);
  const relatedDomain = [...leftDomains].some((domain) => rightDomains.has(domain));

  if (sharedCapabilities.length > 0 && !sameOwner) {
    return Object.freeze({ level: "D4", decision: "FREEZE", sharedCapabilities, overlappingPaths });
  }
  if (overlappingPaths.length > 0 && !sameOwner) {
    return Object.freeze({ level: "D4", decision: "FREEZE", sharedCapabilities, overlappingPaths });
  }
  if (sharedCapabilities.length > 0) {
    return Object.freeze({ level: "D3", decision: "HANDOFF", sharedCapabilities, overlappingPaths });
  }
  if (overlappingPaths.length > 0) {
    return Object.freeze({ level: "D2", decision: "SPLIT", sharedCapabilities, overlappingPaths });
  }
  if (relatedDomain || sameOwner) {
    return Object.freeze({ level: "D1", decision: "REUSE_OR_EXTEND", sharedCapabilities, overlappingPaths });
  }
  return Object.freeze({ level: "D0", decision: "EXECUTE", sharedCapabilities, overlappingPaths });
}

export function validateControlPlane({ ownership, domains, capabilities, workstreams }) {
  const ownerIds = new Set();
  for (const item of ownership.responsibilities) {
    if (ownerIds.has(item.id)) throw new Error(`AEGIS-WS-012 DUPLICATE_RESPONSIBILITY ${item.id}`);
    ownerIds.add(item.id);
  }

  const domainIds = new Set();
  const domainById = new Map();
  for (const domain of domains.domains) {
    if (domainIds.has(domain.id)) throw new Error(`AEGIS-WS-001 DUPLICATE_DOMAIN ${domain.id}`);
    domainIds.add(domain.id);
    domainById.set(domain.id, domain);
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
    const domain = domainById.get(capability.domain);
    const requiresCanonicalDomainOwner = capability.status !== "REFERENCE_ONLY";
    if (
      requiresCanonicalDomainOwner &&
      capability.ownerResponsibility !== null &&
      !(domain.ownerResponsibilities ?? []).includes(capability.ownerResponsibility)
    ) {
      throw new Error(
        `AEGIS-WS-013 CAPABILITY_OWNER_OUTSIDE_DOMAIN ${capability.id}:${capability.ownerResponsibility}:${capability.domain}`,
      );
    }
    capabilityById.set(capability.id, capability);
  }

  const ids = new Set();
  const active = [];
  for (const ws of workstreams) {
    if (ids.has(ws.id)) throw new Error(`AEGIS-WS-006 DUPLICATE_WORKSTREAM ${ws.id}`);
    ids.add(ws.id);
    if (ws.ownerResponsibility !== null && !ownerIds.has(ws.ownerResponsibility)) {
      throw new Error(`AEGIS-WS-007 UNKNOWN_WORKSTREAM_OWNER ${ws.id}:${ws.ownerResponsibility}`);
    }
    for (const capabilityId of ws.capabilityIds ?? []) {
      const capability = capabilityById.get(capabilityId);
      if (!capability) throw new Error(`AEGIS-WS-008 UNKNOWN_WORKSTREAM_CAPABILITY ${ws.id}:${capabilityId}`);
      if (
        ws.ownerResponsibility !== null &&
        capability.ownerResponsibility !== null &&
        ws.ownerResponsibility !== capability.ownerResponsibility
      ) {
        throw new Error(`AEGIS-WS-009 OWNER_CAPABILITY_MISMATCH ${ws.id}:${capabilityId}`);
      }
    }
    if (ACTIVE_STATES.has(ws.state)) active.push(ws);
  }

  const classifications = [];
  for (let i = 0; i < active.length; i += 1) {
    for (let j = i + 1; j < active.length; j += 1) {
      const left = active[i];
      const right = active[j];
      const classification = classifyWorkstreamOverlap(left, right, capabilityById);
      classifications.push(Object.freeze({ left: left.id, right: right.id, ...classification }));
      if (classification.level === "D4") {
        throw new Error(`AEGIS-WS-011 ACTIVE_CONFLICT ${left.id}:${right.id}`);
      }
      if (classification.level === "D3") {
        throw new Error(
          `AEGIS-WS-010 ACTIVE_CAPABILITY_COLLISION ${left.id}:${right.id}:${classification.sharedCapabilities.join(",")}`,
        );
      }
      if (classification.level === "D2") {
        throw new Error(`AEGIS-WS-014 ACTIVE_PATH_INTERSECTION ${left.id}:${right.id}`);
      }
    }
  }

  return {
    owners: ownerIds.size,
    domains: domainIds.size,
    capabilities: capabilityById.size,
    workstreams: ids.size,
    activeWorkstreams: active.length,
    classifications,
  };
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const contracts = new URL("../contracts/", import.meta.url);
  const dir = new URL("workstreams/", contracts);
  const workstreams = fs
    .readdirSync(dir)
    .filter((name) => name.endsWith(".json"))
    .sort()
    .map((name) => readJson(new URL(name, dir)));
  const result = validateControlPlane({
    ownership: readJson(new URL("ownership-registry.json", contracts)),
    domains: readJson(new URL("domain-registry.json", contracts)),
    capabilities: readJson(new URL("capability-registry.json", contracts)),
    workstreams,
  });
  console.log(
    `workstream-collision-check PASS owners=${result.owners} domains=${result.domains} capabilities=${result.capabilities} workstreams=${result.workstreams} active=${result.activeWorkstreams}`,
  );
}
