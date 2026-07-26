#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为 ydsz-workflow-domain 实体类补全缺失的 BaseDO import。

22 个实体类 extends BaseDO，但其中 5 个缺少 import com.njydsz.common.domain.entity.BaseDO;
导致编译失败。本脚本检测并补全缺失的 import。

遵循 prefer-python-over-powershell.md 规则，使用 Python 处理文件以避免编码损坏。
"""
import pathlib
import re

ENTITY_DIR = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-workflow\ydsz-workflow-domain\src\main\java\com\njydsz\workflow\domain\entity")

BASEDO_IMPORT = "import com.njydsz.common.domain.entity.BaseDO;\n"

changed = []
for fpath in sorted(ENTITY_DIR.glob("*.java")):
    content = fpath.read_text(encoding="utf-8")
    # 仅处理 extends BaseDO 但未 import 的文件
    if "extends BaseDO" not in content:
        continue
    if BASEDO_IMPORT in content:
        continue
    # 在最后一个 import 之后插入（保持 import 分组有序）
    # 找到所有 import 行，在最后一行之后插入
    lines = content.splitlines(keepends=True)
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx == -1:
        # 没有 import，在 package 行之后插入
        for i, line in enumerate(lines):
            if line.startswith("package "):
                # 在 package 行之后的空行之后插入
                insert_idx = i + 1
                while insert_idx < len(lines) and lines[insert_idx].strip() == "":
                    insert_idx += 1
                lines.insert(insert_idx, BASEDO_IMPORT)
                break
    else:
        lines.insert(last_import_idx + 1, BASEDO_IMPORT)
    new_content = "".join(lines)
    fpath.write_text(new_content, encoding="utf-8")
    changed.append(fpath.name)
    print(f"[OK] 已补全 BaseDO import: {fpath.name}")

print(f"\n总计修复 {len(changed)} 个文件")
