#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ydsz-pmis-common-cache 模块去品牌化脚本

将 5 个 Ydsz* 类重命名为 Local* 语义前缀：
  YdszCache              -> LocalCache
  YdszCacheManager       -> LocalCacheManager
  YdszCacheProperties    -> LocalCacheProperties
  YdszCacheAutoConfiguration -> LocalCacheAutoConfiguration
  SpringYdszCache        -> SpringLocalCache

同时清理 @author ydsz-pmis-team Javadoc 残留。

注意：使用词边界匹配 YdszCache，避免误伤 redis 模块的 YdszCacheable/YdszCachePut/YdszCacheEvict。
"""

import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")
CACHE_MODULE = ROOT / "ydsz-pmis-backend" / "ydsz-pmis-common" / "ydsz-pmis-common-cache"

# 受影响的目录（cache 模块自身 + 全部外部引用方）
AFFECTED_DIRS = [
    CACHE_MODULE,
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-gateway",
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-common" / "ydsz-pmis-common-auth",
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-common" / "ydsz-pmis-common-safe",
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-common" / "ydsz-pmis-common-lock",
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-literule" / "ydsz-pmis-literule-server",
    ROOT / "ydsz-pmis-backend" / "ydsz-pmis-workflow" / "ydsz-pmis-workflow-server",
]

# 替换规则（按长度降序，避免部分替换）
# 注意：YdszCache 用词边界，避免误伤 YdszCacheable/YdszCachePut/YdszCacheEvict
REPLACEMENTS = [
    ("YdszCacheAutoConfiguration", "LocalCacheAutoConfiguration"),
    ("YdszCacheProperties", "LocalCacheProperties"),
    ("YdszCacheManager", "LocalCacheManager"),
    ("SpringYdszCache", "SpringLocalCache"),
    # YdszCache 词边界匹配（后面不能跟字母/数字/下划线）
    (re.compile(r"YdszCache(?![A-Za-z0-9_])"), "LocalCache"),
]

# @author ydsz-pmis-team 清理（删除整行）
AUTHOR_PATTERN = re.compile(r"^.*@author\s+ydsz-pmis-team.*\n", re.MULTILINE)

# 文件扩展名白名单
EXTENSIONS = {".java", ".md", ".xml", ".imports", ".json", ".yml", ".yaml", ".properties", ".sh"}

# 排除目录
EXCLUDE_DIRS = {"target", ".git", ".idea", "node_modules", "build"}


def should_process(path: pathlib.Path) -> bool:
    """判断文件是否需要处理"""
    if path.suffix not in EXTENSIONS:
        return False
    if any(part in EXCLUDE_DIRS for part in path.parts):
        return False
    return True


def collect_files() -> list:
    """收集所有受影响的文件"""
    files = []
    for d in AFFECTED_DIRS:
        if not d.exists():
            continue
        for f in d.rglob("*"):
            if f.is_file() and should_process(f):
                files.append(f)
    return files


def apply_replacements(content: str) -> tuple:
    """应用所有替换规则，返回 (新内容, 替换次数)"""
    count = 0
    for old, new in REPLACEMENTS:
        if isinstance(old, str):
            if old in content:
                occurrences = content.count(old)
                content = content.replace(old, new)
                count += occurrences
        else:
            new_content, n = old.subn(new, content)
            if n > 0:
                content = new_content
                count += n
    # 清理 @author ydsz-pmis-team
    new_content, n = AUTHOR_PATTERN.subn("", content)
    if n > 0:
        content = new_content
        count += n
    return content, count


def rename_files():
    """重命名 5 个 Ydsz*.java 文件"""
    rename_map = {
        "YdszCache.java": "LocalCache.java",
        "YdszCacheManager.java": "LocalCacheManager.java",
        "YdszCacheProperties.java": "LocalCacheProperties.java",
        "YdszCacheAutoConfiguration.java": "LocalCacheAutoConfiguration.java",
        "SpringYdszCache.java": "SpringLocalCache.java",
    }
    renamed = []
    for old_name, new_name in rename_map.items():
        for f in CACHE_MODULE.rglob(old_name):
            new_path = f.parent / new_name
            f.rename(new_path)
            renamed.append((str(f), str(new_path)))
            print(f"  rename: {old_name} -> {new_name}")
    return renamed


def main():
    print("=" * 60)
    print(" ydsz-pmis-common-cache 去品牌化")
    print("=" * 60)

    # 步骤 1：内容替换
    files = collect_files()
    print(f"\n[1/2] 扫描 {len(files)} 个文件进行内容替换...")
    modified = 0
    total_replacements = 0
    for f in files:
        try:
            content = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, PermissionError):
            continue
        new_content, count = apply_replacements(content)
        if count > 0:
            f.write_text(new_content, encoding="utf-8")
            modified += 1
            total_replacements += count
            print(f"  modified: {f.relative_to(ROOT)} ({count} 处)")
    print(f"\n  共修改 {modified} 个文件，{total_replacements} 处替换")

    # 步骤 2：文件重命名
    print(f"\n[2/2] 重命名 Ydsz*.java 文件...")
    renamed = rename_files()
    print(f"\n  共重命名 {len(renamed)} 个文件")

    print("\n" + "=" * 60)
    print(" 完成。请运行 mvn clean compile 验证。")
    print("=" * 60)


if __name__ == "__main__":
    main()
