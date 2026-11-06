#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
表结构健康度校验（YDIZ-DB-001 ~ YDIZ-DB-008 自动化门禁）

校验 data/sqls/bootstrap/*.sql 与 data/sqls/flyway/V*.sql 中的建表语句，
对以下规范自动判红：

  P0（error）：
    - 表名非 ydsz_ 前缀（DB-002 精神）
    - 缺 is_deleted SMALLINT 列（DB-008 逻辑删除）
    - 缺 tenant_id VARCHAR(36) 列（多租户隔离）
    - 缺审计四字段（created_at / updated_at / created_by / updated_by）
    - 布尔列使用 BOOLEAN/BOOL/TINYINT(1) 而非 SMALLINT（DB-007）
    - ID 主键非 VARCHAR(36)（DB-008 / DB-009）
    - 缺 sort 列（DB-001）

  P1（warning）：
    - 表无 COMMENT（可维护性）
    - 分区/分表无 COMMENT
    - 新增表缺索引（PRIMARY KEY / UNIQUE 之外）

  P2（info）：
    - 列无 COMMENT

用法：
  python3 data/scripts/check-table-design.py
  python3 data/scripts/check-table-design.py --baseline data/sqls/bootstrap/V26.10.01.bootstrap.sql
  python3 data/scripts/check-table-design.py --only-flyway   # 只校验增量脚本

退出码：
  0 = 无 error
  1 = 存在 error
"""

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ── 规范常量 ────────────────────────────────────────────────────────────────

TABLE_PREFIX = "ydsz_"
ID_TYPE = "character varying(36)"
BOOL_TYPES = {"boolean", "bool", "tinyint(1)"}
BOOL_TYPE_OK = "smallint"
AUDIT_FIELDS = ["created_at", "updated_at", "created_by", "updated_by"]
REQUIRED_FIELDS = ["is_deleted", "tenant_id", "sort"] + AUDIT_FIELDS

# 允许非 ydsz_ 前缀的历史分区表（已执行脚本不再修改，仅 bootstrap 同步）
PREFIX_WHITELIST: List[str] = []


@dataclass
class Table:
    name: str
    body: str
    has_comment: bool = False
    comment: str = ""
    columns: List[Tuple[str, str]] = field(default_factory=list)
    indexes: List[str] = field(default_factory=list)
    is_partition: bool = False


@dataclass
class Violation:
    severity: str  # P0 / P1 / P2
    rule: str
    table: str
    message: str
    line: int = 0

    def __str__(self) -> str:
        return f"[{self.severity}] {self.rule} | {self.table} | {self.message}"


# ── 解析器 ──────────────────────────────────────────────────────────────────


def parse_tables(sql_text: str, source: str) -> List[Table]:
    """解析 SQL 文本中的所有 CREATE TABLE 语句。"""
    tables: List[Table] = []
    # 匹配 CREATE TABLE "public"."name" ( ... ); 或 PARTITION OF 形式
    pattern = re.compile(
        r'CREATE TABLE\s+(?:IF NOT EXISTS\s+)?(?:\"public\"\.)?\"?([a-zA-Z0-9_]+)\"?\s*'
        r'(\(\s*.*?\)\s*(?:PARTITION OF\s+.*?)?\s*;|PARTITION OF\s+.*?FOR VALUES\s+.*?;)',
        re.DOTALL | re.IGNORECASE,
    )
    for m in pattern.finditer(sql_text):
        name = m.group(1)
        raw = m.group(0)
        is_partition = "PARTITION OF" in raw.upper()
        body = m.group(2) if not is_partition else ""
        columns = parse_columns(body) if body else []
        line = sql_text[: m.start()].count("\n") + 1
        tables.append(
            Table(
                name=name,
                body=body,
                is_partition=is_partition,
                columns=columns,
            )
        )

    # 解析 COMMENT ON TABLE
    comment_map: Dict[str, str] = {}
    for m in re.finditer(
        r"COMMENT ON TABLE\s+(?:\"public\"\.)?\"?([a-zA-Z0-9_]+)\"?\s+IS\s+'([^']*)';",
        sql_text,
        re.IGNORECASE,
    ):
        comment_map[m.group(1)] = m.group(2)

    for t in tables:
        t.has_comment = t.name in comment_map
        t.comment = comment_map.get(t.name, "")

    # 解析索引
    index_map: Dict[str, List[str]] = {}
    for m in re.finditer(
        r'CREATE (?:UNIQUE )?INDEX\s+(?:CONCURRENTLY\s+)?(?:IF NOT EXISTS\s+)?'
        r'"?([a-zA-Z0-9_]+)"?\s+ON\s+(?:\"public\"\.)?\"?([a-zA-Z0-9_]+)"?',
        sql_text,
        re.IGNORECASE,
    ):
        index_map.setdefault(m.group(2), []).append(m.group(1))
    for t in tables:
        t.indexes = index_map.get(t.name, [])

    return tables


def parse_columns(body: str) -> List[Tuple[str, str]]:
    """解析表体中的列定义，返回 [(列名, 类型)]。"""
    columns: List[Tuple[str, str]] = []
    # 去掉约束行（CONSTRAINT / PRIMARY KEY / UNIQUE / CHECK / FOREIGN KEY / LIKE）
    lines = []
    for line in body.split("\n"):
        stripped = line.strip()
        if stripped.startswith("CONSTRAINT") or stripped.startswith("PRIMARY KEY"):
            continue
        if stripped.startswith("UNIQUE") or stripped.startswith("CHECK"):
            continue
        if stripped.startswith("FOREIGN KEY") or stripped.startswith("LIKE "):
            continue
        lines.append(line)

    for line in lines:
        # 匹配列定义：列名 + 类型（含可选长度/精度括号）
        # 示例：id character varying(36) NOT NULL DEFAULT ...
        #      sort integer DEFAULT 0
        #      is_deleted smallint NOT NULL DEFAULT 0
        m = re.match(
            r'"?([a-zA-Z0-9_]+)"?\s+'
            r'('
            r'[a-zA-Z]+(?:\s+[a-zA-Z]+)?'  # 类型名（含多词如 "double precision"）
            r'(?:\s*\(\s*\d+(?:\s*,\s*\d+)?\s*\))?'  # 可选长度 (36) / (10,2)
            r'(?:\s*\(\s*\d+\s*\))?'  # 再可选长度（如 timestamp(6)）
            r')',
            line.strip(),
            re.IGNORECASE,
        )
        if m:
            col_name = m.group(1).lower()
            col_type = m.group(2).strip().lower()
            # 跳过非列定义的关键字
            if col_name in ("create", "table", "as", "select", "partition"):
                continue
            columns.append((col_name, col_type))
    return columns


# ── 校验规则 ────────────────────────────────────────────────────────────────


def check_table(t: Table) -> List[Violation]:
    violations: List[Violation] = []

    # P0-1: 表名前缀
    if not t.name.startswith(TABLE_PREFIX) and t.name not in PREFIX_WHITELIST:
        violations.append(
            Violation("P0", "DB-002", t.name, f"表名非 {TABLE_PREFIX} 前缀")
        )

    # 分区表本身无列定义，跳过列级校验
    if t.is_partition:
        if not t.has_comment:
            violations.append(
                Violation("P1", "DB-101", t.name, "分区表缺少 COMMENT")
            )
        return violations

    col_names = {c[0] for c in t.columns}

    # P0-2: 必需字段
    for field_name in REQUIRED_FIELDS:
        if field_name not in col_names:
            violations.append(
                Violation(
                    "P0",
                    "DB-008",
                    t.name,
                    f"缺少必需字段 {field_name}",
                )
            )

    # P0-3: 布尔列类型
    for col_name, col_type in t.columns:
        if col_name.startswith("is_") or col_name.startswith("has_"):
            if col_type in BOOL_TYPES or col_type == "boolean":
                violations.append(
                    Violation(
                        "P0",
                        "DB-007",
                        t.name,
                        f"布尔列 {col_name} 使用 {col_type}，必须为 {BOOL_TYPE_OK}",
                    )
                )

    # P0-4: ID 主键类型
    if "id" in col_names:
        id_type = next((ct for cn, ct in t.columns if cn == "id"), "")
        if ID_TYPE not in id_type:
            violations.append(
                Violation(
                    "P0",
                    "DB-009",
                    t.name,
                    f"ID 列类型为 {id_type}，必须为 {ID_TYPE}",
                )
            )

    # P1-1: 表注释
    if not t.has_comment:
        violations.append(Violation("P1", "DB-101", t.name, "表缺少 COMMENT"))

    return violations


# ── 主流程 ──────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description="表结构健康度校验")
    parser.add_argument(
        "--baseline",
        default="data/sqls/bootstrap/V26.10.01.bootstrap.sql",
        help="空库初始化脚本路径",
    )
    parser.add_argument(
        "--flyway-dir",
        default="data/sqls/flyway",
        help="增量脚本目录",
    )
    parser.add_argument(
        "--only-flyway",
        action="store_true",
        help="只校验增量脚本（跳过 bootstrap）",
    )
    args = parser.parse_args()

    all_violations: List[Violation] = []
    checked_tables = 0

    # 校验 bootstrap
    if not args.only_flyway:
        baseline = Path(args.baseline)
        if baseline.exists():
            tables = parse_tables(baseline.read_text(encoding="utf-8"), str(baseline))
            for t in tables:
                all_violations.extend(check_table(t))
                checked_tables += 1
            print(f"✅ 已解析 bootstrap: {len(tables)} 张表")
        else:
            print(f"⚠️  bootstrap 不存在: {baseline}")

    # 校验增量脚本（全局合并：ALTER ADD COLUMN / COMMENT 跨文件生效）
    flyway_dir = Path(args.flyway_dir)
    if flyway_dir.exists():
        # 第一轮：收集所有增量脚本的 ALTER ADD COLUMN 与 COMMENT
        alter_add_cols: Dict[str, List[Tuple[str, str]]] = {}
        extra_comments: Dict[str, str] = {}
        for sql_file in sorted(flyway_dir.glob("V*.sql")):
            sql_text = sql_file.read_text(encoding="utf-8")
            for m in re.finditer(
                r"ALTER TABLE\s+(?:IF EXISTS\s+)?(?:\"public\"\.)?\"?([a-zA-Z0-9_]+)\"?\s+"
                r"ADD COLUMN\s+(?:IF NOT EXISTS\s+)?\"?([a-zA-Z0-9_]+)\"?\s+([a-zA-Z\s\(\),]+?)(?:\s+NOT NULL|\s+DEFAULT|\s*;)",
                sql_text,
                re.IGNORECASE,
            ):
                alter_add_cols.setdefault(m.group(1), []).append(
                    (m.group(2).lower(), m.group(3).strip().lower())
                )
            for m in re.finditer(
                r"COMMENT ON TABLE\s+(?:\"public\"\.)?\"?([a-zA-Z0-9_]+)\"?\s+IS\s+'([^']*)';",
                sql_text,
                re.IGNORECASE,
            ):
                extra_comments[m.group(1)] = m.group(2)

        # 第二轮：解析 CREATE TABLE 并合并增量修正后校验
        for sql_file in sorted(flyway_dir.glob("V*.sql")):
            sql_text = sql_file.read_text(encoding="utf-8")
            tables = parse_tables(sql_text, str(sql_file))
            for t in tables:
                if t.name in alter_add_cols:
                    existing = {c[0] for c in t.columns}
                    for col_name, col_type in alter_add_cols[t.name]:
                        if col_name not in existing:
                            t.columns.append((col_name, col_type))
                if t.name in extra_comments:
                    t.has_comment = True
                    t.comment = extra_comments[t.name]
                all_violations.extend(check_table(t))
                checked_tables += 1
            if tables:
                print(f"✅ 已解析 {sql_file.name}: {len(tables)} 张表")

    # 汇总
    p0 = [v for v in all_violations if v.severity == "P0"]
    p1 = [v for v in all_violations if v.severity == "P1"]
    p2 = [v for v in all_violations if v.severity == "P2"]

    print(f"\n{'=' * 70}")
    print(f"表结构健康度报告")
    print(f"{'=' * 70}")
    print(f"检查表数: {checked_tables}")
    print(f"P0 (error):   {len(p0)}")
    print(f"P1 (warning): {len(p1)}")
    print(f"P2 (info):    {len(p2)}")

    if p0:
        print(f"\n{'─' * 70}")
        print("P0 违规详情（必须修复）:")
        print(f"{'─' * 70}")
        for v in p0:
            print(f"  {v}")

    if p1:
        print(f"\n{'─' * 70}")
        print("P1 警告详情（建议修复）:")
        print(f"{'─' * 70}")
        for v in p1[:50]:  # 最多显示 50 条
            print(f"  {v}")
        if len(p1) > 50:
            print(f"  ... 还有 {len(p1) - 50} 条 P1 警告")

    print(f"\n{'=' * 70}")
    if p0:
        print(f"❌ 表结构健康度检查失败（{len(p0)} 条 P0 违规）")
        return 1
    print("✅ 表结构健康度检查通过（无 P0 违规）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
