# -*- coding: utf-8 -*-
"""列出 ydsz-common 下所有非测试 Java 文件，并统计每个文件的注释率，按注释率从低到高排序。"""
import os, re, sys

root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend', 'ydsz-common')
results = []
for r, _, fs in os.walk(root):
    for f in fs:
        if not f.endswith('.java'):
            continue
        if 'test' in r.lower():
            continue
        if '/target/' in r.replace('\\', '/'):
            continue
        path = os.path.join(r, f)
        try:
            with open(path, 'r', encoding='utf-8') as fh:
                content = fh.read()
        except Exception:
            continue
        lines = content.splitlines()
        total = len(lines)
        comment_lines = 0
        for ln in lines:
            stripped = ln.strip()
            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                comment_lines += 1
        ratio = comment_lines / total if total > 0 else 0
        rel = os.path.relpath(path, root)
        results.append((ratio, comment_lines, total, rel))

results.sort(key=lambda x: x[0])
print(f"Total: {len(results)} files")
for ratio, cl, tl, rel in results:
    print(f"{ratio*100:5.1f}%  {cl:4d}/{tl:4d}  {rel}")
