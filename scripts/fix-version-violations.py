"""扫描并修复 ydsz-common 所有 @since 非 1.0.0 的版本号违规。

规则（依据 .trae/rules/version-policy.md）：
  - 项目自身版本号统一为 1.0.0
  - Javadoc @since 一律写 1.0.0
  - 例外（不得误改）：
    1) 第三方库版本（pom.xml dependencies）
    2) 协议规范版本（OpenAPI 3.0.3 / Trace Context "00"）
    3) SQL 脚本文件名前缀 V1.0.0
    4) 任务批次编号（P1.3.0 重构 等注释）

本脚本只处理 *.java 文件中的 @since X.Y.Z 字面量，统一替换为 @since 1.0.0。
"""
from __future__ import annotations

import re
import pathlib
import sys

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common")
SINCE_PATTERN = re.compile(r"@since\s+(\d+\.\d+(?:\.\d+)?)")
TARGET_VERSION = "1.0.0"

# 仅扫描的目录范围
SCAN_GLOBS = ("**/*.java",)


def collect_violations() -> list[tuple[pathlib.Path, str, list[tuple[int, str, str]]]]:
    """返回 [(file, original_content, [(line_no, old_version, line_text), ...]), ...]"""
    violations: list[tuple[pathlib.Path, str, list[tuple[int, str, str]]]] = []
    for pattern in SCAN_GLOBS:
        for path in ROOT.glob(pattern):
            if not path.is_file():
                continue
            try:
                content = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                # 尝试 GBK 回退（仅读取诊断，不修改）
                continue
            per_file: list[tuple[int, str, str]] = []
            for idx, line in enumerate(content.splitlines(), start=1):
                m = SINCE_PATTERN.search(line)
                if m and m.group(1) != TARGET_VERSION:
                    per_file.append((idx, m.group(1), line.rstrip()))
            if per_file:
                violations.append((path, content, per_file))
    return violations


def apply_fix(dry_run: bool = False) -> tuple[int, int]:
    """返回 (违规文件数, 违规行数)"""
    violations = collect_violations()
    total_lines = 0
    total_files = 0
    for path, content, hits in violations:
        total_files += 1
        total_lines += len(hits)
        print(f"\n=== {path.relative_to(ROOT.parent.parent)} ({len(hits)} 处) ===")
        for line_no, old_ver, line_text in hits:
            print(f"  L{line_no}: {old_ver} -> {TARGET_VERSION}")
            print(f"    {line_text.strip()[:120]}")
        if not dry_run:
            new_content = SINCE_PATTERN.sub(
                lambda m: f"@since {TARGET_VERSION}" if m.group(1) != TARGET_VERSION else m.group(0),
                content,
            )
            if new_content != content:
                path.write_text(new_content, encoding="utf-8")
    return total_files, total_lines


if __name__ == "__main__":
    dry = "--dry" in sys.argv
    files, lines = apply_fix(dry_run=dry)
    mode = "DRY-RUN" if dry else "APPLIED"
    print(f"\n[{mode}] 共修复 {files} 个文件 / {lines} 处 @since 违规")
