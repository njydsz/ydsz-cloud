#!/usr/bin/env python3
"""数据库版本脚本安全检查门禁。

本目录没有 Flyway/Liquibase，脚本由人工按版本执行。该门禁不替代 DBA 评审，
只阻断确定会导致数据丢失或无法安全重入的增量脚本：

  E1 增量脚本（基线以后）禁止 DROP/TRUNCATE TABLE
  E2 增量脚本禁止无 WHERE 条件的 DELETE/UPDATE 批量变更
  E3 增量脚本禁止 DROP DATABASE/SCHEMA/EXTENSION/INDEX CONCURRENTLY
  E4 索引名必须为 ix_/idx_ 前缀，并保持统一前缀
  E5 每个版本文件必须有可解析的版本号，且版本号不得重复

用法：
  python3 data/scripts/check-sql-migrations.py
  python3 data/scripts/check-sql-migrations.py --root data/sqls/flyway
  python3 data/scripts/check-sql-migrations.py --self-test

退出码：0 通过；1 存在新增安全问题；2 前提不成立。
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = REPO_ROOT / "data" / "sqls"
BASELINE_VERSION = "V26.10.01"
BOOTSTRAP_DIR = "bootstrap"
INCREMENTAL_DIR = "flyway"
# 版本解析：
#   V26.11.06.sql                    → V26.11.06
#   V26.11.06_分区注释.sql            → V26.11.06（README 约定：单下划线 + 描述）
#   V26.11.06__index-optimization.sql → V26.11.06__index-optimization
#     （双下划线形式：同一版本号下的多个独立脚本。README 约定描述用单下划线，
#      故双下划线视为「同版本的不同脚本」，迁移工具以完整文件名为去重键，不算重复。）
VERSION_RE = re.compile(
    r"^V(\d{2}\.\d{2}\.\d{2})(__[A-Za-z0-9_.-]+)?(?:[_. -][A-Za-z0-9_一-龥.-]*)?\.sql$",
    re.IGNORECASE,
)
STATEMENT_SPLIT_RE = re.compile(r";(?=(?:[^']*'[^']*')*[^\n]*$|$)")
FORBIDDEN_INCREMENTAL_DDL = (
    r"\bDROP\s+TABLE\b",
    r"\bDROP\s+COLUMN\b",
    r"\bTRUNCATE\s+(?:TABLE\s+)?",
    r"\bDELETE\s+FROM\s+[^\s;]+\s*;",
    r"\bUPDATE\s+[^\s;]+\s+SET\b(?:(?!WHERE).)*;",
    r"\bDROP\s+DATABASE\b",
    r"\bDROP\s+SCHEMA\b",
    r"\bDROP\s+EXTENSION\b",
)
FORBIDDEN_BOOTSTRAP_DDL = (
    r"\bTRUNCATE\s+(?:TABLE\s+)?",
    r"\bDELETE\s+FROM\s+[^\s;]+\s*;",
    r"\bDROP\s+DATABASE\b",
    r"\bDROP\s+SCHEMA\b",
    r"\bDROP\s+EXTENSION\b",
    r"\bDROP\s+INDEX\s+CONCURRENTLY\b",
)
INDEX_CREATE_RE = re.compile(
    r'\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?'
    r'(?:IF\s+NOT\s+EXISTS\s+)?"?([A-Za-z0-9_]+)"?',
    re.IGNORECASE,
)


class GateError(RuntimeError):
    """门禁前提不成立。"""


def strip_sql_comments(sql: str) -> str:
    """移除行注释、块注释和字符串字面量，避免把注释中的示例误判为执行 SQL。"""
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.DOTALL)
    sql = re.sub(r"'(?:''|[^'])*'", "''", sql)
    return re.sub(r"--[^\n]*", "", sql)


def parse_version(path: Path) -> str | None:
    """解析版本键：V26.11.06 或 V26.11.06__index-optimization（双下划线后缀独立计）。"""
    match = VERSION_RE.match(path.name)
    if not match:
        return None
    base = f"V{match.group(1)}"
    return f"{base}{match.group(2)}" if match.group(2) else base


def is_baseline(version: str) -> bool:
    return version == BASELINE_VERSION


def check_index_replacement(cleaned: str, script_kind: str, name: str) -> list[str]:
    """允许在线索引替换，但禁止无替代索引的裸删除。"""
    drops = list(re.finditer(r"\bDROP\s+INDEX\s+CONCURRENTLY\s+IF\s+EXISTS\s+\"?([A-Za-z0-9_]+)\"?", cleaned, re.IGNORECASE))
    creates = list(re.finditer(
        r"\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+CONCURRENTLY\s+IF\s+NOT\s+EXISTS\s+\"?([A-Za-z0-9_]+)\"?",
        cleaned,
        re.IGNORECASE,
    ))
    findings: list[str] = []
    if not drops:
        return findings
    if len(creates) < len(drops):
        findings.append(
            f"{script_kind}/{name}: DROP INDEX CONCURRENTLY 数量超过新建索引数量，禁止索引空窗"
        )
        return findings
    for index, drop in enumerate(drops):
        create = creates[index]
        if create.start() > drop.start():
            findings.append(
                f"{script_kind}/{name}:{cleaned[: drop.start()].count(chr(10)) + 1}: "
                f"索引 {drop.group(1)} 必须先创建替代索引再删除"
            )
    return findings


def check_file(path: Path, script_kind: str) -> list[str]:
    findings: list[str] = []
    version = parse_version(path)
    if version is None:
        return [f"{path.name}: 文件名无法解析出 VYY.MM.DD 版本号"]

    text = path.read_text(encoding="utf-8", errors="replace")
    cleaned = strip_sql_comments(text)
    forbidden = FORBIDDEN_BOOTSTRAP_DDL if script_kind == "bootstrap" else FORBIDDEN_INCREMENTAL_DDL

    for pattern in forbidden:
        match = re.search(pattern, cleaned, re.IGNORECASE | re.MULTILINE | re.DOTALL)
        if match:
            line = cleaned[: match.start()].count("\n") + 1
            findings.append(
                f"{script_kind}/{path.name}:{line}: 禁止破坏性变更：{match.group(0)!r}"
            )

    findings.extend(check_index_replacement(cleaned, script_kind, path.name))

    for match in INDEX_CREATE_RE.finditer(cleaned):
        index_name = match.group(1)
        line = cleaned[: match.start()].count("\n") + 1
        if not re.match(r"^(ix|idx|uk)_", index_name, re.IGNORECASE):
            findings.append(
                f"{path.name}:{line}: 索引名 {index_name!r} 未使用 ix_/idx_ 前缀"
            )

    return findings


def collect(root: Path) -> tuple[list[str], dict[str, list[str]]]:
    if not root.exists():
        raise GateError(f"SQL 目录不存在：{root}")

    bootstrap_root = root / BOOTSTRAP_DIR
    incremental_root = root / INCREMENTAL_DIR
    if not bootstrap_root.exists():
        raise GateError(f"初始化脚本目录不存在：{bootstrap_root}")
    if not incremental_root.exists():
        raise GateError(f"增量脚本目录不存在：{incremental_root}")

    bootstrap_paths = sorted(bootstrap_root.glob("V*.sql"))
    incremental_paths = sorted(incremental_root.glob("V*.sql"))
    if not bootstrap_paths:
        raise GateError(f"初始化目录没有 V*.sql 脚本：{bootstrap_root}")
    if not incremental_paths:
        raise GateError(f"增量目录没有 V*.sql 脚本：{incremental_root}")

    findings: list[str] = []
    versions: dict[str, list[str]] = {}

    for path in bootstrap_paths:
        version = parse_version(path)
        if version:
            versions.setdefault(version, []).append(f"{BOOTSTRAP_DIR}/{path.name}")
        findings.extend(check_file(path, "bootstrap"))

    for path in incremental_paths:
        version = parse_version(path)
        if version:
            versions.setdefault(version, []).append(f"{INCREMENTAL_DIR}/{path.name}")
        findings.extend(check_file(path, "incremental"))

    if not any(is_baseline(parse_version(path) or "") for path in bootstrap_paths):
        raise GateError(f"未找到空库初始化基线 {BASELINE_VERSION}*.bootstrap.sql")

    for version, names in sorted(versions.items()):
        if len(names) > 1:
            findings.append(f"版本号重复：{version} -> {', '.join(names)}")

    bootstrap_order = [parse_version(path) for path in bootstrap_paths if parse_version(path)]
    incremental_order = [parse_version(path) for path in incremental_paths if parse_version(path)]
    if bootstrap_order != sorted(bootstrap_order):
        findings.append("初始化脚本未按版本号升序排列")
    if incremental_order != sorted(incremental_order):
        findings.append("增量脚本未按版本号升序排列")

    return findings, versions


def self_test() -> int:
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        if not ok:
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / BOOTSTRAP_DIR).mkdir()
        (root / INCREMENTAL_DIR).mkdir()
        (root / BOOTSTRAP_DIR / f"{BASELINE_VERSION}.bootstrap.sql").write_text(
            "DROP TABLE IF EXISTS demo CASCADE;", encoding="utf-8"
        )
        (root / INCREMENTAL_DIR / "V26.10.02.sql").write_text(
            "CREATE INDEX bad_name ON demo(id);", encoding="utf-8"
        )
        (root / INCREMENTAL_DIR / "V26.10.03.sql").write_text(
            "TRUNCATE TABLE demo;", encoding="utf-8"
        )
        (root / INCREMENTAL_DIR / "V26.10.04.sql").write_text(
            "DELETE FROM demo;", encoding="utf-8"
        )
        (root / INCREMENTAL_DIR / "V26.10.05.sql").write_text(
            "UPDATE demo SET name = 'x';", encoding="utf-8"
        )
        (root / INCREMENTAL_DIR / "V26.10.06.sql").write_text(
            "CREATE INDEX idx_demo_id ON demo(id);\n"
            "COMMENT ON COLUMN demo.name IS '可执行 ALTER TABLE DROP COLUMN name';\n",
            encoding="utf-8",
        )
        findings, _ = collect(root)
        joined = "\n".join(findings)
        case("基线允许 DROP", BOOTSTRAP_DIR + "/" + BASELINE_VERSION + ".bootstrap.sql" not in joined)
        case("增量 TRUNCATE 被拦截", "TRUNCATE" in joined)
        case("无条件 DELETE 被拦截", "DELETE" in joined)
        case("无条件 UPDATE 被拦截", "UPDATE" in joined)
        case("非规范索引名被拦截", "bad_name" in joined)
        case("注释中的 DDL 示例不报错", f"{INCREMENTAL_DIR}/V26.10.06.sql" not in joined)

    empty_root = Path(tempfile.mkdtemp())
    case("空目录 fail-closed", _raises(lambda: collect(empty_root)))

    print(f"自检结果：{'全部通过' if not failures else f'{len(failures)} 条未通过'}")
    for name in failures:
        print(f"  ✗ {name}", file=sys.stderr)
    return 0 if not failures else 1


def _raises(func) -> bool:
    try:
        func()
    except GateError:
        return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="数据库版本脚本安全检查门禁")
    parser.add_argument("--root", default=str(DEFAULT_ROOT), help="SQL 脚本目录")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass

    if args.self_test:
        return self_test()

    try:
        findings, versions = collect(Path(args.root))
    except GateError as exc:
        print(f"❌ {exc}", file=sys.stderr)
        return 2

    print(f"实测：{len(versions)} 个版本 / {sum(len(v) for v in versions.values())} 个脚本")
    if findings:
        print("❌ 数据库脚本安全检查失败：", file=sys.stderr)
        for item in findings:
            print(f"   {item}", file=sys.stderr)
        return 1

    print("✅ 数据库脚本安全检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
