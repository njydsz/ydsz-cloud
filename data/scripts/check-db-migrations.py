#!/usr/bin/env python3
"""数据库增量脚本危险变更门禁 —— 把「禁止在增量脚本里做破坏性变更」变成可执行断言。

背景（2026-11-03 事实校正）：
  仓库的 `data/sqls/flyway/V26.10.01.sql` 曾被当作「基线升级脚本」，实际它是 DBX 导出的
  **空库全量快照**，含 173 组 `DROP TABLE ... CASCADE`。在已有数据的库上执行会清空业务表。
  该文件已移入 `data/sqls/bootstrap/` 并加警告头；本脚本负责守住增量目录不再出现同类语句。

扫描范围：
  仅 `data/sqls/flyway/V*.sql`（增量目录）。`data/sqls/bootstrap/` 不参与扫描 —— 初始化脚本
  本就需要 DROP，且文件头已有显式警告。

判红规则：
  D1 `DROP TABLE`          —— 增量脚本不允许删表；需要删表先出备份脚本并人工评审
  D2 `DROP SCHEMA`         —— 同上
  D3 `DROP DATABASE`       —— 同上
  D4 无备份的 `DROP COLUMN` —— 需在同文件内提供备份列（见下方白名单说明）
  D5 `TRUNCATE`            —— 等价于无 WHERE 的全表删除
  D6 无 `WHERE` 的 `DELETE` —— 全表删除
  D7 `ALTER COLUMN ... TYPE` 无 `USING` —— 可能导致隐式转换丢数据

白名单（不判红）：
  - 被 `--` 或 `/* */` 注释掉的语句
  - `DROP TABLE IF EXISTS ... ;` 出现在以 `--` 开头的示例块中（整行注释）
  - `DROP COLUMN` 在同一文件内存在同名备份列（`xxx_bak` / `xxx_backup`）时降级为告警
  - 位于「临时表 / 影子表」命名（`_tmp` / `_temp` / `_old` / `_bak` / `_backup` 后缀）的 DROP TABLE

用法：
  python3 data/scripts/check-db-migrations.py            # 扫描增量脚本
  python3 data/scripts/check-db-migrations.py --all      # 连 bootstrap 一起扫（仅报告不判红）
  python3 data/scripts/check-db-migrations.py --self-test
退出码非 0 表示存在危险变更；脚本自身前提不成立时同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
INCREMENTAL_DIR = Path("data/sqls/flyway")
BOOTSTRAP_DIR = Path("data/sqls/bootstrap")

VERSION_RE = re.compile(r"^V\d{2}\.\d{2}\.\d{2}(?:_.*)?\.sql$", re.IGNORECASE)

# 临时表/影子表后缀，删除它们不视为破坏性变更
TEMP_SUFFIXES = ("_tmp", "_temp", "_old", "_bak", "_backup", "_staging", "_shadow")
BACKUP_SUFFIXES = ("_bak", "_backup", "_old", "_copy")


class GateError(RuntimeError):
    """门禁前提不成立（目录缺失 / 无法读取）。"""


def strip_comments(sql: str) -> tuple[str, list[tuple[int, str]]]:
    """去除 SQL 注释，返回 (清理后文本, [(行号, 原始行)] 供定位)。

    保留行号信息：把注释替换为等长空白，避免行号错位。
    同时处理 `--` 行注释与 `/* ... */` 块注释（含跨行）。
    """
    kept_lines: list[tuple[int, str]] = []
    # 用 split("\n") 而非 splitlines()：后者会丢掉末尾换行产生的空行，导致行号偏移
    for idx, line in enumerate(sql.split("\n"), start=1):
        kept_lines.append((idx, line))

    # 先剥块注释：用占位空白替换，保持字符数以便行号不回跳
    def _blank_block(match: re.Match[str]) -> str:
        return "".join("\n" if ch == "\n" else " " for ch in match.group(0))

    without_block = re.sub(r"/\*.*?\*/", _blank_block, sql, flags=re.DOTALL)

    out_lines: list[str] = []
    for line in without_block.split("\n"):
        # 行内 -- 注释：-- 之后全部置空
        out_lines.append(re.sub(r"--.*$", lambda m: " " * len(m.group(0)), line))
    return "\n".join(out_lines), kept_lines


def _is_safe_widening(new_type: str) -> bool:
    """判断目标类型是否属于「变宽/同族」的安全转换。

    项目中最常见的 ALTER COLUMN TYPE 是 ID 类字段统一为 varchar(36)（平台规范 DB-008），
    这类转换的目标类型固定，且全仓已大规模执行过，视为安全。

    其他目标类型（如 varchar → int、text → jsonb）需要人工确认，仍会告警。
    """
    normalized = new_type.strip().lower()
    normalized = re.sub(r"\s+", " ", normalized)
    # 去掉可能的 COLLATE / USING / NOT NULL / DEFAULT 后缀（已统一小写）
    normalized = re.split(r"\b(?:collate|using|not null|default)\b", normalized)[0].strip()
    # 字符串家族：varchar(n) / varchar / text / character varying(n)
    if re.fullmatch(r"(?:varchar|character\s+varying)(?:\(\d+\))?", normalized):
        return True
    if normalized == "text":
        return True
    # 整数扩宽：smallint → integer → bigint
    if normalized in {"smallint", "integer", "int", "int4", "bigint", "int8"}:
        return True
    return False


def _is_temp_table(name: str) -> bool:
    lowered = name.lower().strip('"')
    return any(lowered.endswith(suffix) for suffix in TEMP_SUFFIXES)


def _has_backup_column(text: str, table: str, column: str) -> bool:
    """同一文件内是否存在该列的备份列（xxx_bak / xxx_backup）。

    匹配时不要求列名带引号（PostgreSQL DDL 中列名常不加引号），
    同时排除「备份列自身被 DROP」造成的误判。
    """
    lowered = text.lower()
    col = column.lower().strip('"')
    for suffix in BACKUP_SUFFIXES:
        backup = f"{col}{suffix}"
        # 备份列被 ADD COLUMN / CREATE TABLE 定义即视为存在
        quoted_backup = re.escape(backup)
        if re.search(rf'add\s+column\s+"?{quoted_backup}"?\b', lowered):
            return True
        if re.search(
            rf'"?{quoted_backup}"?\s+(?:int|integer|bigint|smallint|text|varchar|character)',
            lowered,
        ):
            return True
    # 表级备份：xxx_bak 表存在
    table_lower = table.lower().strip('"')
    for suffix in BACKUP_SUFFIXES:
        if re.search(rf'create\s+table\s+"?(?:public\.)?"?{re.escape(table_lower)}{suffix}"?\b', lowered):
            return True
    return False


def scan_sql(rel_path: str, sql: str) -> tuple[list[str], list[str]]:
    """扫描单个 SQL 文件，返回 (错误, 告警)。"""
    cleaned, kept_lines = strip_comments(sql)
    # 合并成单行做跨行语句匹配，同时维护行号映射
    cleaned_flat = cleaned.replace("\n", " ")
    errors: list[str] = []
    warnings: list[str] = []

    def line_of(match_start: int) -> int:
        """把扁平文本偏移映射回原始行号。"""
        return cleaned[:match_start].count("\n") + 1

    # D1/D2/D3：DROP TABLE / SCHEMA / DATABASE
    for pattern, code, desc in (
        (r"\bDROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?\"?([\w.]+)\"?", "D1", "DROP TABLE"),
        (r"\bDROP\s+SCHEMA\s+(?:IF\s+EXISTS\s+)?\"?([\w.]+)\"?", "D2", "DROP SCHEMA"),
        (r"\bDROP\s+DATABASE\s+(?:IF\s+EXISTS\s+)?\"?([\w.]+)\"?", "D3", "DROP DATABASE"),
    ):
        for m in re.finditer(pattern, cleaned_flat, re.IGNORECASE):
            target = m.group(1)
            ln = line_of(m.start())
            if _is_temp_table(target):
                warnings.append(
                    f"W {rel_path}:{ln} {desc} 目标 `{target}` 属临时/影子表，降级为告警"
                )
                continue
            errors.append(f"{code} {rel_path}:{ln} {desc} `{target}` —— 增量脚本禁止删表/删库")

    # D4：DROP COLUMN（无备份列时判红）
    for m in re.finditer(
        r"\bALTER\s+TABLE\s+\"?(?:public\.)?\"?([\w]+)\"?\s+DROP\s+COLUMN\s+(?:IF\s+EXISTS\s+)?\"?([\w]+)\"?",
        cleaned_flat,
        re.IGNORECASE,
    ):
        table, column = m.group(1), m.group(2)
        ln = line_of(m.start())
        if _is_temp_table(table):
            continue
        if _has_backup_column(cleaned_flat, table, column):
            warnings.append(
                f"W {rel_path}:{ln} DROP COLUMN {table}.{column} 存在备份列，降级为告警"
            )
            continue
        errors.append(
            f"D4 {rel_path}:{ln} 无备份的 DROP COLUMN {table}.{column} —— 需先备份列数据"
        )

    # D5：TRUNCATE
    for m in re.finditer(r"\bTRUNCATE\s+(?:TABLE\s+)?\"?([\w.]+)\"?", cleaned_flat, re.IGNORECASE):
        target = m.group(1)
        ln = line_of(m.start())
        if _is_temp_table(target):
            continue
        errors.append(f"D5 {rel_path}:{ln} TRUNCATE `{target}` —— 增量脚本禁止清空表")

    # D6：无 WHERE 的 DELETE
    for m in re.finditer(
        r"\bDELETE\s+FROM\s+\"?(?:public\.)?\"?([\w]+)\"?(?!\s+WHERE)([^;]*);",
        cleaned_flat,
        re.IGNORECASE,
    ):
        segment = m.group(2)
        if re.search(r"\bWHERE\b", segment, re.IGNORECASE):
            continue
        table = m.group(1)
        ln = line_of(m.start())
        if _is_temp_table(table):
            continue
        errors.append(
            f"D6 {rel_path}:{ln} DELETE FROM {table} 无 WHERE —— 会删除全表数据"
        )

    # D8：DROP INDEX —— 索引删除不丢数据，但会导致相关查询退化，降级为告警
    for m in re.finditer(r"\bDROP\s+INDEX\s+(?:CONCURRENTLY\s+)?(?:IF\s+EXISTS\s+)?\"?([\w.]+)\"?", cleaned_flat, re.IGNORECASE):
        target = m.group(1)
        ln = line_of(m.start())
        warnings.append(
            f"W {rel_path}:{ln} DROP INDEX `{target}` —— 确认该索引已无查询依赖"
        )

    # D7：ALTER COLUMN TYPE —— 仅在「跨类型且无 USING」时告警；同类型扩宽视为安全
    for m in re.finditer(
        r"\bALTER\s+TABLE\s+\"?(?:public\.)?\"?([\w]+)\"?\s+ALTER\s+COLUMN\s+\"?([\w]+)\"?\s+(?:SET\s+DATA\s+)?TYPE\s+([^;]*);",
        cleaned_flat,
        re.IGNORECASE,
    ):
        segment = m.group(0)
        if re.search(r"\bUSING\b", segment, re.IGNORECASE):
            continue
        table, column, new_type = m.group(1), m.group(2), m.group(3).strip()
        if _is_safe_widening(new_type):
            continue
        ln = line_of(m.start())
        warnings.append(
            f"W {rel_path}:{ln} ALTER COLUMN {table}.{column} TYPE {new_type} 未声明 USING，"
            f"跨类型隐式转换可能丢数据 —— 请人工确认"
        )

    return errors, warnings


def incremental_files(root: Path) -> list[Path]:
    directory = root / INCREMENTAL_DIR
    if not directory.is_dir():
        raise GateError(f"增量脚本目录不存在：{directory}")
    files = sorted(
        p for p in directory.glob("V*.sql") if VERSION_RE.match(p.name)
    )
    if not files:
        raise GateError(f"{directory} 内没有符合 V{YY}.MM.DD.sql 命名的脚本")
    return files


def bootstrap_files(root: Path) -> list[Path]:
    directory = root / BOOTSTRAP_DIR
    if not directory.is_dir():
        return []
    return sorted(p for p in directory.glob("*.sql") if p.is_file())


def read_sql(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        raise GateError(f"读取 {path} 失败：{exc}") from exc


def scan(root: Path, include_bootstrap: bool) -> tuple[list[str], list[str], int]:
    """返回 (错误, 告警, 扫描文件数)。"""
    files = incremental_files(root)
    scanned = len(files)
    errors: list[str] = []
    warnings: list[str] = []
    for path in files:
        rel = path.relative_to(root).as_posix()
        file_errors, file_warnings = scan_sql(rel, read_sql(path))
        errors.extend(file_errors)
        warnings.extend(file_warnings)

    if include_bootstrap:
        for path in bootstrap_files(root):
            rel = path.relative_to(root).as_posix()
            _, file_warnings = scan_sql(rel, read_sql(path))
            warnings.extend(
                f"[bootstrap 仅报告] {w[len('W '):] if w.startswith('W ') else w}"
                for w in file_warnings
            )
            scanned += 1
    return errors, warnings, scanned


def self_test() -> int:
    passed = 0
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    # --- strip_comments ---
    sample = "SELECT 1; -- 注释\n/* 块注释 */\nDROP TABLE x;\n"
    cleaned, lines = strip_comments(sample)
    case("行号保留 4 行（含末尾空行）", len(lines) == 4)
    case("-- 注释与块注释均被清空", "注释" not in cleaned and "/*" not in cleaned)
    case("语句内容保留", "SELECT 1;" in cleaned)
    case("第 3 行仍是 DROP", "DROP TABLE x;" in lines[2][1])
    case("末尾空行不丢", lines[3][1] == "")

    # --- D1 DROP TABLE ---
    errs, _ = scan_sql("t.sql", "DROP TABLE ydsz_user;\n")
    case("普通 DROP TABLE 判红", len(errs) == 1 and errs[0].startswith("D1 "))
    case("D1 理由含禁止", "禁止" in errs[0])

    errs, _ = scan_sql("t.sql", "DROP TABLE IF EXISTS ydsz_user_tmp;\n")
    case("临时表 DROP 不判红", errs == [])

    errs, warns = scan_sql("t.sql", "-- DROP TABLE ydsz_user;\n")
    case("注释掉的 DROP 不判红", errs == [] and warns == [])

    errs, _ = scan_sql("t.sql", "-- 示例：\n-- DROP TABLE IF EXISTS old;\n")
    case("示例块中的 DROP 不判红", errs == [])

    # --- D4 DROP COLUMN ---
    errs, _ = scan_sql("t.sql", 'ALTER TABLE ydsz_user DROP COLUMN status;\n')
    case("无备份 DROP COLUMN 判红", len(errs) == 1 and errs[0].startswith("D4 "))

    errs, warns = scan_sql(
        "t.sql",
        'ALTER TABLE ydsz_user ADD COLUMN status_bak int;\n'
        'ALTER TABLE ydsz_user DROP COLUMN status;\n',
    )
    case("有备份列降级为告警", errs == [] and len(warns) == 1)

    errs, _ = scan_sql(
        "t.sql", "ALTER TABLE ydsz_user_tmp DROP COLUMN status;\n"
    )
    case("临时表 DROP COLUMN 不判红", errs == [])

    # --- D5 TRUNCATE ---
    errs, _ = scan_sql("t.sql", "TRUNCATE ydsz_log;\n")
    case("TRUNCATE 判红", len(errs) == 1 and errs[0].startswith("D5 "))
    errs, _ = scan_sql("t.sql", "TRUNCATE TABLE ydsz_log_tmp;\n")
    case("临时表 TRUNCATE 不判红", errs == [])

    # --- D6 DELETE ---
    errs, _ = scan_sql("t.sql", "DELETE FROM ydsz_log;\n")
    case("无 WHERE DELETE 判红", len(errs) == 1 and errs[0].startswith("D6 "))
    errs, _ = scan_sql("t.sql", "DELETE FROM ydsz_log WHERE id = '1';\n")
    case("带 WHERE 的 DELETE 放过", errs == [])
    errs, _ = scan_sql("t.sql", "DELETE FROM ydsz_log_tmp;\n")
    case("临时表 DELETE 不判红", errs == [])

    # --- D7 ALTER TYPE ---
    errs, warns = scan_sql("t.sql", "ALTER TABLE t ALTER COLUMN c TYPE jsonb;\n")
    case("跨类型 ALTER TYPE 无 USING 告警", errs == [] and len(warns) == 1)
    errs, warns = scan_sql("t.sql", "ALTER TABLE t ALTER COLUMN c TYPE jsonb USING c::jsonb;\n")
    case("跨类型 ALTER TYPE 带 USING 放过", errs == [] and warns == [])
    errs, warns = scan_sql("t.sql", "ALTER TABLE t ALTER COLUMN c TYPE varchar(36);\n")
    case("ID 统一化 varchar(36) 视为安全", errs == [] and warns == [])
    errs, warns = scan_sql("t.sql", "ALTER TABLE t ALTER COLUMN c TYPE text;\n")
    case("text 目标视为安全", errs == [] and warns == [])
    errs, warns = scan_sql("t.sql", "ALTER TABLE t ALTER COLUMN c TYPE bigint;\n")
    case("整数扩宽视为安全", errs == [] and warns == [])

    # --- _is_safe_widening 单测 ---
    case("varchar(36) 安全", _is_safe_widening("varchar(36)"))
    case("VARCHAR(36) 大小写不敏感", _is_safe_widening("VARCHAR(36)"))
    case("character varying(64) 安全", _is_safe_widening("character varying(64)"))
    case("text 安全", _is_safe_widening("text"))
    case("bigint 安全", _is_safe_widening("bigint"))
    case("integer 安全", _is_safe_widening("INTEGER"))
    case("jsonb 不安全", not _is_safe_widening("jsonb"))
    case("numeric(10,2) 不安全", not _is_safe_widening("numeric(10,2)"))
    case("timestamp 不安全", not _is_safe_widening("timestamp"))
    case("带 COLLATE 的 varchar 安全", _is_safe_widening("varchar(36) COLLATE \"C\""))
    case("空字符串不安全", not _is_safe_widening(""))

    # --- D8 DROP INDEX ---
    errs, warns = scan_sql("t.sql", "DROP INDEX CONCURRENTLY IF EXISTS idx_old;\n")
    case("DROP INDEX 降级为告警", errs == [] and len(warns) == 1)
    errs, warns = scan_sql("t.sql", "-- DROP INDEX idx_old;\n")
    case("注释掉的 DROP INDEX 不告警", errs == [] and warns == [])

    # --- D2/D3 ---
    errs, _ = scan_sql("t.sql", "DROP SCHEMA myschema;\n")
    case("DROP SCHEMA 判红", len(errs) == 1 and errs[0].startswith("D2 "))
    errs, _ = scan_sql("t.sql", "DROP DATABASE mydb;\n")
    case("DROP DATABASE 判红", len(errs) == 1 and errs[0].startswith("D3 "))

    # --- 行号定位 ---
    errs, _ = scan_sql("t.sql", "-- 第 1 行\n-- 第 2 行\nDROP TABLE ydsz_user;\n")
    case("行号定位到第 3 行", ":3 " in errs[0])

    # --- 多语句同行 ---
    errs, _ = scan_sql("t.sql", "ALTER TABLE a ADD x int; DROP TABLE ydsz_b;\n")
    case("同行多语句仍能识别", len(errs) == 1)

    # --- 版本号命名 ---
    case("V26.10.01.sql 匹配", VERSION_RE.match("V26.10.01.sql"))
    case("V26.10.01_add_col.sql 匹配", VERSION_RE.match("V26.10.01_add_col.sql"))
    case("bootstrap 名不匹配", not VERSION_RE.match("V26.10.01.bootstrap.sql"))
    case("非 V 前缀不匹配", not VERSION_RE.match("README.md"))

    # --- 临时表后缀 ---
    case("_tmp 判定为临时", _is_temp_table("x_tmp"))
    case("_old 判定为临时", _is_temp_table('"x_old"'))
    case("普通表不判定为临时", not _is_temp_table("ydsz_user"))

    # --- fail-closed ---
    try:
        incremental_files(Path("/nonexistent-repo-path"))
        case("目录缺失 fail-closed", False)
    except GateError:
        case("目录缺失 fail-closed", True)

    case("自检覆盖正反例与 fail-closed", passed >= 26)

    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    total = passed + len(failures)
    if failures:
        print(f"❌ check-db-migrations 自检：{passed}/{total} 条断言通过", file=sys.stderr)
        return 1
    print(f"✅ check-db-migrations 自检：{total}/{total} 条断言通过")
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="数据库增量脚本危险变更门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录（默认脚本所在仓库）")
    parser.add_argument(
        "--all", action="store_true", help="同时扫描 bootstrap 目录（仅报告不判红）"
    )
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    try:
        errors, warnings, scanned = scan(root, include_bootstrap=args.all)
    except GateError as exc:
        print(f"❌ DB 迁移门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1

    print(f"扫描 {scanned} 个 SQL 脚本（增量目录 {INCREMENTAL_DIR}，根目录 {root}）")
    for warn in warnings:
        print(f"⚠️  {warn}")
    for err in errors:
        print(f"❌ {err}")
    if errors:
        print(
            f"❌ DB 迁移门禁失败：{len(errors)} 处危险变更（告警 {len(warnings)} 条）\n"
            f"   增量脚本禁止 DROP TABLE / DROP COLUMN(无备份) / TRUNCATE / 无 WHERE DELETE；\n"
            f"   如需破坏性变更，请先出备份脚本并走人工评审。",
            file=sys.stderr,
        )
        return 1
    print(
        f"✅ DB 迁移门禁通过：无危险变更（告警 {len(warnings)} 条）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
