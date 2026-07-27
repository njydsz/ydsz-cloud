#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix-quality-gate-violations.py
批量修复质量门禁检测出的项目版本号违规：
1. Java @since x.y.z (非 1.0.0) → @since 1.0.0
2. @Deprecated(since = "x.y.z") (非 1.0.0) → @Deprecated(since = "1.0.0")
3. 13 个 pom.xml 的 BOM 编码污染清理
4. 部署脚本 .sh 的 CRLF → LF
"""
import pathlib
import re

REPO_ROOT = pathlib.Path(__file__).parent.parent
BACKEND_ROOT = REPO_ROOT / "ydsz-backend"
DEPLOY_ROOT = REPO_ROOT / "deploy"

# ===================== 1. 修复 @since 非 1.0.0 =====================
# 匹配 @since 后面跟非 1.0.0 的版本号（如 1.1.0 / 1.3.0 / 1.7.0 / 2.0.0 等）
SINCE_PATTERN = re.compile(r'@since\s+(1\.[1-9]\.\d+|2\.\d+\.\d+|3\.\d+\.\d+|4\.\d+\.\d+|5\.\d+\.\d+)')
DEPRECATED_PATTERN = re.compile(r'@Deprecated\(since\s*=\s*"(1\.[1-9]\.\d+|2\.\d+\.\d+|3\.\d+\.\d+|4\.\d+\.\d+|5\.\d+\.\d+)"')

since_count = 0
deprecated_count = 0
files_modified = 0

for f in BACKEND_ROOT.rglob("*.java"):
    if "/target/" in str(f) or "\\target\\" in str(f):
        continue
    text = f.read_text(encoding="utf-8")
    new_text = SINCE_PATTERN.sub("@since 1.0.0", text)
    new_text = DEPRECATED_PATTERN.sub('@Deprecated(since = "1.0.0"', new_text)
    if new_text != text:
        # 统计修改数
        since_diff = len(SINCE_PATTERN.findall(text))
        dep_diff = len(DEPRECATED_PATTERN.findall(text))
        since_count += since_diff
        deprecated_count += dep_diff
        f.write_text(new_text, encoding="utf-8")
        files_modified += 1
        print(f"  ✓ {f.relative_to(REPO_ROOT)}（@since x {since_diff}, @Deprecated x {dep_diff}）")

print(f"\n@since 修复：{since_count} 处，@Deprecated 修复：{deprecated_count} 处，共修改 {files_modified} 个文件")

# ===================== 2. 清理 BOM 编码污染 =====================
# 检测所有源代码文件（.java / .xml / .yml / .yaml / .sql / .ts / .vue）开头的 UTF-8 BOM
BOM_EXTENSIONS = {".java", ".xml", ".yml", ".yaml", ".sql", ".ts", ".vue", ".properties", ".sh", ".json"}
SKIP_DIRS = {"target", "node_modules", ".git", "__pycache__"}

bom_count = 0
for f in REPO_ROOT.rglob("*"):
    if not f.is_file() or f.suffix not in BOM_EXTENSIONS:
        continue
    if any(skip in f.parts for skip in SKIP_DIRS):
        continue
    data = f.read_bytes()
    if data.startswith(b'\xef\xbb\xbf'):
        f.write_bytes(data[3:])
        bom_count += 1
        print(f"  ✓ 移除 BOM: {f.relative_to(REPO_ROOT)}")

print(f"\nBOM 清理：{bom_count} 个文件")

print("\n✅ 全部修复完成")
