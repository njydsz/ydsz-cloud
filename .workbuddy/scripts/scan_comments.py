#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
注释覆盖率扫描器（YDSZ-PMIS）

依据 docs/CODE_COMMENT_STANDARD.md 对全项目 Java / TS / Vue 源码做静态扫描，
输出各模块的注释覆盖率与待办明细。

用法:
    python .workbuddy/scripts/scan_comments.py              # 全量汇总
    python .workbuddy/scripts/scan_comments.py ydsz-common  # 指定模块明细
    python .workbuddy/scripts/scan_comments.py --json       # 输出 JSON（供自动化消费）
    python .workbuddy/scripts/scan_comments.py --todo ydsz-project  # 列出待办文件清单
"""
from __future__ import annotations

import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

SKIP_DIR_PARTS = {
    "target", "node_modules", "dist", ".git", ".idea", "build",
    ".turbo", "coverage", ".nuxt", ".output", "__pycache__",
}

# ---------------------------------------------------------------- Java 扫描

# 类型声明：class / interface / enum / record / @interface
JAVA_TYPE_RE = re.compile(
    r"^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(class|interface|enum|record|@interface)\s+(\w+)"
)

# public/protected 方法（排除注解定义体、排除 record 紧凑构造）
JAVA_METHOD_RE = re.compile(
    r"^\s*(public|protected)\s+"
    r"(?!(?:class|interface|enum|record|@interface)\b)"
    r"(?:static\s+|final\s+|abstract\s+|synchronized\s+|native\s+|default\s+|<[^>]+>\s*)*"
    r"[\w$<>\[\],.?\s]+?\s+(\w+)\s*\("
)

GETTER_SETTER_RE = re.compile(r"^(get|set|is|has)[A-Z_]")
EXEMPT_METHODS = {"equals", "hashCode", "toString", "main", "clone", "compareTo"}

# DTO/VO/Entity 字段
JAVA_FIELD_RE = re.compile(
    r"^\s*(?:private|protected|public)\s+(?!static\s+final\b)"
    r"(?:final\s+|volatile\s+|transient\s+)*"
    r"[\w$<>\[\],.?\s]+\s+(\w+)\s*(?:=|;)"
)

DATA_CLASS_HINT = re.compile(r"(DTO|VO|BO|PO|Entity|Query|Request|Response|Param|Form)$")


def _strip_java_strings(line: str) -> str:
    """去掉字符串字面量，避免其中的括号/分号干扰正则。"""
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', line)


def scan_java(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except (UnicodeDecodeError, OSError):
        return {}

    cls_name = os.path.splitext(os.path.basename(path))[0]
    is_data_class = bool(DATA_CLASS_HINT.search(cls_name))

    res = {
        "type_total": 0, "type_doc": 0,
        "method_total": 0, "method_doc": 0,
        "field_total": 0, "field_doc": 0,
        "misplaced": 0,
        "missing": [],
    }

    in_block_comment = False
    # 记录"上一段有效内容"：javadoc / 注解 / 其它
    doc_pending = False          # 最近是否出现 javadoc 且尚未被消费
    saw_annotation = False       # 当前声明前是否有注解
    doc_after_annotation = False # javadoc 出现在注解之后 -> 错位
    annotation_depth = 0         # 多行注解的括号深度（>0 表示仍在注解参数内）
    depth = 0                    # 大括号深度
    type_depth = None            # 类型声明所在深度

    for raw in lines:
        line = raw.strip()

        # --- 块注释状态机 ---
        if in_block_comment:
            if "*/" in line:
                in_block_comment = False
            continue

        # --- 多行注解续行：不消费 doc_pending，仅跟踪括号深度 ---
        if annotation_depth > 0:
            c = _strip_java_strings(line)
            annotation_depth += c.count("(") - c.count(")") + c.count("{") - c.count("}")
            if annotation_depth <= 0:
                annotation_depth = 0
            continue

        if line.startswith("/**"):
            if "*/" not in line:
                in_block_comment = True
            doc_pending = True
            if saw_annotation:
                doc_after_annotation = True
            continue
        if line.startswith("/*"):
            if "*/" not in line:
                in_block_comment = True
            continue
        if not line or line.startswith("//"):
            continue
        if line.startswith("package ") or line.startswith("import "):
            doc_pending = False
            continue

        # --- 注解 ---
        if line.startswith("@") and not JAVA_TYPE_RE.match(line):
            saw_annotation = True
            c = _strip_java_strings(line)
            annotation_depth = c.count("(") - c.count(")") + c.count("{") - c.count("}")
            continue

        clean = _strip_java_strings(line)

        # --- 类型声明 ---
        m_type = JAVA_TYPE_RE.match(line)
        if m_type and type_depth is None:
            res["type_total"] += 1
            if doc_pending:
                res["type_doc"] += 1
                if doc_after_annotation:
                    res["misplaced"] += 1
                    res["missing"].append(f"[错位] type {m_type.group(2)}")
            else:
                res["missing"].append(f"[缺类注释] {m_type.group(2)}")
            type_depth = depth
            doc_pending = saw_annotation = doc_after_annotation = False
            depth += clean.count("{") - clean.count("}")
            continue

        # --- 方法 ---
        m_mth = JAVA_METHOD_RE.match(line)
        if m_mth and "=" not in clean.split("(")[0]:
            name = m_mth.group(2)
            exempt = name in EXEMPT_METHODS or (
                GETTER_SETTER_RE.match(name) and clean.rstrip().endswith((";", "}"))
            )
            if not exempt and name != cls_name:  # 排除构造器
                res["method_total"] += 1
                if doc_pending:
                    res["method_doc"] += 1
                    if doc_after_annotation:
                        res["misplaced"] += 1
                        res["missing"].append(f"[错位] method {name}()")
                else:
                    res["missing"].append(f"[缺方法注释] {name}()")
            doc_pending = saw_annotation = doc_after_annotation = False
            depth += clean.count("{") - clean.count("}")
            continue

        # --- 字段（仅数据类统计） ---
        if is_data_class:
            m_fld = JAVA_FIELD_RE.match(line)
            if m_fld and "(" not in clean.split("=")[0]:
                res["field_total"] += 1
                if doc_pending:
                    res["field_doc"] += 1
                else:
                    res["missing"].append(f"[缺字段注释] {m_fld.group(1)}")
                doc_pending = saw_annotation = doc_after_annotation = False
                depth += clean.count("{") - clean.count("}")
                continue

        depth += clean.count("{") - clean.count("}")
        doc_pending = saw_annotation = doc_after_annotation = False

    return res


# ---------------------------------------------------------------- 前端扫描

TS_EXPORT_RE = re.compile(
    r"^export\s+(?:default\s+)?(?:async\s+)?"
    r"(function|const|class|interface|type|enum)\s+(\w+)"
)


def scan_ts(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as fh:
            content = fh.read()
    except (UnicodeDecodeError, OSError):
        return {}

    lines = content.splitlines()
    res = {
        "file_total": 1, "file_doc": 0,
        "export_total": 0, "export_doc": 0,
        "missing": [],
    }

    # --- 文件头注释：前 20 行内出现 /** 或 <!-- ---
    head = "\n".join(lines[:20])
    if re.search(r"/\*\*", head) or re.search(r"<!--", head):
        res["file_doc"] = 1
    else:
        res["missing"].append("[缺文件头注释]")

    # --- 导出成员 ---
    in_block = False
    doc_pending = False
    for raw in lines:
        line = raw.strip()
        if in_block:
            if "*/" in line:
                in_block = False
            continue
        if line.startswith("/**"):
            if "*/" not in line:
                in_block = True
            doc_pending = True
            continue
        if line.startswith("/*"):
            if "*/" not in line:
                in_block = True
            continue
        if not line or line.startswith("//"):
            continue

        m = TS_EXPORT_RE.match(line)
        if m:
            res["export_total"] += 1
            if doc_pending:
                res["export_doc"] += 1
            else:
                res["missing"].append(f"[缺导出注释] {m.group(1)} {m.group(2)}")
        doc_pending = False

    return res


def scan_vue(path: str) -> dict:
    res = scan_ts(path)
    if not res:
        return {}
    return res


# ---------------------------------------------------------------- 遍历与聚合

def module_of(rel: str) -> str:
    parts = rel.replace("\\", "/").split("/")
    if parts[0] == "ydsz-backend":
        if len(parts) > 2 and parts[1] == "ydsz-common":
            return f"ydsz-common/{parts[2]}"
        return parts[1] if len(parts) > 1 else "ydsz-backend"
    if parts[0] == "ydsz-frontend":
        if len(parts) > 2 and parts[1] in ("apps", "comm"):
            return f"{parts[1]}/{parts[2]}"
        return f"ydsz-frontend/{parts[1]}" if len(parts) > 1 else "ydsz-frontend"
    return parts[0]


def walk(filter_mod: str | None = None):
    stats = defaultdict(lambda: defaultdict(int))
    todos = defaultdict(list)

    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIR_PARTS]
        for fn in filenames:
            ext = os.path.splitext(fn)[1]
            if ext not in (".java", ".ts", ".vue", ".tsx"):
                continue
            if fn.endswith((".d.ts", ".spec.ts", ".test.ts")):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, ROOT).replace("\\", "/")
            mod = module_of(rel)
            if filter_mod and not mod.startswith(filter_mod) and filter_mod not in rel:
                continue

            r = scan_java(full) if ext == ".java" else (
                scan_vue(full) if ext == ".vue" else scan_ts(full))
            if not r:
                continue
            stats[mod]["files"] += 1
            for k, v in r.items():
                if k == "missing":
                    if v:
                        todos[mod].append((rel, v))
                else:
                    stats[mod][k] += v
    return stats, todos


def pct(a: int, b: int) -> str:
    return f"{a / b * 100:5.1f}%" if b else "  n/a"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = {a for a in sys.argv[1:] if a.startswith("--")}
    filter_mod = args[0] if args else None

    stats, todos = walk(filter_mod)

    if "--json" in flags:
        print(json.dumps({m: dict(s) for m, s in stats.items()},
                         ensure_ascii=False, indent=2))
        return

    if "--todo" in flags:
        for mod in sorted(todos):
            print(f"\n### {mod}")
            for rel, items in sorted(todos[mod]):
                print(f"  {rel}")
                for it in items[:12]:
                    print(f"      - {it}")
                if len(items) > 12:
                    print(f"      ... 另有 {len(items) - 12} 项")
        return

    # ---- 汇总表 ----
    hdr = (f"{'模块':<32}{'文件':>6}{'类注释':>9}{'方法注释':>10}"
           f"{'字段注释':>10}{'错位':>6}{'FE头':>8}{'FE导出':>9}")
    print(hdr)
    print("-" * len(hdr.encode("gbk", "ignore")))

    tot = defaultdict(int)
    for mod in sorted(stats):
        s = stats[mod]
        for k, v in s.items():
            tot[k] += v
        print(f"{mod:<32}{s['files']:>6}"
              f"{pct(s['type_doc'], s['type_total']):>10}"
              f"{pct(s['method_doc'], s['method_total']):>11}"
              f"{pct(s['field_doc'], s['field_total']):>11}"
              f"{s['misplaced']:>6}"
              f"{pct(s['file_doc'], s['file_total']):>9}"
              f"{pct(s['export_doc'], s['export_total']):>10}")

    print("-" * len(hdr.encode("gbk", "ignore")))
    print(f"{'合计':<32}{tot['files']:>6}"
          f"{pct(tot['type_doc'], tot['type_total']):>10}"
          f"{pct(tot['method_doc'], tot['method_total']):>11}"
          f"{pct(tot['field_doc'], tot['field_total']):>11}"
          f"{tot['misplaced']:>6}"
          f"{pct(tot['file_doc'], tot['file_total']):>9}"
          f"{pct(tot['export_doc'], tot['export_total']):>10}")

    print(f"\n后端: 类 {tot['type_doc']}/{tot['type_total']}  "
          f"方法 {tot['method_doc']}/{tot['method_total']}  "
          f"字段 {tot['field_doc']}/{tot['field_total']}  错位 {tot['misplaced']}")
    print(f"前端: 文件头 {tot['file_doc']}/{tot['file_total']}  "
          f"导出 {tot['export_doc']}/{tot['export_total']}")


if __name__ == "__main__":
    main()
