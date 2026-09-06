import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const packagesDir = path.join(root, "packages");
const packageDirs = fs
  .readdirSync(packagesDir, { withFileTypes: true })
  .filter((entry) => entry.isDirectory());

const publicOwners = new Map();
const publicDuplicates = [];
const bodyOwners = new Map();
const bodyDuplicates = [];
const exportPattern = /export\s+(?:const|function|class)\s+([A-Za-z_$][\w$]*)/g;

function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:])\/\/.*$/gm, "$1");
}

function normalizeModuleBody(source) {
  return stripComments(source)
    .replace(/^\s*import[\s\S]*?;\s*$/gm, "")
    .replace(/^\s*export\s+\{[^}]*\};?\s*$/gm, "")
    .replace(/\s+/g, " ")
    .trim();
}

for (const dirent of packageDirs) {
  const dir = path.join(packagesDir, dirent.name);
  for (const file of fs.readdirSync(dir).filter((name) => /\.(?:js|ts)$/.test(name))) {
    const owner = `${dirent.name}/${file}`;
    const text = fs.readFileSync(path.join(dir, file), "utf8");

    for (const match of text.matchAll(exportPattern)) {
      const symbol = match[1];
      const key = symbol.toLowerCase();
      if (publicOwners.has(key) && publicOwners.get(key) !== owner) {
        publicDuplicates.push(`${symbol}: ${publicOwners.get(key)} <> ${owner}`);
      } else {
        publicOwners.set(key, owner);
      }
    }

    const normalizedBody = normalizeModuleBody(text);
    if (normalizedBody.length >= 160) {
      if (bodyOwners.has(normalizedBody) && bodyOwners.get(normalizedBody) !== owner) {
        bodyDuplicates.push(`${bodyOwners.get(normalizedBody)} <> ${owner}`);
      } else {
        bodyOwners.set(normalizedBody, owner);
      }
    }
  }
}

if (publicDuplicates.length) {
  throw new Error(
    `AEGIS-DUP-001 DUPLICATE_PUBLIC_SYMBOL\n${publicDuplicates.join("\n")}`,
  );
}

if (bodyDuplicates.length) {
  throw new Error(
    `AEGIS-DUP-002 DUPLICATE_MODULE_BODY\n${bodyDuplicates.join("\n")}`,
  );
}

console.log(
  `duplicate-check PASS (${publicOwners.size} public symbols, ${bodyOwners.size} normalized module bodies)`,
);
