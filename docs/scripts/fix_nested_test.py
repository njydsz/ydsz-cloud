# -*- coding: utf-8 -*-
"""批量补充 @Nested 测试内部类的类级注释。

规则：对形如
    @Nested
    @DisplayName("xxx")
    class XxxCases {
的测试分组，在 class 声明前插入
    /**
     * 测试分组：xxx
     */
处理范围：扫描全部待办中缺类注释的 Nested 测试类。
"""
import io
import os
import re
import sys
import json

sys.path.insert(0, r'D:\Code\ydsz\ydsz-pmis\docs\scripts')
import scan_comments as sc

ROOT = r'D:\Code\ydsz\ydsz-pmis'
todo = json.load(open(os.path.join(ROOT, 'docs', 'scripts', 'java_todo.json'), encoding='utf-8'))

# 收集所有缺类注释的测试文件
targets = []
for rel, v in todo.items():
    if '/test/' not in rel.replace('\\', '/'):
        continue
    if not v['missing_classes']:
        continue
    full = os.path.join(ROOT, rel)
    targets.append((full, v['missing_classes']))

print(f'待处理的测试文件: {len(targets)}')

CLASS_RE = re.compile(
    r"(?m)^(?P<indent>\s*)class\s+(?P<name>\w+)\s*\{"
)

def process(full_path, missing_classes):
    with io.open(full_path, 'r', encoding='utf-8', errors='replace') as f:
        src = f.read()
    lines = src.split('\n')
    modified = 0
    for m in CLASS_RE.finditer(src):
        name = m.group('name')
        if name not in missing_classes:
            continue
        line_no = src[: m.start()].count('\n')  # 0-based
        # 向上看 4 行，找 @DisplayName("...")
        display = None
        for k in range(line_no - 1, max(line_no - 5, -1), -1):
            t = lines[k].strip()
            dm = re.match(r'@DisplayName\("([^"]+)"\)', t)
            if dm:
                display = dm.group(1)
                break
            if t.startswith('@Nested'):
                continue
            if t.startswith('@') and not t.startswith('@DisplayName'):
                continue
            if not t:
                continue
            break
        if display is None:
            print(f'  [SKIP] {os.path.basename(full_path)}::{name} 未找到 @DisplayName')
            continue
        # 插入注释（在 class 行之前，缩进一致）
        indent = m.group('indent')
        comment = f"{indent}/**\n{indent} * 测试分组：{display}\n{indent} */\n"
        pos = m.start()
        src = src[:pos] + comment + src[pos:]
        lines = src.split('\n')
        modified += 1
        print(f'  [OK] {os.path.basename(full_path)}::{name} <- {display}')
    if modified:
        with io.open(full_path, 'w', encoding='utf-8', newline='') as f:
            f.write(src)
    return modified

total = 0
for full, missing in targets:
    total += process(full, missing)
print(f'共插入 {total} 处注释')
