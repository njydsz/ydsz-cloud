#!/usr/bin/env python3
"""Check all DTO files for missing imports."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# Types that need imports
TYPE_IMPORTS = {
    'List': 'java.util.List',
    'Map': 'java.util.Map',
    'Set': 'java.util.Set',
    'LocalDateTime': 'java.time.LocalDateTime',
    'LocalDate': 'java.time.LocalDate',
    'BigDecimal': 'java.math.BigDecimal',
    'ArrayList': 'java.util.ArrayList',
    'HashMap': 'java.util.HashMap',
}

issues = []
for root, dirs, files in os.walk(BACKEND):
    if '\\dto\\post\\' not in root and '\\dto\\put\\' not in root:
        continue
    for fn in files:
        if not fn.endswith('.java'):
            continue
        fp = os.path.join(root, fn)
        with open(fp, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find all types used in field declarations
        for type_name, import_path in TYPE_IMPORTS.items():
            # Check if type is used
            if re.search(rf'\b{type_name}\b', content):
                # Check if import exists
                if import_path not in content:
                    rel_path = os.path.relpath(fp, BACKEND)
                    issues.append((rel_path, type_name, import_path))

if issues:
    print(f"Found {len(issues)} missing imports:")
    for path, type_name, import_path in issues:
        print(f"  {path}: missing {import_path} (for {type_name})")
else:
    print("All imports OK!")
