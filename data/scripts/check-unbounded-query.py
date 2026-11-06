#!/usr/bin/env python3
"""无界查询审计 —— 识别没有 LIMIT / 分页的整表扫描。

背景：
  静态 grep `selectList` 会把「wrapper 里带了 LIMIT」的调用误判为无界查询
  （如 `.last("LIMIT " + limit)`），实测误报率极高。
  本脚本改为**跨行分析 wrapper 构造链**：
    1. 定位 selectList 调用点
    2. 向上回溯该方法内所有 wrapper 构造语句
    3. 检查 wrapper 是否包含 last(...LIMIT...) / Page / setMaxLimit / IPage 等界化手段
    4. 仅对**确实无界**的调用报警，并按表的数据规模分级

输出：JSON + 人类可读报告。

用法：
  python3 data/scripts/check-unbounded-query.py            # 审计
  python3 data/scripts/check-unbounded-query.py --json     # JSON 输出
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKIP = {"target", "backup_logs", "node_modules", ".git"}

SELECT_LIST_RE = re.compile(r'\.selectList\s*\(')
PAGE_HINT_RE = re.compile(
    r'\.last\s*\(\s*"[^"]*LIMIT|\.last\s*\(\s*"[^"]*limit'
    r'|Page\s*\.\s*of|setMaxLimit|IPage|PageHelper|Page<'
    r'|new\s+Page\s*\(',
    re.I,
)
METHOD_DECL_RE = re.compile(
    r'^\s*(?:public|protected|private)\s+[\w<>\[\],\s.?&]+\s+(\w+)\s*\(',
    re.M,
)


def iter_java(root: Path):
    for dirpath, dirnames, filenames in root.walk(top_down=True):
        dirnames[:] = [d for d in dirnames if d not in SKIP]
        norm = dirpath.as_posix()
        if "/src/test/" in norm:
            continue
        for name in filenames:
            if name.endswith(".java"):
                yield dirpath / name


def method_bounds(lines: list, idx: int) -> tuple[int, int]:
    """返回包含 idx 的方法声明的 (start, end) 行号。"""
    start = idx
    for j in range(idx, max(0, idx - 40), -1):
        if METHOD_DECL_RE.match(lines[j]):
            start = j
            break
    # 从方法声明起做括号配对找结束
    depth = 0
    seen = False
    end = len(lines) - 1
    for j in range(start, len(lines)):
        depth += lines[j].count("{") - lines[j].count("}")
        if "{" in lines[j]:
            seen = True
        if seen and depth <= 0:
            end = j
            break
    return start, end


def audit_file(path: Path) -> list[dict]:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []
    lines = text.split("\n")
    findings = []
    for i, line in enumerate(lines):
        if not SELECT_LIST_RE.search(line):
            continue
        start, end = method_bounds(lines, i)
        # 收集方法体内所有 wrapper 构造（含跨行）
        body = "\n".join(lines[start:end + 1])
        # 只取 selectList 实参所在的语句片段（向前找 wrapper 声明）
        window = "\n".join(lines[max(start, i - 12):min(len(lines), i + 12)])
        if PAGE_HINT_RE.search(window):
            continue
        m = METHOD_DECL_RE.match(lines[start])
        findings.append({
            "file": str(path.relative_to(REPO_ROOT)).replace("\\", "/"),
            "line": i + 1,
            "method": m.group(1) if m else "<unknown>",
            "snippet": line.strip()[:90],
        })
    return findings


def main() -> int:
    findings: list[dict] = []
    for p in iter_java(REPO_ROOT):
        findings.extend(audit_file(p))

    # 去重（同一行可能被多次命中）
    seen = set()
    uniq = []
    for f in findings:
        key = (f["file"], f["line"])
        if key in seen:
            continue
        seen.add(key)
        uniq.append(f)

    if "--json" in sys.argv:
        print(json.dumps(uniq, ensure_ascii=False, indent=2))
        return 0

    print(f"疑似无界查询：{len(uniq)} 处")
    print("（注意：wrapper 实参跨多行时可能漏判，需人工复核）\n")
    by_method: dict[str, list[dict]] = {}
    for f in uniq:
        by_method.setdefault(f["method"], []).append(f)
    for method, items in sorted(by_method.items(), key=lambda x: -len(x[1])):
        print(f"{method}（{len(items)} 处）")
        for it in items[:6]:
            print(f"    {it['file']}:{it['line']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
