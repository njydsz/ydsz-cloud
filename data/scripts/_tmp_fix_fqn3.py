"""修复剩余的真实行内 FQN（第 3 批）。

只处理代码体（跳过含字符串字面量的行与注释行），白名单扩展至本轮发现的所有 FQN。
对 `@注解(FQN.class)` 形式同样处理。
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(".")

WHITELIST = {
    "java.util.Collections": "Collections",
    "java.util.HashSet": "HashSet",
    "java.util.ArrayList": "ArrayList",
    "java.util.Locale": "Locale",
    "java.util.Random": "Random",
    "java.util.function.Consumer": "Consumer",
    "java.util.function.Function": "Function",
    "java.util.zip.ZipInputStream": "ZipInputStream",
    "java.io.ByteArrayInputStream": "ByteArrayInputStream",
    "java.io.ByteArrayOutputStream": "ByteArrayOutputStream",
    "java.text.SimpleDateFormat": "SimpleDateFormat",
    "java.time.ZoneId": "ZoneId",
    "java.time.LocalDate": "LocalDate",
    "java.time.LocalDateTime": "LocalDateTime",
    "java.time.LocalTime": "LocalTime",
    "java.nio.charset.StandardCharsets": "StandardCharsets",
    "java.net.URI": "URI",
    "java.util.Date": "Date",
    "java.util.List": "List",
    "java.util.Map": "Map",
    "java.util.Set": "Set",
    "java.util.Objects": "Objects",
    "java.math.BigDecimal": "BigDecimal",
    "java.util.concurrent.Flow": "Flow",
    "java.util.concurrent.ThreadPoolExecutor": "ThreadPoolExecutor",
    "java.util.concurrent.TimeUnit": "TimeUnit",
    "java.lang.reflect.Field": "Field",
    "java.lang.invoke.VarHandle": "VarHandle",
    "org.springframework.data.domain.Page": "Page",
    "org.springframework.web.context.request.RequestContextHolder": "RequestContextHolder",
    "org.springframework.web.context.request.RequestAttributes": "RequestAttributes",
    "org.springframework.aop.support.AopUtils": "AopUtils",
    "org.springframework.beans.factory.BeanCreationException": "BeanCreationException",
    "org.springframework.data.redis.core.StringRedisTemplate": "StringRedisTemplate",
    "org.springframework.boot.context.properties.NestedConfigurationProperty": "NestedConfigurationProperty",
    "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration": "DataRedisAutoConfiguration",
    "org.springframework.core.Ordered": "Ordered",
    "org.springframework.core.annotation.Order": "Order",
    "org.springframework.core.io.Resource": "Resource",
    "org.springframework.web.bind.annotation.RestController": "RestController",
}

SKIP_PREFIXES = ("*", "//", "/*", '"', "@")

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
        # 无 import 块：插到 package 行之后
        for i, l in enumerate(lines):
            if l.startswith("package "):
                lines.insert(i + 1, "")
                lines.insert(i + 2, imp)
                return "\n".join(lines)
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
            continue  # 字符串字面量里的 FQN 是包名，不是类引用
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
