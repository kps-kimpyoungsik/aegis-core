#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const ROOT = 'portable-brain/skills';
const TAXONOMY_PATH = `${ROOT}/taxonomy.json`;
const MAX_DEPTH = 5;
const ROOT_EXCEPTIONS = new Set(['README.md', 'taxonomy.json', 'INDEX.json']);
const FORBIDDEN = new Set(['misc', 'temp', 'new', 'other', 'unknown']);
const SEGMENT = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const UNIT = /^(service|resource|page)-[a-z0-9]+(?:-[a-z0-9]+)*$/;
const MANIFESTS = ['skill-manifest.json', 'unit-manifest.json', 'resource-manifest.json', 'page-manifest.json'];
const RELATION = /^[A-Z][A-Z0-9_]*:[a-z0-9][a-z0-9._/-]*$/;

function fail(code, message) {
  console.error(`AEGIS-INFO-TREE-${code} ${message}`);
  process.exit(1);
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (error) {
    fail('006', `INVALID_JSON path=${file} error=${error instanceof Error ? error.message : String(error)}`);
  }
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

if (!fs.existsSync(TAXONOMY_PATH)) fail('007', `MISSING_TAXONOMY path=${TAXONOMY_PATH}`);
const taxonomy = readJson(TAXONOMY_PATH);
if (taxonomy.maxSemanticDepth !== MAX_DEPTH) {
  fail('008', `TAXONOMY_DEPTH_DRIFT expected=${MAX_DEPTH} actual=${taxonomy.maxSemanticDepth}`);
}

const files = filesUnder(ROOT);
const units = new Map();
const ids = new Map();

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
  const manifestName = MANIFESTS.find((name) => names.has(name));
  if (!manifestName) fail('005', `MISSING_UNIT_MANIFEST unit=${unitDir}`);

  const manifestPath = `${unitDir}/${manifestName}`;
  const raw = readJson(manifestPath);
  const asset = raw.skill ?? raw.unit ?? raw.resource ?? raw.page;
  if (!asset || typeof asset !== 'object') fail('009', `MISSING_ASSET_OBJECT path=${manifestPath}`);

  const expected = unitDir.slice(ROOT.length + 1).split('/');
  const classification = asset.classification;
  if (!classification || typeof classification !== 'object') {
    fail('010', `MISSING_CLASSIFICATION path=${manifestPath}`);
  }

  const keys = ['field', 'area', 'relation', 'module', 'unit'];
  keys.forEach((key, index) => {
    if (classification[key] !== expected[index]) {
      fail('011', `CLASSIFICATION_PATH_MISMATCH key=${key} expected=${expected[index]} actual=${classification[key]} path=${manifestPath}`);
    }
  });
  if (classification.semanticDepth !== MAX_DEPTH) {
    fail('012', `CLASSIFICATION_DEPTH_MISMATCH expected=${MAX_DEPTH} actual=${classification.semanticDepth} path=${manifestPath}`);
  }

  if (typeof asset.id !== 'string' || asset.id.trim() === '') fail('013', `MISSING_ASSET_ID path=${manifestPath}`);
  if (ids.has(asset.id)) fail('014', `DUPLICATE_ASSET_ID id=${asset.id} first=${ids.get(asset.id)} second=${manifestPath}`);
  ids.set(asset.id, manifestPath);

  if (typeof asset.owner !== 'string' || asset.owner.trim() === '') fail('015', `MISSING_OWNER id=${asset.id} path=${manifestPath}`);
  if (!asset.provenance || typeof asset.provenance !== 'object' || Object.keys(asset.provenance).length === 0) {
    fail('016', `MISSING_PROVENANCE id=${asset.id} path=${manifestPath}`);
  }
  if (!Array.isArray(asset.relations)) fail('017', `MISSING_RELATIONS id=${asset.id} path=${manifestPath}`);
  for (const relation of asset.relations) {
    if (typeof relation !== 'string' || !RELATION.test(relation)) {
      fail('018', `INVALID_RELATION id=${asset.id} relation=${String(relation)} path=${manifestPath}`);
    }
  }
}

console.log(`AEGIS-INFO-TREE-PASS files=${files.length} units=${units.size} ids=${ids.size} maxDepth=${MAX_DEPTH}`);
