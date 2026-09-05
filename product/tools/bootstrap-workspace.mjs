import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const scopeDir = path.join(root, 'node_modules', '@aegis');
fs.mkdirSync(scopeDir, { recursive: true });
let linked = 0;
for (const entry of fs.readdirSync(path.join(root, 'packages'), { withFileTypes: true })) {
  if (!entry.isDirectory()) continue;
  const dir = path.join(root, 'packages', entry.name);
  const packageJsonPath = path.join(dir, 'package.json');
  if (!fs.existsSync(packageJsonPath)) continue;
  const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
  if (typeof pkg.name !== 'string' || !pkg.name.startsWith('@aegis/')) continue;
  const link = path.join(scopeDir, pkg.name.slice('@aegis/'.length));
  const target = path.relative(scopeDir, dir);
  fs.rmSync(link, { recursive: true, force: true });
  fs.symlinkSync(target, link, process.platform === 'win32' ? 'junction' : 'dir');
  linked += 1;
}
console.log(`bootstrap-workspace PASS (${linked} @aegis workspace links)`);
