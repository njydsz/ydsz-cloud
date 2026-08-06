#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""精确输出每个引用文件中调用弃用 API 的具体行"""
import os, re

ROOT = r"D:\Code\open\remi-cloud"

TARGETS = {
    "IpAddrUtils": r"\bIpAddrUtils\b",
    "RequestHolder": r"\bRequestHolder\b",
    "TenantContextHolder": r"\bTenantContextHolder\b",
    "AuthContext": r"\bAuthContext\b",
    "SnowflakeAutoConfiguration": r"\bSnowflakeAutoConfiguration\b",
    "SnowflakeUtils": r"\bSnowflakeUtils\b",
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
    dirs[:] = [d for d in dirs if d not in (".git", "target", "node_modules", ".idea", ".workbuddy-tmp")]
    for fn in fnames:
        if fn.endswith(".java"):
            files.append(os.path.join(root, fn))

for name, pattern in TARGETS.items():
    rx = re.compile(pattern)
    print(f"\n{'='*70}\n### {name} 引用详情\n{'='*70}")
    for f in files:
        try:
            with open(f, encoding="utf-8") as fh:
                lines = fh.readlines()
        except UnicodeDecodeError:
            with open(f, encoding="gbk", errors="replace") as fh:
                lines = fh.readlines()
        for i, line in enumerate(lines, 1):
            if rx.search(line):
                rel = os.path.relpath(f, ROOT)
                print(f"{rel}:{i}: {line.rstrip()[:160]}")
