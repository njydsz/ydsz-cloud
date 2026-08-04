const fs = require('fs');
const path = require('path');

const base = path.resolve(__dirname, '..');

// Read catalog from pnpm-workspace.yaml
const yaml = fs.readFileSync(path.join(base, 'pnpm-workspace.yaml'), 'utf-8');
const catalogKeys = new Set();
yaml.split('\n').forEach(line => {
  const m = line.match(/^\s+'([^']+)': /) || line.match(/^\s+"([^"]+)": /);
  if (m) catalogKeys.add(m[1]);
});

// Find all catalog: refs
function findFiles(dir, pattern) {
  const results = [];
  const list = fs.readdirSync(dir);
  for (const file of list) {
    const full = path.join(dir, file);
    const stat = fs.statSync(full);
    if (stat.isDirectory() && !file.startsWith('node_modules') && !file.startsWith('.turbo') && !file.startsWith('_tmp_')) {
      results.push(...findFiles(full, pattern));
    } else if (file === 'package.json') {
      const content = fs.readFileSync(full, 'utf-8');
      const matches = content.match(/"([^"]+)":\s*"catalog:"/g);
      if (matches) {
        for (const m of matches) {
          const name = m.match(/"([^"]+)"/)[1];
          if (!catalogKeys.has(name)) {
            console.log('MISSING: ' + name + ' (in ' + path.relative(base, full) + ')');
          }
        }
      }
    }
  }
  return results;
}

findFiles(base);
console.log('Done. Catalog has ' + catalogKeys.size + ' entries');
