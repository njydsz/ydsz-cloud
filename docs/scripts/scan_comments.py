#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""注释覆盖率扫描脚本（YDSZ-PMIS）。

对标 docs/CODE_COMMENT_STANDARD.md 的达标线：
- 后端：类级 Javadoc 100%；public/protected 方法 Javadoc >= 98%；Javadoc 错位 0
- 前端：文件头注释 100%；导出成员 JSDoc >= 98%

用法：
    python scan_comments.py                  # 全量扫描，输出按模块汇总
    python scan_comments.py <模块关键字>       # 只看匹配模块，如 ydsz-common / comm/@core
    python scan_comments.py --todo <模块>     # 列出待处理文件明细
"""
import io
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BACKEND = os.path.join(ROOT, "ydsz-backend")
FRONTEND = os.path.join(ROOT, "ydsz-frontend")

SKIP_DIRS = {"target", "node_modules", "dist", ".turbo", "coverage", "out", "build"}


def walk(root: str, exts: tuple) -> list:
    files = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for f in filenames:
            if f.endswith(exts):
                files.append(os.path.join(dirpath, f))
    return sorted(files)


# ---------- Java 分析 ----------

JAVA_CLASS_RE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private|abstract|final|static|strictfp|sealed|non-sealed|@\w+)\s+)*"
    r"(?:class|interface|enum|record|@interface)\s+(\w+)"
)
JAVA_METHOD_RE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private|public\s+static|static|final|abstract|synchronized|default|"
    r"native|strictfp)\s+)*"
    r"(?P<ret>[\[\]\w<>?,\s\.]+?)\s+(?P<name>\w+)\s*\((?P<args>[^)]*)\)\s*(?:\{|throws|\w|$)"
)
JAVA_MODIFIER_RE = re.compile(r"\b(?:public|protected|private|static|final|abstract|default|synchronized|native|strictfp)\b")

SKIP_METHODS = {"equals", "hashCode", "toString", "main", "clone", "wait", "notify", "notifyAll"}
# 函数式接口的抽象方法名（@FunctionalInterface 单方法接口，规范允许省略）
FUNCTIONAL_IFACE_METHODS = {"run", "call", "apply", "accept", "test", "supply", "get"}


def strip_block_comments(src: str):
    """删除 /* */ 块注释（含 Javadoc），返回 (无注释源码, Javadoc 列表[(起始行, 内容)])。"""
    out = []
    docs = []  # (line_no_1based, content)
    i, n = 0, len(src)
    line_no = src.count("\n", 0, 0) + 1
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            # 行注释：跳过到行尾，避免注释内的引号干扰字符串解析
            j = src.find("\n", i)
            if j == -1:
                j = n
            out.append(src[i:j])
            line_no += src[i:j].count("\n")
            i = j
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            block = src[i : j + 2] if j != -1 else src[i:]
            is_javadoc = block.startswith("/**") and not block.startswith("/**/")
            if is_javadoc:
                docs.append((line_no, block))
            line_no += block.count("\n")
            out.append("\n" * block.count("\n"))
            if j == -1:
                break
            i = j + 2
        elif c == '"':
            # 处理 Java 15+ 文本块（三个连续双引号），整体跳过避免其中内容被误判
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                block = src[i : j + 3] if j != -1 else src[i:]
                out.append("\n" * block.count("\n"))
                line_no += block.count("\n")
                i = j + 3 if j != -1 else n
            else:
                quote = c
                out.append(c)
                i += 1
                while i < n:
                    ch = src[i]
                    out.append(ch)
                    if ch == "\\":
                        if i + 1 < n:
                            out.append(src[i + 1])
                            i += 2
                        continue
                    if ch == quote:
                        i += 1
                        break
                    i += 1
                line_no = src.count("\n", 0, i) + 1
        elif c == "'":
            quote = c
            out.append(c)
            i += 1
            while i < n:
                ch = src[i]
                out.append(ch)
                if ch == "\\":
                    if i + 1 < n:
                        out.append(src[i + 1])
                        i += 2
                    continue
                if ch == quote:
                    i += 1
                    break
                i += 1
            line_no = src.count("\n", 0, i) + 1
        else:
            out.append(c)
            if c == "\n":
                line_no += 1
            i += 1
    return "".join(out), docs


def find_doc_above(src_lines: list, line_no_1based: int, doc_spans=None) -> bool:
    """判断第 line_no 行上方是否有紧邻的 Javadoc/注释块。

    规则：从目标行上一行开始向上扫描，跳过空行、注解行（@ 开头）及
    注解参数续行（不含分号且以 `)`/`,`/`"` 结尾的延续行）；
    若在遇到第一个普通代码行之前遇到块注释结束符 `*/`，确认该行落在某个
    Javadoc/注释块区间内即视为已覆盖；行注释 `//` 也视为已覆盖。
    doc_spans: [(start_line, end_line), ...]（1-based，含结束行）
    """
    doc_spans = doc_spans or []
    idx = line_no_1based - 2  # 上一行（0-based）
    if idx < 0:
        return False
    for k in range(idx, max(idx - 40, -1), -1):
        line = src_lines[k].strip()
        if not line:
            continue  # 跳过空行
        if line.startswith("@"):
            continue  # 注解行
        if line.startswith("/**") or line.startswith("/*") or line.startswith("//"):
            return True
        if line.endswith("*/"):
            end_line = k + 1  # 1-based
            return any(s <= end_line <= e for s, e in doc_spans)
        # 注解参数续行（如 @EnableConfigurationProperties({ A.class, B.class }) 或
        # @RocketMQMessageListener(topic=..., consumerGroup=...) 的中间行）：
        # 不含分号且以 `)`/`,`/`"`/`.class` 结尾，或行内含 `=`（注解属性赋值），
        # 无法区分时继续向上找
        if ";" not in line and (
                line.endswith((")", ",", '"', ".class"))
                or ".class" in line
                or "=" in line
        ):
            continue
        return False  # 其他代码行，中断
    return False


# 控制流关键字，用于过滤被误判为方法的正则匹配
SKIP_CTRL_WORDS = {
    "if", "for", "while", "switch", "catch", "synchronized", "return",
    "new", "super", "this", "case", "do", "try", "finally", "assert", "yield",
}


def is_functional_interface_above(src_lines: list, line_no_1based: int = None, method_line: int = None) -> bool:
    """判断方法所在的上层接口是否标注了 @FunctionalInterface。

    规则：从方法声明行向上回溯，找到最近的 interface 声明（可能跨越嵌套类），
    检查其上方（含注解区）是否出现 @FunctionalInterface。
    """
    line = method_line or line_no_1based
    if not line:
        return False
    idx = line - 2  # 0-based
    # 向上找最近的 interface / class 关键字（限 200 行内）
    for k in range(idx, max(idx - 200, -1), -1):
        l = src_lines[k].strip()
        if l.startswith("interface ") or " interface " in l or l.startswith("enum ") or l.startswith("class "):
            # 找到类型声明，从其上一行开始向上（跳过注解）找 @FunctionalInterface
            for kk in range(k - 1, max(k - 12, -1), -1):
                t = src_lines[kk].strip()
                if t.startswith("@FunctionalInterface"):
                    return True
                if t.startswith("@"):
                    continue
                if not t or t.startswith("//"):
                    continue
                break  # 遇到代码/注释块即停止
            return False
    return False


def has_override_above(src_lines: list, line_no_1based: int) -> bool:
    """判断方法声明行上方是否有 @Override 注解（跳过空行与其他注解）。"""
    idx = line_no_1based - 2
    if idx < 0:
        return False
    for k in range(idx, max(idx - 8, -1), -1):
        line = src_lines[k].strip()
        if not line:
            continue
        if line.startswith("@Override"):
            return True
        if line.startswith("@"):
            continue
        if line.endswith("*/") or line.startswith("//") or line.startswith("/*"):
            return False  # 已越过注释块，不再有 @Override
        return False  # 普通代码行
    return False


def is_simple_accessor(name: str, m: "re.Match", stripped: str) -> bool:
    """判断是否为简单 getter/setter（规范 1.2 豁免项）。

    规则：方法名以 get/set/is 开头，且方法体仅包含单个 return 字段 或 字段赋值语句。
    """
    if not (name.startswith("get") or name.startswith("set") or name.startswith("is")):
        return False
    # 正则已消费方法声明与 `{`，m.end() 即方法体起点
    body_start = m.end()
    body_end = stripped.find("}", body_start, body_start + 200)
    if body_end == -1 or body_end - body_start > 150:
        return False
    body = stripped[body_start + 1 : body_end].strip()
    if not body:
        return False
    if ";" not in body or "(" in body or ")" in body:
        return False
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    if len(stmts) != 1:
        return False
    stmt = stmts[0]
    if name.startswith("get") or name.startswith("is"):
        return stmt.startswith("return ")
    return bool(re.match(r"^(?:this\.)?\w+\s*=", stmt))


def analyze_java(path: str):
    """返回 dict: class_total, class_doc, method_pub_total, method_pub_doc, misplace, no_doc_classes, no_doc_methods"""
    with io.open(path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
    lines = src.split("\n")
    stripped, docs = strip_block_comments(src)
    stripped_lines = stripped.split("\n")

    doc_lines = {d[0] for d in docs}  # Javadoc 起始行（1-based）
    doc_spans = []
    for start, content in docs:
        end = start + content.count("\n")
        doc_spans.append((start, end))

    def covered(line_no: int) -> bool:
        return any(s <= line_no <= e for s, e in doc_spans)

    # ---- 类级 ----
    class_total = class_doc = 0
    no_doc_classes = []
    for m in JAVA_CLASS_RE.finditer(stripped):
        cls_line = stripped[: m.start(1)].count("\n") + 1
        class_total += 1
        if covered(cls_line) or find_doc_above(lines, cls_line, doc_spans):
            class_doc += 1
        else:
            no_doc_classes.append(m.group(1))

    # ---- 方法级 ----
    method_pub_total = method_pub_doc = 0
    no_doc_methods = []
    misplace = []  # (行号, 方法名) Javadoc 出现在注解之后
    enclosing_types = [m.group(1) for m in JAVA_CLASS_RE.finditer(stripped)]
    for m in JAVA_METHOD_RE.finditer(stripped):
        name = m.group("name")
        if name in SKIP_METHODS or name.startswith("lambda$") or name in SKIP_CTRL_WORDS:
            continue
        if name in ("throw", "return", "new"):  # throw xxx; 等语句误匹配
            continue
        if name in enclosing_types:
            continue  # 构造器（方法名=类名），规范豁免
        line_no = stripped[: m.start("name")].count("\n") + 1
        # 函数式接口抽象方法豁免（仅当该方法位于 @FunctionalInterface 接口内）
        if name in FUNCTIONAL_IFACE_METHODS and is_functional_interface_above(lines, method_line=line_no):
            continue
        # 过滤方法体内部的调用（如 return xxx(...)），而非声明
        prefix = stripped[max(0, m.start("name") - 40) : m.start("name")]
        if re.search(r"\b(?:return|this|super|new|case|throw|assert)\b\s*$", prefix):
            continue
        if prefix.rstrip().endswith((".", "=", "(", "[", ",", "?", ":")):
            continue
        # 方法声明与调用的区分：正则已把 `{`/`;`/`throws` 消费进匹配，
        # m.end() 之后若是 `,`、`;`、`)` 等则为调用，跳过。
        tail = stripped[m.end() : m.end() + 20]
        if re.match(r"^\s*(;|,|\))", tail):
            continue
        # 判断可见性
        head = stripped[m.start() - 200 : m.start()]
        is_public = bool(re.search(r"\bpublic\b", head.split(";")[-1][-200:])) or bool(
            re.search(r"\bpublic\b", stripped[m.start() : m.end()])
        )
        is_protected = bool(re.search(r"\bprotected\b", stripped[m.start() : m.end()]))
        if not (is_public or is_protected):
            continue
        # 豁免：简单 getter/setter（方法体仅单行 return 字段 / 赋值字段），规范 1.2 豁免
        if is_simple_accessor(name, m, stripped):
            continue  # 简单 getter/setter 不计入分母（豁免项）
        # 豁免：@Override 方法（父类/接口已有 Javadoc 时可省略）
        if has_override_above(lines, line_no):
            continue  # @Override 方法不计入分母（豁免项）
        method_pub_total += 1
        if covered(line_no) or find_doc_above(lines, line_no, doc_spans):
            method_pub_doc += 1
        else:
            no_doc_methods.append((line_no, name))
        # 错位检测：Javadoc 起始行的紧邻上方（跳过空行）是注解行
        # （如 @Override 在 Javadoc 之前），说明注释写在注解之后，工具不采集。
        for dl in sorted(doc_lines):
            if dl > line_no:
                break
            if line_no - 8 <= dl < line_no:
                # 检查 Javadoc 起始行 dl 的上方最近非空行是否为注解
                k = dl - 2  # 0-based 上一行
                while k >= 0 and not lines[k].strip():
                    k -= 1
                if k >= 0 and lines[k].strip().startswith("@"):
                    misplace.append((dl, name, line_no))

    return {
        "class_total": class_total,
        "class_doc": class_doc,
        "method_total": method_pub_total,
        "method_doc": method_pub_doc,
        "no_doc_classes": no_doc_classes,
        "no_doc_methods": no_doc_methods,
        "misplace": misplace,
    }


# ---------- TS / Vue 分析 ----------

TS_EXPORT_RE = re.compile(
    r"(?m)^\s*export\s+(?:declare\s+)?(?:async\s+)?(?:function|const|let|var|class|interface|type|enum|abstract\s+class)\s+(\w+)"
)
TS_EXPORT_ARROW_RE = re.compile(
    r"(?m)^\s*export\s+(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>"
)


def analyze_ts(path: str):
    with io.open(path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
    lines = src.split("\n")
    header_ok = bool(re.match(r"\s*/\*\*", src)) or bool(re.match(r"\s*<!--", src))

    doc_line_set = set()
    for m in re.finditer(r"/\*\*", src):
        start = src[: m.start()].count("\n") + 1
        doc_line_set.add(start)

    def covered(line_no):
        return line_no in doc_line_set

    exports = []
    no_doc_exports = []
    for m in TS_EXPORT_RE.finditer(src):
        line_no = src[: m.start()].count("\n") + 1
        exports.append((line_no, m.group(1)))
    for m in TS_EXPORT_ARROW_RE.finditer(src):
        line_no = src[: m.start()].count("\n") + 1
        if (line_no, m.group(1)) not in exports:
            exports.append((line_no, m.group(1)))

    for line_no, name in exports:
        if not covered(line_no):
            found = False
            for k in range(line_no - 2, max(line_no - 8, -1), -1):
                t = lines[k].strip()
                if not t:
                    break
                if t.startswith("/**"):
                    found = True
                    break
                if t.startswith("@") or t.startswith("//"):
                    continue
                break
            if not found:
                no_doc_exports.append((line_no, name))
    return {
        "export_total": len(exports),
        "export_doc": len(exports) - len(no_doc_exports),
        "no_doc_exports": no_doc_exports,
        "header_ok": header_ok,
    }


def analyze_vue(path: str):
    with io.open(path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
    header_ok = bool(re.match(r"\s*<!--", src))
    return {"header_ok": header_ok}


# ---------- 主流程 ----------

def module_name(path: str) -> str:
    rel = os.path.relpath(path, ROOT)
    parts = rel.split(os.sep)
    if parts[0] == "ydsz-backend":
        if len(parts) > 2 and parts[1] == "ydsz-common":
            return parts[2]  # ydsz-common 下的子模块单独统计
        return parts[1] if len(parts) > 1 else "ydsz-backend"
    if parts[0] == "ydsz-frontend":
        if len(parts) > 1:
            return os.path.join("frontend", parts[1])
    return parts[0]


def main():
    args = sys.argv[1:]
    keyword = None
    todo_only = False
    if "--todo" in args:
        todo_only = True
        args.remove("--todo")
    if args:
        keyword = args[0]

    summary = defaultdict(lambda: {
        "java": {"cls": [0, 0], "mtd": [0, 0], "mis": 0},
        "ts": {"exp": [0, 0], "hd": [0, 0]},
        "vue": {"hd": [0, 0]},
        "no_doc_classes": [], "no_doc_methods": [], "no_doc_exports": [], "no_header": [],
        "misplace": [],
    })

    java_files = walk(BACKEND, (".java",))
    ts_files = walk(FRONTEND, (".ts", ".tsx", ".mts", ".cts", ".js", ".mjs")) + walk(
        os.path.join(ROOT, "main"), (".ts", ".tsx", ".mts", ".cts", ".js", ".mjs")
    )
    vue_files = walk(FRONTEND, (".vue",)) + walk(os.path.join(ROOT, "main"), (".vue",))

    all_ts = sorted(set(ts_files))
    all_vue = sorted(set(vue_files))

    for p in java_files:
        mod = module_name(p)
        if keyword and keyword not in mod:
            continue
        r = analyze_java(p)
        s = summary[mod]
        s["java"]["cls"][0] += r["class_total"]
        s["java"]["cls"][1] += r["class_doc"]
        s["java"]["mtd"][0] += r["method_total"]
        s["java"]["mtd"][1] += r["method_doc"]
        s["java"]["mis"] += len(r["misplace"])
        s["misplace"].extend((os.path.relpath(p, ROOT), *x) for x in r["misplace"])
        s["no_doc_classes"].extend((os.path.relpath(p, ROOT), c) for c in r["no_doc_classes"])
        s["no_doc_methods"].extend((os.path.relpath(p, ROOT), ln, n) for ln, n in r["no_doc_methods"])

    for p in all_ts:
        mod = module_name(p)
        if keyword and keyword not in mod:
            continue
        r = analyze_ts(p)
        s = summary[mod]
        s["ts"]["exp"][0] += r["export_total"]
        s["ts"]["exp"][1] += r["export_doc"]
        s["ts"]["hd"][0] += 1
        s["ts"]["hd"][1] += 1 if r["header_ok"] else 0
        s["no_doc_exports"].extend((os.path.relpath(p, ROOT), ln, n) for ln, n in r["no_doc_exports"])
        if not r["header_ok"]:
            s["no_header"].append(os.path.relpath(p, ROOT))

    for p in all_vue:
        mod = module_name(p)
        if keyword and keyword not in mod:
            continue
        r = analyze_vue(p)
        s = summary[mod]
        s["vue"]["hd"][0] += 1
        s["vue"]["hd"][1] += 1 if r["header_ok"] else 0
        if not r["header_ok"]:
            s["no_header"].append(os.path.relpath(p, ROOT))

    print("=" * 100)
    print("注释覆盖率扫描报告（对照 docs/CODE_COMMENT_STANDARD.md）")
    print("=" * 100)
    header = f"{'模块':<30}{'Java类':>14}{'Java方法':>14}{'错位':>8}{'TS导出':>14}{'TS文件头':>14}{'Vue文件头':>14}"
    print(header)
    print("-" * 100)

    tot = {"jc": 0, "jd": 0, "mc": 0, "md": 0, "mis": 0, "te": 0, "td": 0, "th": 0, "thd": 0, "vh": 0, "vhd": 0}
    for mod in sorted(summary.keys()):
        s = summary[mod]
        jc, jd = s["java"]["cls"]
        mc, md = s["java"]["mtd"]
        te, td = s["ts"]["exp"]
        th, thd = s["ts"]["hd"]
        vh, vhd = s["vue"]["hd"]
        jpct = f"{jd}/{jc}" if jc else "-"
        mpct = f"{md}/{mc}" if mc else "-"
        tpct = f"{td}/{te}" if te else "-"
        thpct = f"{thd}/{th}" if th else "-"
        vhpct = f"{vhd}/{vh}" if vh else "-"
        print(f"{mod:<30}{jpct:>14}{mpct:>14}{s['java']['mis']:>8}{tpct:>14}{thpct:>14}{vhpct:>14}")
        tot["jc"] += jc; tot["jd"] += jd; tot["mc"] += mc; tot["md"] += md; tot["mis"] += s["java"]["mis"]
        tot["te"] += te; tot["td"] += td; tot["th"] += th; tot["thd"] += thd; tot["vh"] += vh; tot["vhd"] += vhd

    print("-" * 100)
    print(
        f"{'TOTAL':<30}{tot['jd']}/{tot['jc']:>14}{tot['md']}/{tot['mc']:>14}{tot['mis']:>8}"
        f"{tot['td']}/{tot['te']:>14}{tot['thd']}/{tot['th']:>14}{tot['vhd']}/{tot['vh']:>14}"
    )
    print()

    if todo_only:
        print("=" * 100)
        print("待处理明细")
        print("=" * 100)
        for mod in sorted(summary.keys()):
            s = summary[mod]
            lines = []
            for rel, c in s["no_doc_classes"]:
                lines.append(f"  [类] {rel} :: {c}")
            for rel, ln, n in s["no_doc_methods"]:
                lines.append(f"  [方法] {rel}:{ln} :: {n}")
            for rel, ln, n in s["no_doc_exports"]:
                lines.append(f"  [导出] {rel}:{ln} :: {n}")
            for rel in s["no_header"]:
                lines.append(f"  [文件头] {rel}")
            for rel, dl, n, ml in s["misplace"]:
                lines.append(f"  [错位] {rel}:{dl} (方法 {n} 声明于 {ml})")
            if lines:
                print(f"\n### {mod}  ({len(lines)} 项)")
                for l in lines:
                    print(l)
        return


if __name__ == "__main__":
    main()
