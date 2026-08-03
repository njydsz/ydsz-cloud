#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
"仅注释改动" 校验器（YDSZ-PMIS）

对全部已修改 / 新增的源码文件，剥离注释与空白后与 git 基线逐字节比对。
只要有效代码发生任何变化即判定失败 —— 用于保证补注释工作绝不改动业务逻辑。

用法:
    python .workbuddy/scripts/verify_comments_only.py            # 对比 HEAD
    python .workbuddy/scripts/verify_comments_only.py <ref>      # 对比指定 commit
"""
from __future__ import annotations

import re
import subprocess
import sys

EXTS = (".java", ".ts", ".vue", ".tsx")


def sh(*args: str) -> str:
    return subprocess.run(args, capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


def strip_comments(src: str, ext: str) -> str:
    """剥离注释与字符串外的空白；字符串字面量原样保留。"""
    out = []
    i, n = 0, len(src)
    in_s = None  # 当前字符串定界符

    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""

        if in_s:
            out.append(c)
            if c == "\\":
                if i + 1 < n:
                    out.append(src[i + 1])
                    i += 2
                    continue
            elif c == in_s:
                in_s = None
            i += 1
            continue

        # 进入字符串
        if c in "\"'`":
            in_s = c
            out.append(c)
            i += 1
            continue

        # 块注释
        if c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            i = n if j == -1 else j + 2
            continue

        # 行注释
        if c == "/" and nxt == "/":
            j = src.find("\n", i)
            i = n if j == -1 else j
            continue

        # Vue/HTML 注释
        if ext == ".vue" and src.startswith("<!--", i):
            j = src.find("-->", i + 4)
            i = n if j == -1 else j + 3
            continue

        out.append(c)
        i += 1

    # 归一化所有空白
    return re.sub(r"\s+", " ", "".join(out)).strip()


def main() -> int:
    ref = sys.argv[1] if len(sys.argv) > 1 else "HEAD"

    changed = [p for p in sh("git", "diff", "--name-only", ref).splitlines()
               if p.strip().endswith(EXTS)]

    if not changed:
        print("没有已修改的源码文件。")
        return 0

    bad, ok, added = [], 0, 0
    for path in changed:
        ext = "." + path.rsplit(".", 1)[-1]
        old = sh("git", "show", f"{ref}:{path}")
        if not old:
            added += 1
            continue
        try:
            with open(path, encoding="utf-8") as fh:
                new = fh.read()
        except OSError as exc:
            bad.append((path, f"读取失败: {exc}"))
            continue

        a, b = strip_comments(old, ext), strip_comments(new, ext)
        if a == b:
            ok += 1
        else:
            k = next((x for x in range(min(len(a), len(b))) if a[x] != b[x]),
                     min(len(a), len(b)))
            bad.append((path,
                        f"代码发生变化 @偏移{k}\n      基线: ...{a[max(0,k-60):k+60]}...\n"
                        f"      现状: ...{b[max(0,k-60):k+60]}..."))

    print(f"校验完成（基线 {ref}）：{ok} 个文件仅注释改动 ✅"
          + (f"，{added} 个新增文件（跳过）" if added else ""))
    if bad:
        print(f"\n❌ {len(bad)} 个文件的有效代码被改动：")
        for p, why in bad:
            print(f"  - {p}\n      {why}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
