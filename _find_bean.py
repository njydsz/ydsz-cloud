#!/usr/bin/env python3
"""Find all BeanUtils.copyProperties usage in the project."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

results = []
for root, dirs, files in os.walk(BACKEND):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        fp = os.path.join(root, fn)
        try:
            with open(fp, 'r', encoding='utf-8') as f:
                content = f.read()
        except:
            continue
        
        for m in re.finditer(r'BeanUtils\.copyProperties\s*\(', content):
            line_num = content[:m.start()].count('\n') + 1
            rel_path = os.path.relpath(fp, BACKEND)
            results.append((rel_path, line_num))

print(f"Total BeanUtils.copyProperties usage: {len(results)}")
for path, line in sorted(results):
    print(f"  {path}:{line}")