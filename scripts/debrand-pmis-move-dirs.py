#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
去 pmis 品牌化：物理目录迁移 + 文件重命名脚本。
在 debrand-pmis-fullrepo.py（文本替换）之后运行。
"""

import os
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

EXCLUDE_DIRS = {".git", "target", "node_modules", ".idea", ".vscode", ".codebuddy", ".trae", "dist", "build"}

stats = {"dirs_renamed": 0, "dirs_collapsed": 0, "files_renamed": 0}


def is_excluded(path: pathlib.Path) -> bool:
    parts = path.parts
    for exc in EXCLUDE_DIRS:
        if exc in parts:
            return True
    return False


def rename_module_dirs():
    """重命名所有 ydsz-pmi-* 目录为 ydsz-*（从深到浅）。"""
    print("=== 阶段 1: 重命名模块目录 ydsz-pmis-* → ydsz-* ===")

    dirs_to_rename = []
    for root, dirs, _ in os.walk(ROOT):
        root_path = pathlib.Path(root)
        if is_excluded(root_path):
            continue
        for d in dirs:
            if d.startswith("ydsz-pmis-"):
                dirs_to_rename.append(pathlib.Path(root) / d)

    # 按路径深度降序（最深的先改，避免父路径失效）
    dirs_to_rename.sort(key=lambda p: len(p.parts), reverse=True)

    for old_path in dirs_to_rename:
        old_name = old_path.name
        new_name = "ydsz-" + old_name[len("ydsz-pmis-"):]
        new_path = old_path.parent / new_name
        if new_path.exists():
            print(f"  [SKIP] 目标已存在: {new_path}")
            continue
        old_path.rename(new_path)
        stats["dirs_renamed"] += 1
        print(f"  [RENAMED] {old_name} → {new_name}")

    print(f"  小计: {stats['dirs_renamed']} 个目录重命名")


def collapse_pmis_package():
    """折叠 com/njydsz/pmis/ 目录，将子目录上移到 com/njydsz/。"""
    print("\n=== 阶段 2: 折叠 com/njydsz/pmis/ → com/njydsz/ ===")

    pmis_dirs = []
    for root, dirs, _ in os.walk(ROOT):
        root_path = pathlib.Path(root)
        if is_excluded(root_path):
            continue
        if root_path.name == "pmis" and root_path.parent.name == "njydsz" and root_path.parent.parent.name == "com":
            pmis_dirs.append(root_path)

    # 按路径深度降序（最深的先处理）
    pmis_dirs.sort(key=lambda p: len(p.parts), reverse=True)

    for pmis_dir in pmis_dirs:
        njydsz_dir = pmis_dir.parent  # com/njydsz/
        if not pmis_dir.exists():
            continue

        # 将 pmis/ 下的所有子项移动到 njydsz/
        for item in pmis_dir.iterdir():
            target = njydsz_dir / item.name
            if target.exists():
                # 合并目录
                if item.is_dir():
                    for sub in item.rglob("*"):
                        if sub.is_file():
                            rel = sub.relative_to(item)
                            dst = target / rel
                            dst.parent.mkdir(parents=True, exist_ok=True)
                            shutil.move(str(sub), str(dst))
                    # 移动后清理空目录
                    shutil.rmtree(item, ignore_errors=True)
                else:
                    shutil.move(str(item), str(target))
            else:
                shutil.move(str(item), str(target))

        # 删除空的 pmis 目录
        try:
            pmis_dir.rmdir()
            stats["dirs_collapsed"] += 1
            print(f"  [COLLAPSED] {pmis_dir.relative_to(ROOT)}")
        except OSError:
            # 目录非空，可能有残留
            shutil.rmtree(pmis_dir, ignore_errors=True)
            stats["dirs_collapsed"] += 1
            print(f"  [COLLAPSED] {pmis_dir.relative_to(ROOT)} (forced)")

    print(f"  小计: {stats['dirs_collapsed']} 个 pmis 目录折叠")


def rename_class_files():
    """重命名 Pmis*.java → Ydsz*.java。"""
    print("\n=== 阶段 3: 重命名类文件 Pmis*.java → Ydsz*.java ===")

    files_to_rename = []
    for root, _, files in os.walk(ROOT):
        root_path = pathlib.Path(root)
        if is_excluded(root_path):
            continue
        for f in files:
            if f.startswith("Pmis") and f.endswith(".java"):
                files_to_rename.append(pathlib.Path(root) / f)

    for old_path in files_to_rename:
        old_name = old_path.name
        new_name = "Ydsz" + old_name[len("Pmis"):]
        new_path = old_path.parent / new_name
        if new_path.exists():
            print(f"  [SKIP] 目标已存在: {new_path}")
            continue
        old_path.rename(new_path)
        stats["files_renamed"] += 1
        print(f"  [RENAMED] {old_name} → {new_name}")

    print(f"  小计: {stats['files_renamed']} 个文件重命名")


def rename_helm_dir():
    """重命名 deploy/helm/ydsz-pmis/ → deploy/helm/ydsz/。"""
    print("\n=== 阶段 4: 重命名 helm 目录 ===")

    helm_old = ROOT / "deploy" / "helm" / "ydsz-pmis"
    helm_new = ROOT / "deploy" / "helm" / "ydsz"

    if helm_old.exists() and not helm_new.exists():
        helm_old.rename(helm_new)
        stats["dirs_renamed"] += 1
        print(f"  [RENAMED] deploy/helm/ydsz-pmis → deploy/helm/ydsz")
    else:
        print(f"  [SKIP] helm 目录不存在或目标已存在")


def rename_misc_files():
    """重命名其他含 pmis 的文件名。"""
    print("\n=== 阶段 5: 重命名其他含 pmis 的文件 ===")

    # 排除 debrand 脚本自身及计划文档（作为历史回退参考，保留原文件名）
    EXCLUDE_FILES = {
        "debrand-pmis-fullrepo.py",
        "debrand-pmis-move-dirs.py",
        "debrand-plan.md",
        "debrand-cache-module.py",
    }

    files_to_rename = []
    for root, _, files in os.walk(ROOT):
        root_path = pathlib.Path(root)
        if is_excluded(root_path):
            continue
        for f in files:
            lower = f.lower()
            if "pmis" in lower and f not in EXCLUDE_FILES:
                files_to_rename.append(pathlib.Path(root) / f)

    for old_path in files_to_rename:
        old_name = old_path.name
        new_name = old_name.replace("pmis", "ydsz").replace("Pmis", "Ydsz").replace("PMIS", "YDSZ")
        if new_name == old_name:
            continue
        new_path = old_path.parent / new_name
        if new_path.exists():
            print(f"  [SKIP] 目标已存在: {new_path.name}")
            continue
        old_path.rename(new_path)
        stats["files_renamed"] += 1
        print(f"  [RENAMED] {old_name} → {new_name}")

    print(f"  小计: {stats['files_renamed']} 个文件重命名")


def main():
    print(f"=== 去 pmis 品牌化：物理目录迁移 ===")
    print(f"根目录: {ROOT}\n")

    rename_module_dirs()
    collapse_pmis_package()
    rename_class_files()
    rename_helm_dir()
    rename_misc_files()

    print(f"\n=== 总结 ===")
    print(f"目录重命名: {stats['dirs_renamed']}")
    print(f"目录折叠: {stats['dirs_collapsed']}")
    print(f"文件重命名: {stats['files_renamed']}")


if __name__ == "__main__":
    main()
