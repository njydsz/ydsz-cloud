#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描项目中弃用 API 的引用情况"""
import os, re, sys, json

ROOT = r"D:\Code\open\remi-cloud"

# 弃用 API -> 关键匹配模式
TARGETS = {
    "IpAddrUtils": r"\bIpAddrUtils\b",
    "SnowflakeUtils": r"\bSnowflakeUtils\b",
    "SnowflakeAutoConfiguration": r"\bSnowflakeAutoConfiguration\b",
    "RequestHolder": r"\bRequestHolder\b",
    "TenantContextHolder": r"\bTenantContextHolder\b",
    "AuthContext": r"\bAuthContext\b",
    "getJSONObject": r"\bgetJSONObject\b",
    "getJSONArray": r"\bgetJSONArray\b",
    "maskSensitive": r"\bmaskSensitive\b",
    "maskMobile": r"\bmaskMobile\b",
    "maskIdCard": r"\bmaskIdCard\b",
    "maskEmail": r"\bmaskEmail\b",
    "RemiJson.register": r"\bRemiJson\s*\.\s*register\b",
}

files = []
for root, dirs, fnames in os.walk(ROOT):
    # 跳过 .git / target / node_modules 等
    dirs[:] = [d for d in dirs if d not in (".git", "target", "node_modules", ".idea", ".workbuddy-tmp")]
    for fn in fnames:
        if fn.endswith(".java"):
            files.append(os.path.join(root, fn))

print(f"共扫描 {len(files)} 个 Java 文件\n")

for name, pattern in TARGETS.items():
    rx = re.compile(pattern)
    hits = []
    for f in files:
        try:
            with open(f, encoding="utf-8") as fh:
                content = fh.read()
        except UnicodeDecodeError:
            with open(f, encoding="gbk", errors="replace") as fh:
                content = fh.read()
        if rx.search(content):
            rel = os.path.relpath(f, ROOT)
            count = len(rx.findall(content))
            hits.append((rel, count))
    print(f"=== {name} ({pattern}) : {len(hits)} 个文件 ===")
    for rel, cnt in sorted(hits):
        print(f"    {cnt:3d}x  {rel}")
    print()
