#!/usr/bin/env python3
"""Scan message module Java files for comment-to-code ratio."""
import os

base = 'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-message/ydsz-message-server/src/main/java'
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
        rel = fpath.split('message-server')[-1]
        results.append((rel, comment_lines, code_lines, ratio))

results.sort(key=lambda x: x[3])
print(f"{'Comments':>8} {'Code':>6} {'Ratio':>6}  File")
print('-' * 80)
for rel, cl, code, ratio in results[:25]:
    print(f'{cl:8d} {code:6d} {ratio:5.1f}%  {rel}')
