#!/usr/bin/env python3
"""Scan common module Java files for comment-to-code ratio - find files needing work."""
import os

base = 'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common'
results = []

for root, _, fnames in os.walk(base):
    for fn in fnames:
        if not fn.endswith('.java'):
            continue
        fpath = os.path.join(root, fn)
        with open(fpath, encoding='utf-8') as f:
            lines = f.readlines()
        if len(lines) < 30:
            continue
        comment_lines = sum(1 for l in lines if l.strip().startswith(('/**', '*', '//', '*/')))
        code_lines = sum(1 for l in lines if l.strip() and not l.strip().startswith(('/**', '*', '//', '*/', 'package', 'import', '@')))
        if code_lines == 0:
            continue
        ratio = comment_lines / code_lines * 100
        # Only include files with ratio < 15%
        if ratio >= 15:
            continue
        rel = fpath.split('ydsz-common')[-1]
        results.append((rel, comment_lines, code_lines, ratio))

results.sort(key=lambda x: x[3])
print(f"Files with comment ratio < 15%: {len(results)}")
print(f"{'Comments':>8} {'Code':>6} {'Ratio':>6}  File")
print('-' * 100)
for rel, cl, code, ratio in results[:40]:
    print(f'{cl:8d} {code:6d} {ratio:5.1f}%  {rel}')
