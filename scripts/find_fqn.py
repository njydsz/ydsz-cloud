#!/usr/bin/env python3
"""Find FQN violations in specified modules."""
import os
import re

MODULES = [
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-audit',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-feign',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-auth',
]

# Pattern: java.xxx.Yyy outside strings and comments
PATTERN = re.compile(r'(?<![a-zA-Z."])java\.(util|time|io|concurrent|lang|sql|net|math|nio)\.[A-Z]\w*(?:\.[a-z]\w*)*(?:\.[A-Z]\w*)*')

for module in MODULES:
    for root, dirs, files in os.walk(module):
        if 'target' in root or 'test' in root:
            continue
        for fn in files:
            if not fn.endswith('.java'):
                continue
            fpath = os.path.join(root, fn)
            with open(fpath, 'r', encoding='utf-8') as f:
                for i, line in enumerate(f, 1):
                    # Skip comment lines
                    stripped = line.strip()
                    if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                        continue
                    # Skip string literals (simple check)
                    if stripped.startswith('"') and stripped.endswith('";'):
                        continue
                    matches = PATTERN.findall(line)
                    if matches:
                        for m in matches:
                            # Find full match
                            full_match = re.search(r'(?<![a-zA-Z."])java\.' + m + r'\.[A-Z]\w*(?:\.[a-z]\w*)*(?:\.[A-Z]\w*)*', line)
                            if full_match:
                                rel = os.path.relpath(fpath, module)
                                print(f'{rel}:{i}: {full_match.group()} | {stripped[:80]}')
