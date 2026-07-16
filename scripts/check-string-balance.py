#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查 Java 文件中的字符串平衡问题。"""

import pathlib

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")


def check_file(filepath):
    """检查 Java 文件中字符串是否正确闭合。"""
    content = pathlib.Path(filepath).read_text(encoding="utf-8")
    lines = content.split('\n')

    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    escape = False

    issues = []

    for i, line in enumerate(lines, 1):
        j = 0
        line_len = len(line)
        while j < line_len:
            c = line[j]

            if escape:
                escape = False
                j += 1
                continue

            if c == '\\' and (in_string or in_char):
                escape = True
                j += 1
                continue

            if in_line_comment:
                break  # 行注释到行尾结束

            if in_block_comment:
                if c == '*' and j + 1 < line_len and line[j + 1] == '/':
                    in_block_comment = False
                    j += 2
                    continue
                j += 1
                continue

            if in_string:
                if c == '"':
                    in_string = False
                j += 1
                continue

            if in_char:
                if c == "'":
                    in_char = False
                j += 1
                continue

            # 不在字符串/字符/注释中
            if c == '/' and j + 1 < line_len:
                if line[j + 1] == '/':
                    in_line_comment = True
                    break
                elif line[j + 1] == '*':
                    in_block_comment = True
                    j += 2
                    continue
            if c == '"':
                in_string = True
                j += 1
                continue
            if c == "'":
                in_char = True
                j += 1
                continue
            j += 1

        # 行结束时检查状态
        if in_string:
            issues.append(f"Line {i}: 字符串未闭合: {line.rstrip()[:80]}")
            # 重置状态，继续检查
            in_string = False
        if in_char:
            issues.append(f"Line {i}: 字符未闭合: {line.rstrip()[:80]}")
            in_char = False
        in_line_comment = False

    return issues


def main():
    files = [
        ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/engine/pg/PgSearchEngine.java",
        ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/service/IndexRebuildService.java",
    ]

    for f in files:
        print(f"\n=== 检查 {f.name} ===")
        issues = check_file(f)
        if issues:
            for issue in issues:
                print(f"  {issue}")
        else:
            print("  无字符串平衡问题")


if __name__ == "__main__":
    main()
