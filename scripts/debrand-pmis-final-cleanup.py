#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
debrand-pmis-final-cleanup.py
=============================
ydsz-pmis 项目 pmis 品牌标识最终残留扫描与清理脚本。

背景：
  项目品牌标识是 ydsz（不是 pmis）。已完成去 pmis 化重构，本脚本对最终残留进行扫描和清理。

排除项（合法的 pmis 残留，保留不动）：
  - .git / target / node_modules / .idea / dist / .run-logs 目录
  - scripts/ 目录下所有 .py 文件（历史迁移脚本）
  - scripts/brand-residue-report.json（历史报告）
  - deploy/scripts/check-brand-consistency.sh（品牌检测脚本，pmis 是检测目标）
  - 根目录路径 d:\\Code\\ydsz\\ydsz-pmis（git 仓库根目录，保留）
  - GitHub URL github.com/njydsz/ydsz-pmis（实际仓库地址，保留）
  - .trae/rules 中的 file:///d:/Code/ydsz/ydsz-pmis/ 文件系统路径引用（保留）

使用方法：
  python scripts/debrand-pmis-final-cleanup.py          # 执行替换
  python scripts/debrand-pmis-final-cleanup.py --dry-run # 仅扫描不替换
"""

import pathlib
import sys

REPO_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")

# ---------- 排除目录名（出现在路径任一段中即跳过） ----------
# 注意：.github 不排除，CI 配置文件需要清理 pmis 残留
SKIP_DIRS = {
    ".git", "target", "node_modules", "__pycache__",
    ".idea", "dist", ".run-logs",
}

# ---------- 排除文件（精确路径，相对仓库根） ----------
SKIP_FILES = {
    "deploy/scripts/check-brand-consistency.sh",
    "scripts/brand-residue-report.json",
}

# ---------- 排除目录前缀（整个目录跳过） ----------
SKIP_DIR_PREFIXES = [
    "scripts",
]

# ---------- 有序替换规则（顺序敏感，前面的先执行） ----------
REPLACEMENTS = [
    # === 1. Java 包路径（最具体的先替换） ===
    ("com.njydsz.pmis.", "com.njydsz."),
    ("com.njydsz.pmis:", "com.njydsz:"),
    ("com.njydsz.pmis", "com.njydsz"),

    # === 2. 模块名 / 目录名 / 服务名（有后缀 dash 的先替换） ===
    ("ydsz-pmis-", "ydsz-"),

    # === 2b. Docker 镜像名（ydsz-pmis/ 后跟模块名，非文件系统路径） ===
    #  不使用全局 ydsz-pmis/ → ydsz/，避免破坏 .trae/rules 中的文件系统路径
    ("ydsz-pmis/gateway", "ydsz/gateway"),
    ("ydsz-pmis/frontend", "ydsz/frontend"),
    ("ydsz-pmis/system", "ydsz/system"),
    ("ydsz-pmis/userinfo", "ydsz/userinfo"),
    ("ydsz-pmis/project", "ydsz/project"),
    ("ydsz-pmis/cronjob", "ydsz/cronjob"),
    ("ydsz-pmis/workflow", "ydsz/workflow"),
    ("ydsz-pmis/agent", "ydsz/agent"),
    ("ydsz-pmis/message", "ydsz/message"),
    ("ydsz-pmis/nextwiki", "ydsz/nextwiki"),
    ("ydsz-pmis/${", "ydsz/${"),

    # === 2c. "ydsz-pmis " 带尾随空格（如 "在 ydsz-pmis 项目中"） ===
    ("ydsz-pmis ", "ydsz "),

    # === 3. 数据库名 ===
    ("POSTGRES_DB=ydsz-pmis", "POSTGRES_DB=ydsz"),
    ("DB_NAME=ydsz-pmis", "DB_NAME=ydsz"),
    ("ydsz_pmis", "ydsz"),

    # === 4. 表前缀 ===
    ("pmis_flow_", "ydsz_flow_"),

    # === 5. 域名示例（先于通用 pmis. 替换，避免误改） ===
    ("admin.pmis.ydsz.cn", "admin.ydsz.cn"),
    ("pmis.ydsz.cn", "ydsz.cn"),

    # === 6. 通用配置键 pmis. → ydsz.（域名已在上面处理） ===
    ("pmis.", "ydsz."),

    # === 7. YAML 配置键 pmis: → ydsz:（在 pmis:pmis 之后） ===
    ("pmis:pmis", "ydsz:ydsz"),
    ("pmis:", "ydsz:"),

    # === 8. 环境变量名 ===
    ("PMIS_WEBHOOK", "YDSZ_WEBHOOK"),
    ("LOG_PMIS_LEVEL", "LOG_YDSZ_LEVEL"),

    # === 9. 容器/网络/资源名 ===
    ("pmis-postgres", "ydsz-postgres"),
    ("pmis-redis", "ydsz-redis"),
    ("pmis-nacos", "ydsz-nacos"),
    ("pmis-minio", "ydsz-minio"),
    ("pmis-net", "ydsz-net"),
    ("pmis-dev", "ydsz-dev"),
    ("pmis-tls", "ydsz-tls"),
    ("pmis123", "ydsz123"),

    # === 10. 服务/应用名 ===
    ("pmis-executor", "ydsz-executor"),
    ("pmis-producer-group", "ydsz-producer-group"),

    # === 11. 环境变量值（=pmis 后换行，NACOS_NAMESPACE / MINIO_BUCKET） ===
    ("=pmis\n", "=ydsz\n"),
    ("=pmis\r\n", "=ydsz\r\n"),

    # === 12. SQL 字符串字面量 ===
    ("'pmis'", "'ydsz'"),

    # === 13. SQL 注释 ===
    ("init pmis ", "init ydsz "),

    # === 14. 日志路径 ===
    ("/var/log/pmis/", "/var/log/ydsz/"),

    # === 15. 产品名 / 标题 / 描述（中文） ===
    ("YDSZ PMIS", "YDSZ"),
    ("南京云顶 PMIS", "南京云顶 YDSZ"),
    ("Nanjing Yunding PMIS", "Nanjing Yunding YDSZ"),
    ("PMIS 项目", "YDSZ 项目"),

    # === 16. 通用 PMIS 大写带空格（如 "PMIS 工作流"、"PMIS 团队"等） ===
    #  在 PMIS 项目 之后执行，避免重复匹配
    ("PMIS ", "YDSZ "),

    # === 17. 字符串字面量 'PMIS' ===
    ("'PMIS'", "'YDSZ'"),

    # === 18. Helm keyword / install 命令 ===
    ("- pmis\n", "- ydsz\n"),
    ("- pmis\r\n", "- ydsz\r\n"),
    ("helm install pmis ", "helm install ydsz "),

    # === 19. K8s namespace ===
    ("namespace: pmis", "namespace: ydsz"),

    # === 20. 通用小写 pmis 带空格（兜底，如 "the pmis project"） ===
    ("pmis ", "ydsz "),

    # === 21. Dockerfile 非 root 用户名 ===
    ("-S pmis", "-S ydsz"),
    ("-G pmis", "-G ydsz"),

    # === 22. 通用前缀替换（兜底，捕获上面具体规则未覆盖的变体） ===
    #  放在所有具体 pmis-X 规则之后，安全：文件路径用 ydsz-pmis/ 不会被匹配
    ("pmis-", "ydsz-"),
    ("pmis_", "ydsz_"),
    ("PMIS_", "YDSZ_"),

    # === 23. 双引号字符串字面量 "pmis" ===
    ('"pmis"', '"ydsz"'),

    # === 24. 数组元素 [pmis] ===
    ("[pmis]", "[ydsz]"),

    # === 25. 括号包裹 (pmis) ===
    ("(pmis)", "(ydsz)"),

    # === 26. 默认值 :-pmis 和 :pmis（Nacos ${VAR:pmis} / ${VAR:-pmis}） ===
    (":-pmis", ":-ydsz"),
    (":pmis", ":ydsz"),

    # === 27. YAML 值 ": pmis" 后换行（如 issuer: pmis） ===
    (": pmis\n", ": ydsz\n"),
    (": pmis\r\n", ": ydsz\r\n"),

    # === 28. grep pmis（如 docker volume ls | grep pmis） ===
    ("grep pmis", "grep ydsz"),

    # === 29. 通用大写 PMIS → YDSZ（兜底，捕获 PMIS报表/PMIS助手/PMIS（自研）等） ===
    #  放在 PMIS_ / PMIS / 'PMIS' 等具体规则之后
    ("PMIS", "YDSZ"),

    # === 30. 通用 PascalCase Pmis → Ydsz（类名引用，如 PmisCacheConfig） ===
    ("Pmis", "Ydsz"),

    # === 31. 行尾 pmis（安全：文件路径 ydsz-pmis 后跟 / \ " 不会匹配） ===
    ("pmis\n", "ydsz\n"),
    ("pmis\r\n", "ydsz\r\n"),

    # === 32. 文件系统路径中的 pmis（/opt/pmis/、C:\pmis\、/var/log/pmis}） ===
    ("/opt/pmis/", "/opt/ydsz/"),
    ("C:\\pmis", "C:\\ydsz"),
    ("/var/log/pmis}", "/var/log/ydsz}"),

    # === 33. URL 路径段 /pmis/ → /ydsz/（安全：文件路径用 ydsz-pmis/ 不会匹配） ===
    ("/pmis/", "/ydsz/"),

    # === 34. 反引号包裹 `pmis`（Markdown 行内代码） ===
    ("`pmis`", "`ydsz`"),

    # === 35. pmis + 中文标点（逗号/句号/顿号等） ===
    ("pmis，", "ydsz，"),
    ("pmis。", "ydsz。"),
    ("pmis、", "ydsz、"),
    ("pmis；", "ydsz；"),

    # === 36. SQL GRANT 语句 TO pmis; ===
    ("TO pmis;", "TO ydsz;"),
    ("to pmis;", "to ydsz;"),

    # === 37. SQL LIKE 模式 'pmis\_%'（表名前缀检测） ===
    ("pmis\\_%", "ydsz\\_%"),

    # === 38. config 值 =pmis（=pmis, / =pmis（ 等） ===
    ("=pmis,", "=ydsz,"),
    ("=pmis（", "=ydsz（"),
    ("=pmis)", "=ydsz)"),

    # === 39. Javadoc {@code pmis} 和 ${VAR:pmis}（pmis 后跟 }） ===
    ("pmis}", "ydsz}"),

    # === 40. Grafana JSON 转义引号 \"pmis\" ===
    ('\\"pmis', '\\"ydsz'),

    # === 41. Windows 路径形式的 Java 包 com\njydsz\pmis ===
    ("com\\njydsz\\pmis", "com\\njydsz"),
    # === 41b. Unix 路径形式的 Java 包 com/njydsz/pmis ===
    ("com/njydsz/pmis", "com/njydsz"),

    # === 42. typo 修复 ===
    ("ydsy-pmis-team", "ydsz-team"),
]


def should_skip(path: pathlib.Path) -> bool:
    """判断文件是否应该跳过。"""
    rel = path.relative_to(REPO_ROOT)
    parts = rel.parts

    # 排除目录名
    for part in parts:
        if part in SKIP_DIRS:
            return True

    # 排除 scripts/ 目录前缀
    if parts and parts[0] in SKIP_DIR_PREFIXES:
        return True

    # 排除特定文件
    rel_str = rel.as_posix()
    if rel_str in SKIP_FILES:
        return True

    # 注意：不全局跳过 .py 文件。scripts/ 目录已由 SKIP_DIR_PREFIXES 跳过，
    # deploy/sql/modules/split_project_sql.py 等非 scripts 目录下的 .py 文件需要清理

    return False


def replace_in_file(path: pathlib.Path, dry_run: bool) -> tuple:
    """
    对单个文件应用替换规则。
    返回 (changed: bool, count: int)
    """
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, PermissionError):
        return (False, 0)

    original = content
    total_count = 0

    for old, new in REPLACEMENTS:
        if old in content:
            count = content.count(old)
            content = content.replace(old, new)
            total_count += count

    if content == original:
        return (False, 0)

    if not dry_run:
        path.write_text(content, encoding="utf-8")

    return (True, total_count)


def main():
    dry_run = "--dry-run" in sys.argv

    print("=" * 70)
    print(" ydsz-pmis pmis brand residue final cleanup")
    print(f" mode: {'DRY-RUN (no files modified)' if dry_run else 'REPLACE (modifying files)'}")
    print(f" repo: {REPO_ROOT}")
    print("=" * 70)
    print()

    changed_files = []
    total_replacements = 0

    for path in REPO_ROOT.rglob("*"):
        if not path.is_file():
            continue
        if should_skip(path):
            continue

        changed, count = replace_in_file(path, dry_run)
        if changed:
            rel = path.relative_to(REPO_ROOT)
            changed_files.append((rel, count))
            total_replacements += count

    print(f"{'WOULD CHANGE' if dry_run else 'CHANGED'} {len(changed_files)} files, "
          f"{total_replacements} replacements.")
    print()
    print("-" * 70)
    for rel, count in sorted(changed_files, key=lambda x: str(x[0])):
        print(f"  {str(rel):<80} {count}")
    print("-" * 70)
    print()

    if dry_run:
        print("(dry-run mode. Remove --dry-run to apply changes.)")
    else:
        print("Done. Run a second scan to verify no residue remains.")


if __name__ == "__main__":
    main()
