import fs from "node:fs";
import { execFileSync } from "node:child_process";

function readJson(url) { return JSON.parse(fs.readFileSync(url, "utf8")); }
function matchesPrefix(path, prefixes) { return prefixes.some((prefix) => path.startsWith(prefix)); }

export function evaluateChangeImpact({ changedFiles, diffsByPath, registry }) {
  const failures = [];
  const triggered = [];
  for (const rule of registry.rules ?? []) {
    const changedSources = changedFiles.filter((path) => matchesPrefix(path, rule.sourcePrefixes ?? []));
    if (!changedSources.length) continue;
    const material = changedSources.some((path) => {
      const diff = diffsByPath[path] ?? "";
      return new RegExp(rule.materialDiffPattern, "im").test(diff);
    });
    if (!material) continue;
    triggered.push(rule.id);
    const missing = (rule.requiredCompanionPaths ?? []).filter((path) => !changedFiles.includes(path));
    if (missing.length) failures.push({ ruleId: rule.id, missing, changedSources });
  }
  return { triggered, failures };
}

function git(args) { return execFileSync("git", args, { encoding: "utf8" }).trim(); }
function resolveBaseSha() {
  const configured = process.env.AEGIS_BASE_SHA?.trim();
  if (configured && !/^0+$/.test(configured)) return configured;
  return git(["rev-parse", "HEAD^"]);
}

export function runCheck({ baseSha = resolveBaseSha(), registryUrl = new URL("../contracts/change-impact-registry.json", import.meta.url) } = {}) {
  const registry = readJson(registryUrl);
  const changedFiles = git(["diff", "--name-only", `${baseSha}...HEAD`]).split("\n").filter(Boolean);
  const diffsByPath = {};
  for (const rule of registry.rules ?? []) {
    for (const path of changedFiles.filter((candidate) => matchesPrefix(candidate, rule.sourcePrefixes ?? []))) {
      if (!(path in diffsByPath)) diffsByPath[path] = git(["diff", "--unified=0", `${baseSha}...HEAD`, "--", path]);
    }
  }
  const result = evaluateChangeImpact({ changedFiles, diffsByPath, registry });
  if (result.failures.length) {
    const details = result.failures.map((item) => `${item.ruleId} missing=${item.missing.join(",")} sources=${item.changedSources.join(",")}`).join("; ");
    throw new Error(`AEGIS-IMPACT-001 UNACKNOWLEDGED_CHANGE_IMPACT ${details}`);
  }
  console.log(`ci-change-impact-check PASS base=${baseSha} changed=${changedFiles.length} triggered=${result.triggered.join(",") || "none"}`);
  return result;
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) runCheck();
