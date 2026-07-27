#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P1-3 子任务：为 ydsz-project-domain 下所有 VO 批量补齐缺失的 import 语句。

按项目规则 prefer-python-over-powershell.md，使用 Python 而非 PowerShell 处理文件。
仅对缺少 LocalDateTime / BigDecimal / LocalDate import 但又使用到这些类型的文件追加 import。
"""
from __future__ import annotations

import pathlib
import re

VO_DIRS = [
    pathlib.Path(
        "ydsz-backend/ydsz-project/ydsz-project-domain/src/main/java/com/njydsz/project/domain/vo"
    ),
    pathlib.Path(
        "ydsz-backend/ydsz-workflow/ydsz-workflow-domain/src/main/java/com/njydsz/workflow/domain/vo"
    ),
]

# 类型 → import 行
TYPE_IMPORT_MAP = {
    "LocalDateTime": "import java.time.LocalDateTime;",
    "LocalDate": "import java.time.LocalDate;",
    "BigDecimal": "import java.math.BigDecimal;",
}


def fix_file(path: pathlib.Path) -> bool:
    """返回 True 表示文件被修改。"""
    content = path.read_text(encoding="utf-8")
    if not content:
        return False

    lines = content.split("\n")
    changed = False

    # 找到现有 import 区域的最后一行（用于插入新 import）
    last_import_idx = -1
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("import "):
            last_import_idx = i

    if last_import_idx == -1:
        # 没有 import，找 package 行
        for i, line in enumerate(lines):
            if line.strip().startswith("package "):
                last_import_idx = i
                break
        if last_import_idx == -1:
            return False
        # 在 package 行后插入空行 + import
        insert_idx = last_import_idx + 1
        # 跳过已有空行
        while insert_idx < len(lines) and lines[insert_idx].strip() == "":
            insert_idx += 1
        insert_idx -= 1
    else:
        insert_idx = last_import_idx + 1

    new_imports: list[str] = []
    for type_name, import_line in TYPE_IMPORT_MAP.items():
        # 检查文件中是否使用了该类型（粗略匹配字段声明）
        # 排除注释行和字符串
        used = False
        for line in lines:
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if re.search(rf"\b{type_name}\b", line):
                used = True
                break
        if not used:
            continue
        # 检查是否已有 import
        already_imported = any(import_line in line for line in lines)
        if already_imported:
            continue
        new_imports.append(import_line)

    if not new_imports:
        return False

    # 插入新 import
    for imp in new_imports:
        lines.insert(insert_idx, imp)
        insert_idx += 1
        changed = True

    if changed:
        path.write_text("\n".join(lines), encoding="utf-8")
        print(f"  patched: {path.name} (+{len(new_imports)} imports)")
    return changed


def main() -> None:
    fixed_count = 0
    for vo_dir in VO_DIRS:
        if not vo_dir.exists():
            print(f"WARNING: directory not found: {vo_dir}")
            continue
        print(f"\n=== Processing: {vo_dir} ===")
        for java_file in vo_dir.glob("*.java"):
            if fix_file(java_file):
                fixed_count += 1
    print(f"\nTotal files patched: {fixed_count}")


if __name__ == "__main__":
    main()
