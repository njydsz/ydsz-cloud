#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查 PgSearchEngine.java Line 322 的字符串。"""
import pathlib

f = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-search\src\main\java\com\njydsz\common\search\engine\pg\PgSearchEngine.java")
lines = f.read_text(encoding="utf-8").split('\n')

# 检查 Line 322
line322 = lines[321]  # 0-indexed
print(f"Line 322 length: {len(line322)}")
print(f"Line 322 content (first 200 chars): {line322[:200]}")
print(f"Line 322 content (last 50 chars): {line322[-50:]}")

# 检查双引号数量
quote_count = 0
i = 0
in_str = False
escaped = False
positions = []
for idx, c in enumerate(line322):
    if escaped:
        escaped = False
        continue
    if c == '\\' and in_str:
        escaped = True
        continue
    if c == '"':
        quote_count += 1
        positions.append(idx)
        in_str = not in_str

print(f"\n双引号数量: {quote_count} (应为偶数)")
print(f"双引号位置: {positions[:10]}...")

# 检查字符串是否平衡
if quote_count % 2 != 0:
    print("警告: 双引号数量为奇数，字符串未闭合!")
else:
    print("双引号平衡")

# 检查 Line 255-276 的 text block
print("\n--- Line 255 (text block start) ---")
print(repr(lines[254][:60]))
print("--- Line 276 (text block end) ---")
print(repr(lines[275][:60]))

# 用 javac 检查 Java 版本
import subprocess
result = subprocess.run(['javac', '-version'], capture_output=True, text=True)
print(f"\njavac version: {result.stderr.strip()}")

# 检查 pom.xml 的 Java 版本
pom = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\pom.xml").read_text(encoding="utf-8")
import re
maven_compiler = re.search(r'<maven\.compiler\.(?:release|source)>(\d+)</maven\.compiler\.(?:release|source)>', pom)
java_version = re.search(r'<java\.version>([\d.]+)</java\.version>', pom)
if maven_compiler:
    print(f"Maven compiler release: {maven_compiler.group(1)}")
if java_version:
    print(f"Java version: {java_version.group(1)}")
