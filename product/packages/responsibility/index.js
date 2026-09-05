export const overlapLevels = Object.freeze(["D0","D1","D2","D3","D4"]);
export function preflight({responsibility, requestedOwner, activeClaims = []}) {
  const same = activeClaims.filter((claim) => claim.responsibility === responsibility && claim.status === "ACTIVE");
  if (same.length === 0) return {overlap:"D0", decision:"EXECUTE"};
  if (same.every((claim) => claim.owner === requestedOwner)) return {overlap:"D1", decision:"EXTEND"};
  return {overlap:"D3", decision:"HANDOFF"};
}
