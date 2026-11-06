"""修复代码体中的行内 FQN（IMPORT-001，P0）—— 扩展版。

处理两类模式：
  A) 普通类型/参数位置的 FQN，如 `java.io.File file`、`new java.util.IdentityHashMap<>()`
     → 改为简单名 + 顶部普通 import
  B) 静态成员访问，如 `java.time.ZoneId.systemDefault()`、`java.util.Objects.hash(...)`
     → 改为简单名 + 顶部 static import（`import static java.time.ZoneId.systemDefault;` 或
        `import static java.time.ZoneId.*;` 不适用，改为普通 import + 简单类名.方法）

对 B 类采用「普通 import + 简单类名」策略（如 `import java.time.ZoneId;` + `ZoneId.systemDefault()`），
比 static import 更清晰且不会与同名方法冲突。

保守策略：
  - 跳过注释行（`*`、`//`、`/*`）与含字符串字面量的行
  - 只处理白名单内的 FQN（避免误伤）
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(".")
BS = chr(92)

# 白名单 FQN → (简单类名, 需要 import 的全限定名)
WHITELIST = {
    "java.io.ByteArrayOutputStream": "ByteArrayOutputStream",
    "java.io.OutputStream": "OutputStream",
    "java.io.File": "File",
    "java.io.IOException": "IOException",
    "java.io.InputStream": "InputStream",
    "java.io.Reader": "Reader",
    "java.io.Writer": "Writer",
    "java.util.ArrayList": "ArrayList",
    "java.util.HashMap": "HashMap",
    "java.util.HashSet": "HashSet",
    "java.util.IdentityHashMap": "IdentityHashMap",
    "java.util.LinkedHashMap": "LinkedHashMap",
    "java.util.LinkedList": "LinkedList",
    "java.util.List": "List",
    "java.util.Map": "Map",
    "java.util.Set": "Set",
    "java.util.Optional": "Optional",
    "java.util.Properties": "Properties",
    "java.util.Date": "Date",
    "java.util.Collections": "Collections",
    "java.util.Collection": "Collection",
    "java.util.Objects": "Objects",
    "java.util.Base64": "Base64",
    "java.util.UUID": "UUID",
    "java.math.BigDecimal": "BigDecimal",
    "java.time.ZoneId": "ZoneId",
    "java.time.Instant": "Instant",
    "java.time.LocalDate": "LocalDate",
    "java.time.LocalDateTime": "LocalDateTime",
    "java.time.Duration": "Duration",
    "java.time.format.DateTimeFormatter": "DateTimeFormatter",
    "java.util.concurrent.ThreadPoolExecutor": "ThreadPoolExecutor",
    "java.util.concurrent.TimeUnit": "TimeUnit",
    "java.util.concurrent.ConcurrentHashMap": "ConcurrentHashMap",
    "java.lang.reflect.Field": "Field",
    "java.lang.invoke.VarHandle": "VarHandle",
    "java.lang.StringBuilder": "StringBuilder",
    "org.springframework.boot.context.properties.NestedConfigurationProperty": "NestedConfigurationProperty",
    "org.springframework.core.Ordered": "Ordered",
    "org.springframework.core.annotation.Order": "Order",
}

SKIP_PREFIXES = ("*", "//", "/*", '"', "@")

# 匹配白名单 FQN（最长优先）
FQN_PATTERN = re.compile(
    r"\b(" + "|".join(sorted((re.escape(k) for k in WHITELIST), key=len, reverse=True)) + r")\b"
)


def collect_targets() -> dict[Path, set[int]]:
    targets: dict[Path, set[int]] = defaultdict(set)
    for p in ROOT.rglob("target/checkstyle-result.xml"):
        if "backup_logs" in str(p):
            continue
        try:
            r = ET.parse(p).getroot()
        except Exception:
            continue
        for f in r.findall("file"):
            abspath = Path(f.get("name", ""))
            if not abspath.exists():
                continue
            for e in f.findall("error"):
                if "RegexpSinglelineJava" not in (e.get("source") or ""):
                    continue
                if "行内FQN" not in (e.get("message") or ""):
                    continue
                targets[abspath].add(int(e.get("line", 0)))
    return targets


def add_import(src: str, fqn: str) -> str:
    imp = f"import {fqn};"
    if imp in src:
        return src
    lines = src.split("\n")
    import_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not import_idx:
        return src
    pos = import_idx[-1] + 1
    for i in import_idx:
        cur = lines[i][len("import "):-1]
        if cur > fqn:
            pos = i
            break
    lines.insert(pos, imp)
    return "\n".join(lines)


targets = collect_targets()
changed_files = 0
changed_lines = 0
for abspath, line_nums in targets.items():
    src = abspath.read_text(encoding="utf-8")
    lines = src.split("\n")
    needed: set[str] = set()

    for idx in sorted(line_nums):
        if not (1 <= idx <= len(lines)):
            continue
        line = lines[idx - 1]
        stripped = line.lstrip()
        if any(stripped.startswith(p) for p in SKIP_PREFIXES):
            continue
        if '"' in line:
            continue
        before = line

        def repl(m):
            fqn = m.group(1)
            needed.add(fqn)
            return WHITELIST[fqn]

        line = FQN_PATTERN.sub(repl, line)
        if line != before:
            lines[idx - 1] = line
            changed_lines += 1

    if needed:
        src = "\n".join(lines)
        for fqn in sorted(needed):
            src = add_import(src, fqn)
        with open(abspath, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(src)
        changed_files += 1

print(f"files changed: {changed_files}, lines changed: {changed_lines}")
