# -*- coding: utf-8 -*-
"""
注释覆盖率扫描器 v2

判定标准对齐互联网大厂规范（Google Java Style / 阿里巴巴 Java 开发手册 / TSDoc）：

后端 Java：
  必须有文档注释：class / interface / enum / record / @interface
  必须有文档注释的方法：public、protected 方法
  豁免：@Override 方法、getter/setter、构造器、equals/hashCode/toString、
        Lombok 生成、main 方法、单行 builder 链式方法
  字段：DTO/VO/Entity/常量类的字段应有注释

前端 TS/Vue：
  必须有文件头注释
  必须有 JSDoc：export 的 function / class / interface / type / const 函数
  豁免：re-export（export * from / export { } from）、类型收窄的内部实现

用法：
  python scan_comments.py            # 全量扫描并输出报告
  python scan_comments.py <模块名>    # 只看某模块待办明细
"""
import os
import re
import sys
import json
from collections import defaultdict, Counter

ROOT = r"D:\Code\ydsz\ydsz-pmis"
BACKEND = os.path.join(ROOT, "ydsz-backend")
FRONTEND = os.path.join(ROOT, "ydsz-frontend")
OUT = os.path.join(ROOT, ".workbuddy", "scripts", "comment_report.json")

SKIP_DIRS = {"target", "node_modules", "dist", ".git", ".idea", "build",
             ".turbo", "coverage", ".output", ".nuxt", "generated"}

# ---------------------------------------------------------------- 通用工具


def iter_files(base, exts):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if os.path.splitext(fn)[1] in exts:
                yield os.path.join(dirpath, fn)


def read(path):
    for enc in ("utf-8", "gbk"):
        try:
            with open(path, "r", encoding=enc) as f:
                return f.read()
        except (UnicodeDecodeError, LookupError):
            continue
        except Exception:
            return ""
    return ""


def strip_strings_and_comments(src):
    """把字符串字面量内容替换为空格，避免正则误匹配。

    关键：必须**等长替换**，否则 src 与原文 raw 的下标无法对齐，
    会导致 has_doc_before / is_overridden 判定到错误的位置。
    """
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c in ('"', "'"):
            quote = c
            j = i + 1
            while j < n and src[j] != quote:
                if src[j] == "\\":
                    out[j] = " "
                    j += 1
                    if j < n:
                        out[j] = " "
                    j += 1
                    continue
                if src[j] == "\n":       # 未闭合，放弃
                    break
                out[j] = " "
                j += 1
            i = j + 1
        elif c == "/" and i + 1 < n and src[i + 1] == "/":
            j = i
            while j < n and src[j] != "\n":
                out[j] = " "        # 行注释整体抹平，避免其中括号干扰配平
                j += 1
            i = j
        else:
            i += 1
    return "".join(out)


def has_doc_before(src, pos):
    """判断 pos 之前（跳过注解与空行）是否紧邻一个 /** */ 文档注释。

    需正确处理**跨行注解**，例如：

        /** 文档 */
        @Operation(summary = "x",
                   description = "y")
        public void foo()

    此时声明上方一行是 `description = "y")`，并不以 @ 开头，
    必须靠括号配平回溯到 @Operation 才能找到真正的文档注释。
    """
    lines = src[:pos].split("\n")[:-1]
    i = len(lines) - 1
    balance = 0
    while i >= 0:
        s = lines[i].strip()
        if s == "":
            i -= 1
            continue
        if balance == 0 and s.endswith("*/"):
            return True
        balance += s.count(")") - s.count("(")
        if balance > 0:
            i -= 1              # 跨行注解的续行
            continue
        if balance == 0 and s.startswith("@"):
            i -= 1              # 完整注解，继续向上找
            continue
        return False
    return False


def javadoc_misplaced(src, pos):
    """检测 Javadoc 被错误地放在注解之后（注解 → Javadoc → 声明）。

    Javadoc 必须位于所有注解之前，否则 javadoc 工具 / IDE 不会采集，
    等同于注释失效。这是大厂 Code Review 必查项。
    """
    head = src[:pos].rstrip()
    lines = head.split("\n")
    idx = len(lines) - 1
    # 跳过空行
    while idx >= 0 and lines[idx].strip() == "":
        idx -= 1
    if idx < 0 or not lines[idx].strip().endswith("*/"):
        return False
    # 向上找 /** 起始
    while idx >= 0 and "/**" not in lines[idx]:
        idx -= 1
    idx -= 1
    while idx >= 0 and lines[idx].strip() == "":
        idx -= 1
    return idx >= 0 and lines[idx].strip().startswith("@")


def is_overridden(src, pos):
    """判断方法上方是否带 @Override 注解（支持跨行注解回溯）。"""
    lines = src[:pos].split("\n")[:-1]
    i = len(lines) - 1
    balance = 0
    while i >= 0:
        s = lines[i].strip()
        if s == "":
            i -= 1
            continue
        if balance == 0 and (s.endswith("*/") or s.startswith("*") or s.startswith("/*")):
            return False
        balance += s.count(")") - s.count("(")
        if balance > 0:
            i -= 1
            continue
        if balance == 0 and s.startswith("@"):
            if s.startswith("@Override"):
                return True
            i -= 1
            continue
        return False
    return False


# ---------------------------------------------------------------- Java

TYPE_DECL = re.compile(
    r"^[ \t]*(?:public|protected|private)?[ \t]*(?:static[ \t]+)?(?:final[ \t]+)?"
    r"(?:abstract[ \t]+)?(?:sealed[ \t]+)?"
    r"\b(class|interface|enum|record)\b[ \t]+(\w+)",
    re.M,
)

METHOD_DECL = re.compile(
    r"^([ \t]+)(public|protected)[ \t]+"
    r"(?!class\b|interface\b|enum\b|record\b)"
    r"((?:static[ \t]+|final[ \t]+|synchronized[ \t]+|default[ \t]+|abstract[ \t]+|native[ \t]+)*)"
    r"(?:<[^<>]*(?:<[^<>]*>)?[^<>]*>[ \t]+)?"
    r"([\w.$]+(?:<[^;{}()]*?>)?(?:\[\])*)[ \t]+"
    r"(\w+)[ \t]*\(",
    re.M,
)

TRIVIAL_NAME = re.compile(r"^(get|set|is|has)[A-Z_]")
EXEMPT_METHODS = {"equals", "hashCode", "toString", "main", "clone",
                  "builder", "build", "of", "valueOf", "values", "compareTo"}

DTO_LIKE = re.compile(r"(DTO|VO|BO|PO|Entity|Request|Response|Param|Query|Config|Properties|Constants?)$")


def analyze_java(path):
    raw = read(path)
    if not raw.strip():
        return None
    src = strip_strings_and_comments(raw)
    fname = os.path.splitext(os.path.basename(path))[0]

    res = {
        "type_doc_missing": False,
        "methods_total": 0,
        "methods_missing": 0,
        "missing_names": [],
        "misplaced": [],
        "lines": raw.count("\n") + 1,
        "kind": "class",
    }

    if fname == "package-info":
        res["kind"] = "package-info"
        res["type_doc_missing"] = "/**" not in raw
        return res

    m = TYPE_DECL.search(src)
    if m:
        res["kind"] = m.group(1)
        res["type_doc_missing"] = not has_doc_before(src, m.start())
    else:
        return res

    body_start = m.start()
    for mm in METHOD_DECL.finditer(src):
        if mm.start() < body_start:
            continue
        name = mm.group(5)
        ret = mm.group(4)
        if name in EXEMPT_METHODS:
            continue
        if ret == fname:          # 构造器
            continue
        if name == fname:         # 构造器
            continue
        # 注释错位：@Override 在 Javadoc 之前 —— 注释失效，必须修
        if javadoc_misplaced(src, mm.start()):
            if len(res["misplaced"]) < 25:
                res["misplaced"].append(name)
            continue
        if is_overridden(src, mm.start()):
            continue
        if TRIVIAL_NAME.match(name):
            # 简单存取器豁免：方法体在 3 行内
            tail = src[mm.start():mm.start() + 400]
            first_lines = tail.split("\n")[:3]
            if any("return " in l or " = " in l for l in first_lines):
                continue
        res["methods_total"] += 1
        if not has_doc_before(src, mm.start()):
            res["methods_missing"] += 1
            if len(res["missing_names"]) < 25:
                res["missing_names"].append(name)
    return res


# ---------------------------------------------------------------- TS / Vue

EXPORT_DECL = re.compile(
    r"^export[ \t]+(?:default[ \t]+)?(?:declare[ \t]+)?(?:async[ \t]+)?"
    r"\b(function|class|interface|type|enum|const|let)\b[ \t]+(\w+)",
    re.M,
)
RE_EXPORT = re.compile(r"^export\s*(?:\*|\{)", re.M)


def analyze_ts(path):
    raw = read(path)
    if not raw.strip():
        return None
    is_vue = path.endswith(".vue")

    res = {
        "file_doc_missing": False,
        "exports_total": 0,
        "exports_missing": 0,
        "missing_names": [],
        "lines": raw.count("\n") + 1,
    }

    # 文件头：前 20 行内出现 /** 或 <!--
    head = "\n".join(raw.split("\n")[:20])
    res["file_doc_missing"] = ("/**" not in head) and ("<!--" not in head)

    # Vue: 只看 <script> 段
    scope = raw
    if is_vue:
        mm = re.search(r"<script[^>]*>(.*?)</script>", raw, re.S)
        scope = mm.group(1) if mm else ""

    for mm in EXPORT_DECL.finditer(scope):
        kind, name = mm.group(1), mm.group(2)
        # 纯常量（非函数）豁免
        if kind in ("const", "let"):
            tail = scope[mm.end():mm.end() + 200]
            if not re.match(r"[^=\n]*=\s*(?:async\s*)?(?:\([^)]*\)\s*(?::[^=]*)?=>|function)", tail):
                continue
        res["exports_total"] += 1
        if not has_doc_before(scope, mm.start()):
            res["exports_missing"] += 1
            if len(res["missing_names"]) < 25:
                res["missing_names"].append(f"{kind} {name}")
    return res


# ---------------------------------------------------------------- 聚合

def be_module(path):
    rel = os.path.relpath(path, BACKEND).split(os.sep)
    return rel[0]


def fe_module(path):
    rel = os.path.relpath(path, FRONTEND).split(os.sep)
    if rel[0] in ("apps", "comm") and len(rel) > 1:
        return f"{rel[0]}/{rel[1]}"
    return rel[0]


def build():
    report = {"backend": {}, "frontend": {}}

    agg = defaultdict(lambda: {"files": 0, "type_doc_missing": 0, "methods_total": 0,
                               "methods_missing": 0, "misplaced": 0, "lines": 0, "todo": []})
    for p in iter_files(BACKEND, {".java"}):
        r = analyze_java(p)
        if r is None:
            continue
        a = agg[be_module(p)]
        a["files"] += 1
        a["lines"] += r["lines"]
        a["methods_total"] += r["methods_total"]
        a["methods_missing"] += r["methods_missing"]
        a["misplaced"] += len(r["misplaced"])
        if r["type_doc_missing"]:
            a["type_doc_missing"] += 1
        if r["type_doc_missing"] or r["methods_missing"] or r["misplaced"]:
            a["todo"].append({
                "file": os.path.relpath(p, ROOT),
                "type_doc": r["type_doc_missing"],
                "miss": r["methods_missing"],
                "names": r["missing_names"],
                "misplaced": r["misplaced"],
            })
    report["backend"] = dict(agg)

    agg2 = defaultdict(lambda: {"files": 0, "file_doc_missing": 0, "exports_total": 0,
                                "exports_missing": 0, "lines": 0, "todo": []})
    for p in iter_files(FRONTEND, {".ts", ".tsx", ".vue"}):
        r = analyze_ts(p)
        if r is None:
            continue
        a = agg2[fe_module(p)]
        a["files"] += 1
        a["lines"] += r["lines"]
        a["exports_total"] += r["exports_total"]
        a["exports_missing"] += r["exports_missing"]
        if r["file_doc_missing"]:
            a["file_doc_missing"] += 1
        if r["file_doc_missing"] or r["exports_missing"]:
            a["todo"].append({
                "file": os.path.relpath(p, ROOT),
                "file_doc": r["file_doc_missing"],
                "miss": r["exports_missing"],
                "names": r["missing_names"],
            })
    report["frontend"] = dict(agg2)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    return report


def summary(report):
    print("=" * 84)
    print("后端 Java（排除 @Override / getter-setter / 构造器 等豁免项）")
    print("=" * 84)
    print(f"{'模块':<18}{'文件':>6}{'缺类注释':>9}{'需注释方法':>11}{'缺注释':>8}{'覆盖率':>9}{'注释错位':>9}{'待办':>7}")
    T = Counter()
    for k in sorted(report["backend"]):
        v = report["backend"][k]
        tot, mis = v["methods_total"], v["methods_missing"]
        cov = ((tot - mis) / tot * 100) if tot else 100.0
        print(f"{k:<18}{v['files']:>6}{v['type_doc_missing']:>9}{tot:>11}{mis:>8}"
              f"{cov:>8.1f}%{v['misplaced']:>9}{len(v['todo']):>7}")
        T["f"] += v["files"]; T["t"] += v["type_doc_missing"]
        T["mt"] += tot; T["mm"] += mis; T["td"] += len(v["todo"])
        T["mp"] += v["misplaced"]
    cov = ((T["mt"] - T["mm"]) / T["mt"] * 100) if T["mt"] else 100
    print("-" * 84)
    print(f"{'合计':<18}{T['f']:>6}{T['t']:>9}{T['mt']:>11}{T['mm']:>8}"
          f"{cov:>8.1f}%{T['mp']:>9}{T['td']:>7}")

    print()
    print("=" * 84)
    print("前端 TS/Vue")
    print("=" * 84)
    print(f"{'模块':<22}{'文件':>6}{'缺文件头':>9}{'需注释导出':>11}{'缺注释':>8}{'导出覆盖':>10}{'待办':>7}")
    T2 = Counter()
    for k in sorted(report["frontend"]):
        v = report["frontend"][k]
        tot, mis = v["exports_total"], v["exports_missing"]
        cov = ((tot - mis) / tot * 100) if tot else 100.0
        print(f"{k:<22}{v['files']:>6}{v['file_doc_missing']:>9}{tot:>11}{mis:>8}"
              f"{cov:>9.1f}%{len(v['todo']):>7}")
        T2["f"] += v["files"]; T2["t"] += v["file_doc_missing"]
        T2["mt"] += tot; T2["mm"] += mis; T2["td"] += len(v["todo"])
    cov = ((T2["mt"] - T2["mm"]) / T2["mt"] * 100) if T2["mt"] else 100
    print("-" * 84)
    print(f"{'合计':<22}{T2['f']:>6}{T2['t']:>9}{T2['mt']:>11}{T2['mm']:>8}{cov:>9.1f}%{T2['td']:>7}")
    print()
    print(f"待办文件总数: {T['td'] + T2['td']}    明细: {OUT}")


def detail(report, mod):
    for side in ("backend", "frontend"):
        if mod in report[side]:
            v = report[side][mod]
            print(f"== {mod} 待办 {len(v['todo'])} 个文件")
            for t in v["todo"]:
                flag = "类头" if t.get("type_doc") or t.get("file_doc") else "  "
                print(f"[{flag}] miss={t['miss']:<3} {t['file']}")
                if t["names"]:
                    print(f"        -> {', '.join(t['names'])}")
            return
    print(f"未找到模块 {mod}")


if __name__ == "__main__":
    rep = build() if not os.path.exists(OUT) or "--rescan" in sys.argv or len(sys.argv) == 1 else json.load(open(OUT, encoding="utf-8"))
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if args:
        rep = json.load(open(OUT, encoding="utf-8"))
        detail(rep, args[0])
    else:
        summary(rep)
