"""P0-5 SQL DDL 审计字段统一 + 删除 V1.4.0_nextwiki.sql

1. 将所有 V1.0.0_*.sql 中 created_by/updated_by 字段统一为：
   VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL
2. 将 V1.4.0_nextwiki.sql 的两张表（nw_file_comment / nw_audit_log）用 PG 语法
   重写后追加到 V1.0.0_nextwiki.sql 末尾
3. 删除 V1.4.0_nextwiki.sql
4. 为 V1.0.0_nextwiki.sql 的 nw_search_index 表补 updated_by 字段

遵循 .trae/rules/prefer-python-over-powershell.md：UTF-8 编码，无 BOM
"""
import pathlib
import re

SQL_DIR = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\deploy\sql\modules")

# 匹配 created_by 或 updated_by 字段定义（无论当前格式如何）
# 捕获：字段名 + 中间空格 + VARCHAR(N) + 后续到逗号或行尾
FIELD_PATTERN = re.compile(
    r"^(\s*)(created_by|updated_by)(\s+)VARCHAR\(\d+\)([^,\n]*)(,?)\s*$",
    re.MULTILINE,
)

# 标准格式
STANDARD = "{pad}{name}{space}VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL{comma}"


def fix_audit_fields() -> int:
    """统一所有 V1.0.0_*.sql 的 created_by/updated_by 字段。返回修改文件数"""
    count = 0
    for sql_file in sorted(SQL_DIR.glob("V1.0.0_*.sql")):
        if sql_file.name == "V1.0.0_all.sql":
            continue
        content = sql_file.read_text(encoding="utf-8")
        new_content = FIELD_PATTERN.sub(
            lambda m: STANDARD.format(
                pad=m.group(1),
                name=m.group(2),
                space=m.group(3),
                comma=m.group(5),
            ),
            content,
        )
        if new_content != content:
            sql_file.write_text(new_content, encoding="utf-8")
            count += 1
            print(f"  [ok] {sql_file.name}")
    return count


# nw_file_comment 和 nw_audit_log 的 PG 语法重写（从 V1.4.0_nextwiki.sql 迁移）
NEXTWIKI_APPEND = """
-- 10. 文件评论表（从 V1.4.0 合并，PG 语法重写）
CREATE TABLE IF NOT EXISTS nw_file_comment (
    id                VARCHAR(32)   PRIMARY KEY,
    file_node_id      VARCHAR(32)   NOT NULL,
    content           TEXT          NOT NULL,
    parent_comment_id VARCHAR(32)   DEFAULT NULL,
    resolved          BOOLEAN       NOT NULL DEFAULT FALSE,
    position          VARCHAR(500)  DEFAULT NULL,
    edited            BOOLEAN       NOT NULL DEFAULT FALSE,
    revision          INT           NOT NULL DEFAULT 0,
    deleted           INT           NOT NULL DEFAULT 0,
    status            VARCHAR(20)   DEFAULT 'active',
    created_by        VARCHAR(64)   DEFAULT 'SYSTEM' NOT NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   DEFAULT 'SYSTEM' NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE nw_file_comment IS '文件评论表';

CREATE INDEX IF NOT EXISTS idx_file_comment_node ON nw_file_comment (file_node_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_comment_parent ON nw_file_comment (parent_comment_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_comment_created_by ON nw_file_comment (created_by) WHERE deleted = 0;

-- 11. 审计日志表（从 V1.4.0 合并，PG 语法重写）
CREATE TABLE IF NOT EXISTS nw_audit_log (
    id              VARCHAR(32)   PRIMARY KEY,
    operation       VARCHAR(50)   NOT NULL,
    file_node_id    VARCHAR(32)   DEFAULT NULL,
    file_name       VARCHAR(255)  DEFAULT NULL,
    node_type       VARCHAR(20)   DEFAULT NULL,
    storage_key     VARCHAR(500)  DEFAULT NULL,
    bucket_name     VARCHAR(100)  DEFAULT NULL,
    operator_id     VARCHAR(32)   NOT NULL,
    operated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    extra           VARCHAR(2000) DEFAULT NULL,
    result          VARCHAR(20)   DEFAULT 'success',
    error_message   VARCHAR(1000) DEFAULT NULL,
    revision        INT           NOT NULL DEFAULT 0,
    deleted         INT           NOT NULL DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'active',
    created_by      VARCHAR(64)   DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE nw_audit_log IS '审计日志表';

CREATE INDEX IF NOT EXISTS idx_audit_log_node ON nw_audit_log (file_node_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_audit_log_operator ON nw_audit_log (operator_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_audit_log_operated_at ON nw_audit_log (operated_at) WHERE deleted = 0;
"""


def merge_v1_4_to_v1_0() -> None:
    """将 V1.4.0_nextwiki.sql 的两张表合并到 V1.0.0_nextwiki.sql，然后删除 V1.4.0"""
    nextwiki = SQL_DIR / "V1.0.0_nextwiki.sql"
    v140 = SQL_DIR / "V1.4.0_nextwiki.sql"

    content = nextwiki.read_text(encoding="utf-8")

    # 检查是否已合并（避免重复追加）
    if "nw_file_comment" not in content:
        # 在文件末尾追加（确保末尾有换行）
        if not content.endswith("\n"):
            content += "\n"
        content += NEXTWIKI_APPEND
        nextwiki.write_text(content, encoding="utf-8")
        print(f"  [ok] appended nw_file_comment + nw_audit_log to {nextwiki.name}")

    # 为 nw_search_index 表补 updated_by 字段（如果缺失）
    # nw_search_index 表当前只有 created_by（L227），没有 updated_by
    # 匹配模式：在 nw_search_index 表的 created_by 行后插入 updated_by 行
    search_index_pattern = re.compile(
        r"(CREATE TABLE IF NOT EXISTS nw_search_index.*?created_by\s+VARCHAR\(64\)[^\n]*\n)"
        r"(\s+updated_at)",
        re.DOTALL,
    )
    new_content = nextwiki.read_text(encoding="utf-8")
    if search_index_pattern.search(new_content) and "nw_search_index" in new_content:
        # 检查 nw_search_index 表内是否已有 updated_by
        si_match = re.search(
            r"CREATE TABLE IF NOT EXISTS nw_search_index.*?\);",
            new_content,
            re.DOTALL,
        )
        if si_match and "updated_by" not in si_match.group(0):
            new_content = search_index_pattern.sub(
                r"\1    updated_by      VARCHAR(64)   DEFAULT 'SYSTEM' NOT NULL,\n\2",
                new_content,
            )
            nextwiki.write_text(new_content, encoding="utf-8")
            print(f"  [ok] added updated_by to nw_search_index in {nextwiki.name}")

    # 删除 V1.4.0_nextwiki.sql
    if v140.exists():
        v140.unlink()
        print(f"  [ok] deleted {v140.name}")


if __name__ == "__main__":
    print("=== P0-5: unify created_by/updated_by audit fields ===")
    fc = fix_audit_fields()
    print(f"Modified {fc} SQL files\n")

    print("=== P0-5: merge V1.4.0_nextwiki.sql into V1.0.0_nextwiki.sql ===")
    merge_v1_4_to_v1_0()
    print("\nDone.")
