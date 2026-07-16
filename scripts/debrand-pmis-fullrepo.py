#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ydsz-pmis 全仓库去 pmis 品牌化批量替换脚本。

品牌定位：项目品牌是 ydsz（不是 pmis），移除所有 pmis 标识。
替换策略（混合）：
  1. com.njydsz.pmis  → com.njydsz       （包路径 / import / FQN 字符串）
  2. com/njydsz/pmis/ → com/njydsz/       （正斜杠路径，resources 配置）
  3. ydsz-pmis        → ydsz              （模块名 / 目录 / chart 名）
  4. pmis_            → ydsz_             （SQL 表前缀，仅 .sql/.java/.xml）
  5. pmis.            → ydsz.             （配置键，仅 .yml/.yaml/.properties/.java）
  6. pmis:            → ydsz:             （分布式锁 key / 权限码，仅 .java/.yml/.yaml）
  7. Pmis             → Ydsz              （类名前缀，词边界，仅 .java）

用法：python scripts/debrand-pmis-fullrepo.py [--dry-run]
"""

import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 排除目录（不递归进入）
EXCLUDE_DIRS = {
    ".git", "target", "node_modules", ".idea", ".vscode",
    ".codebuddy", ".trae", "dist", "build", ".gradle",
}

# 排除文件（不处理内容）
EXCLUDE_FILES = {
    "debrand-pmis-fullrepo.py",   # 本脚本自身
    "brand-residue-report.json",   # 品牌残留报告（数据文件）
    "pnpm-lock.yaml",
    "package-lock.json",
}

# 需要处理的文本文件扩展名
TEXT_EXTENSIONS = {
    ".java", ".xml", ".yml", ".yaml", ".properties",
    ".sql", ".json", ".md", ".sh", ".ps1", ".bat",
    ".ts", ".tsx", ".vue", ".js", ".jsx",
    ".tpl", ".txt", ".cfg", ".conf", ".config",
    ".gradle", ".kts", ".toml", ".ini",
    ".ftl", ".vm", ".html", ".css", ".scss",
}

# 统计
stats = {
    "files_scanned": 0,
    "files_modified": 0,
    "replacements": {
        "package": 0,      # com.njydsz.pmis → com.njydsz
        "path_slash": 0,  # com/njydsz/pmis/ → com/njydsz/
        "module": 0,      # ydsz-pmis → ydsz
        "table": 0,       # pmis_ → ydsz_
        "config": 0,      # pmis. → ydsz.
        "lockkey": 0,     # pmis: → ydsz:
        "classname": 0,   # Pmis → Ydsz
    },
}


def is_excluded(path: pathlib.Path) -> bool:
    """判断路径是否应被排除。"""
    parts = path.parts
    for exc in EXCLUDE_DIRS:
        if exc in parts:
            return True
    if path.name in EXCLUDE_FILES:
        return True
    # 排除 .pyc / 二进制
    if path.suffix in {".pyc", ".class", ".jar", ".war", ".zip", ".gz", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".pdf"}:
        return True
    return False


def apply_replacements(content: str, ext: str) -> tuple[str, dict]:
    """对文件内容应用替换规则，返回 (新内容, 替换计数)。"""
    counts = {k: 0 for k in stats["replacements"]}
    original = content

    # 规则 1: com.njydsz.pmis → com.njydsz （所有文本文件）
    n = content.count("com.njydsz.pmis")
    if n:
        content = content.replace("com.njydsz.pmis", "com.njydsz")
        counts["package"] = n

    # 规则 2: com/njydsz/pmis/ → com/njydsz/ （正斜杠路径）
    n = content.count("com/njydsz/pmis/")
    if n:
        content = content.replace("com/njydsz/pmis/", "com/njydsz/")
        counts["path_slash"] = n

    # 规则 3: ydsz-pmis → ydsz （模块名/目录/chart 名，所有文本文件）
    n = content.count("ydsz-pmis")
    if n:
        content = content.replace("ydsz-pmis", "ydsz")
        counts["module"] = n

    # 规则 4: pmis_ → ydsz_ （SQL 表前缀，仅 .sql/.java/.xml）
    if ext in {".sql", ".java", ".xml"}:
        n = content.count("pmis_")
        if n:
            content = content.replace("pmis_", "ydsz_")
            counts["table"] = n

    # 规则 5: pmis. → ydsz. （配置键，仅 .yml/.yaml/.properties/.java）
    if ext in {".yml", ".yaml", ".properties", ".java"}:
        n = content.count("pmis.")
        if n:
            content = content.replace("pmis.", "ydsz.")
            counts["config"] = n

    # 规则 6: pmis: → ydsz: （锁 key/权限码，仅 .java/.yml/.yaml）
    if ext in {".java", ".yml", ".yaml"}:
        n = content.count("pmis:")
        if n:
            content = content.replace("pmis:", "ydsz:")
            counts["lockkey"] = n

    # 规则 7: \bPmis → Ydsz （类名前缀，仅 .java）
    if ext == ".java":
        new_content, n = re.subn(r"\bPmis", "Ydsz", content)
        if n:
            content = new_content
            counts["classname"] = n

    return content, counts


def process_file(path: pathlib.Path, dry_run: bool) -> bool:
    """处理单个文件，返回是否被修改。"""
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return False

    ext = path.suffix.lower()
    new_content, counts = apply_replacements(content, ext)

    if new_content == content:
        return False

    if not dry_run:
        path.write_text(new_content, encoding="utf-8")

    # 更新统计
    for k, v in counts.items():
        stats["replacements"][k] += v

    return True


def main():
    dry_run = "--dry-run" in sys.argv

    print(f"=== 去 pmis 品牌化批量替换 ===")
    print(f"模式: {'DRY RUN（预览）' if dry_run else 'EXECUTE（执行）'}")
    print(f"根目录: {ROOT}")
    print()

    start = time.time()

    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if is_excluded(path):
            continue
        if path.suffix.lower() not in TEXT_EXTENSIONS:
            continue

        stats["files_scanned"] += 1
        modified = process_file(path, dry_run)
        if modified:
            stats["files_modified"] += 1
            rel = path.relative_to(ROOT)
            print(f"  [MODIFIED] {rel}")

    elapsed = time.time() - start

    print()
    print(f"=== 统计 ===")
    print(f"扫描文件数: {stats['files_scanned']}")
    print(f"修改文件数: {stats['files_modified']}")
    print(f"替换明细:")
    for k, v in stats["replacements"].items():
        print(f"  {k:15s}: {v}")
    total = sum(stats["replacements"].values())
    print(f"  {'总计':15s}: {total}")
    print(f"耗时: {elapsed:.1f}s")

    if dry_run:
        print()
        print("[DRY RUN] 未实际修改文件。去掉 --dry-run 执行实际替换。")


if __name__ == "__main__":
    main()
