#!/usr/bin/env python3
"""Find all toVO methods in Service classes across the project."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

results = []
for root, dirs, files in os.walk(BACKEND):
    # Only scan server modules (where services live)
    if '\\ydsz-' not in root:
        continue
    for fn in files:
        if not fn.endswith('.java'):
            continue
        if 'Service' not in fn and 'ServiceImpl' not in fn:
            continue
        fp = os.path.join(root, fn)
        try:
            with open(fp, 'r', encoding='utf-8') as f:
                content = f.read()
        except:
            continue
        
        # Find toVO method declarations
        for m in re.finditer(r'(public|private|protected)\s+\w+\s+toVO\w*\s*\(', content):
            line_num = content[:m.start()].count('\n') + 1
            rel_path = os.path.relpath(fp, BACKEND)
            results.append((rel_path, line_num, m.group(0).strip()))

print(f"Total toVO methods in Service classes: {len(results)}")
for path, line, sig in sorted(results):
    print(f"  {path}:{line}  {sig}")
