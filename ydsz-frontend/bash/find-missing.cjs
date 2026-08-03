const fs = require('fs');
const path = require('path');
const base = process.cwd();

// Read catalog keys
const yaml = fs.readFileSync(path.join(base, 'pnpm-workspace.yaml'), 'utf-8');
const catalogKeys = new Set();
yaml.split('\n').forEach(line => {
  const m = line.match(/^\s+'([^']+)':\s/) || line.match(/^\s+"([^"]+)":\s/);
  if (m) catalogKeys.add(m[1]);
});

// Walk package.json files
const missing = new Set();
function walk(dir) {
  for (const f of fs.readdirSync(dir)) {
    const full = path.join(dir, f);
    if (f.startsWith('.') || f === 'node_modules' || f === '_tmp_' || f.startsWith('_tmp_')) continue;
    const st = fs.statSync(full);
    if (st.isDirectory()) { walk(full); continue; }
    if (f === 'package.json') {
      const pkg = JSON.parse(fs.readFileSync(full, 'utf-8'));
      for (const deps of ['dependencies','devDependencies','optionalDependencies','peerDependencies']) {
        if (!pkg[deps]) continue;
        for (const [name, ver] of Object.entries(pkg[deps])) {
          if (ver === 'catalog:' && !catalogKeys.has(name)) {
            missing.add(name);
          }
        }
      }
    }
  }
}
walk(base);
const list = [...missing].sort();
for (const m of list) console.log(m);
