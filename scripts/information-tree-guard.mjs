#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const ROOT = 'portable-brain/skills';
const MAX_DEPTH = 5;
const ROOT_EXCEPTIONS = new Set(['README.md', 'taxonomy.json', 'INDEX.json']);
const FORBIDDEN = new Set(['misc', 'temp', 'new', 'other', 'unknown']);
const SEGMENT = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const UNIT = /^(service|resource|page)-[a-z0-9]+(?:-[a-z0-9]+)*$/;
const MANIFESTS = new Set(['skill-manifest.json', 'unit-manifest.json', 'resource-manifest.json', 'page-manifest.json']);

function fail(code, message) {
  console.error(`AEGIS-INFO-TREE-${code} ${message}`);
  process.exit(1);
}

function filesUnder(directory) {
  if (!fs.existsSync(directory)) return [];
  const result = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...filesUnder(target));
    else if (entry.isFile()) result.push(target.split(path.sep).join('/'));
  }
  return result;
}

const files = filesUnder(ROOT);
const units = new Map();

for (const file of files) {
  const relative = file.slice(ROOT.length + 1);
  const parts = relative.split('/');
  if (parts.length === 1) {
    if (!ROOT_EXCEPTIONS.has(parts[0])) fail('001', `ROOT_CONTENT_NOT_METADATA path=${file}`);
    continue;
  }

  const directories = parts.slice(0, -1);
  if (directories.length !== MAX_DEPTH) {
    fail('002', `SEMANTIC_DEPTH expected=${MAX_DEPTH} actual=${directories.length} path=${file}`);
  }

  for (let index = 0; index < 4; index += 1) {
    const segment = directories[index];
    if (!SEGMENT.test(segment) || FORBIDDEN.has(segment)) {
      fail('003', `INVALID_SEGMENT depth=${index + 1} segment=${segment} path=${file}`);
    }
  }

  if (!UNIT.test(directories[4]) || FORBIDDEN.has(directories[4])) {
    fail('004', `INVALID_UNIT expected=service-*|resource-*|page-* actual=${directories[4]} path=${file}`);
  }

  const unitDir = `${ROOT}/${directories.join('/')}`;
  if (!units.has(unitDir)) units.set(unitDir, new Set());
  units.get(unitDir).add(parts.at(-1));
}

for (const [unitDir, names] of units) {
  const hasManifest = [...MANIFESTS].some((name) => names.has(name));
  if (!hasManifest) fail('005', `MISSING_UNIT_MANIFEST unit=${unitDir}`);
}

console.log(`AEGIS-INFO-TREE-PASS files=${files.length} units=${units.size} maxDepth=${MAX_DEPTH}`);
