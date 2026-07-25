#!/usr/bin/env python3
"""Scan Redis module for @Component/@Service classes and extract constructor info."""
import os
import re

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-redis\src\main\java'

for root, dirs, files in os.walk(BASE):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        fpath = os.path.join(root, fn)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()

        if '@Component' not in content and '@Service' not in content:
            continue

        class_match = re.search(r'public class (\w+)', content)
        cls_name = class_match.group(1) if class_match else fn.replace('.java', '')

        has_rac = '@RequiredArgsConstructor' in content

        ctor_match = re.search(r'public\s+' + re.escape(cls_name) + r'\s*\(([^)]*)\)', content)
        params = ctor_match.group(1).strip() if ctor_match else 'NO_PUBLIC_CONSTRUCTOR'

        # Get relative path
        rel_path = os.path.relpath(fpath, BASE).replace(os.sep, '/')

        print(f'{rel_path}|{cls_name}|rac={has_rac}|params={params}')
