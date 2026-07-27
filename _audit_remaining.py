#!/usr/bin/env python3
"""Scan all web controllers for remaining @RequestBody Entity violations."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# Known DTO/VO/Query/Request patterns to skip
SKIP_PATTERNS = ['DTO', 'VO', 'Query', 'Request', 'String', 'Boolean', 'Integer', 'Long', 
                  'Map', 'List', 'Object', 'MultipartFile', 'ultipartFile']

violations = []
for root, dirs, files in os.walk(BACKEND):
    # Only scan controller/web directories
    if 'controller' not in root.lower() and '\\web\\' not in root.lower():
        continue
    for fn in files:
        if not fn.endswith('.java'):
            continue
        fp = os.path.join(root, fn)
        try:
            with open(fp, 'r', encoding='utf-8') as f:
                content = f.read()
        except:
            continue
        
        for m in re.finditer(r'@RequestBody\s+(\w+)\s+\w+', content):
            param_type = m.group(1)
            if any(x in param_type for x in SKIP_PATTERNS):
                continue
            # Get relative path for display
            rel_path = os.path.relpath(fp, BACKEND)
            violations.append((rel_path, param_type))

print(f"Total remaining @RequestBody Entity violations: {len(violations)}")
for path, entity in sorted(violations):
    print(f"  {entity:30s} in {path}")
