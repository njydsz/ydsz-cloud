#!/usr/bin/env python3
"""Fix V1.0.0.sql issues and regenerate module SQL files.

Fixes:
1. Add DEFAULT for id columns (VARCHAR PRIMARY KEY without DEFAULT)
2. Fix 'COMMENT ON COLUMN IF EXISTS' -> 'COMMENT ON COLUMN'
3. Move CREATE EXTENSION vector to top with other extensions
4. Cross-module statements assigned to correct module by table name prefix
"""
import re
import os

SQL_FILE = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
MODULE_DIR = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"

# Module order and their table prefixes.
# 拆分依据(2026-07-10 重构):按后端服务的 **物理 Mapper/DO 实际所在模块** 归位,
#   禁止按"表名看起来像哪个模块"主观分类(如 pmis_job_level 是 userinfo 的职级,
#   不归 cronjob)。common 几乎没有自己的 DDL,仅承载 extensions / PL/pgSQL 函数 / 触发器。
# 前缀匹配规则:按长度降序,最长前缀优先(`pmis_job_level` > `pmis_job`)。
MODULE_PREFIXES = {
    # common: extensions + PL/pgSQL 函数 + 触发器,无业务 DDL
    "common":   [],
    # system: 配置/租户/文件/操作日志/审计/字典版本/报表订阅/导出记录/元数据
    "system":   [
        "pmis_config", "pmis_tenant_", "pmis_file",
        "pmis_operation_log",  # 短前缀优先,pmis_operation_log_default 是其 DEFAULT 分区表,同表归 system
        "pmis_login_audit", "pmis_data_export_audit", "pmis_sensitive_operation",
        "pmis_dict_version", "pmis_report_subscription", "pmis_export_record",
        "pmis_meta_schema_version",
    ],
    # userinfo: RBAC + 用户/部门/岗位/字典/资源/考勤 + 职级系列
    "userinfo": [
        "pmis_user_", "pmis_role", "pmis_permission",
        "pmis_department", "pmis_employee", "pmis_position", "pmis_org_",
        "pmis_dict_type", "pmis_dict_item",
        "pmis_part_time", "pmis_outsource",
        "pmis_resource_assignment", "pmis_bench_record",
        "pmis_attendance", "pmis_overtime", "pmis_leave",
        "pmis_job_level", "pmis_job_level_rate",  # 职级/费率(JobLevelMapper 在 userinfo)
    ],
    # project: 项目 + 执行 + 成本 + 财务 + 资源 + 售后 + 8 张 literule 业务表
    "project":  [
        "pmis_project_", "pmis_opportunity", "pmis_contract", "pmis_delivery",
        "pmis_expense", "pmis_payment", "pmis_revenue", "pmis_reconcile",
        "pmis_risk", "pmis_rate", "pmis_resource_pool", "pmis_timesheet",
        "pmis_closure", "pmis_warranty", "pmis_eval_", "pmis_cockpit",
        "pmis_search_", "pmis_rule_admin",
        "pmis_view_initiation", "pmis_view_cockpit",
        "pmis_execution_", "pmis_cost_", "pmis_profit_", "pmis_finance_", "pmis_evm_",
        "pmis_ops_ticket", "pmis_satisfaction", "pmis_billable_utilization_snapshot",
        # literule 业务表(执行跟踪/决策表/AB 实验/评分卡),物理 Mapper 在 project 模块
        "pmis_rule_execution_trace", "pmis_rule_decision_table",
        "pmis_rule_canary_bucket", "pmis_rule_scorecard",
        "pmis_rule_decision_tree", "pmis_rule_script",
        "pmis_rule_ab_policy", "pmis_rule_ab_rollback",
    ],
    # cronjob: pmis_job 主表(任务定义) + 调度节点/日志/DAG/告警子表
    "cronjob":  [
        "pmis_job_node", "pmis_job_log", "pmis_job_log_content",
        "pmis_job_task", "pmis_job_glue", "pmis_job_history",
        "pmis_job_slow_log", "pmis_job_relation", "pmis_job_dag",
        "pmis_job_dag_instance", "pmis_job_dag_node_instance",
        "pmis_job_alert_rule", "pmis_job_alert_log",
        "pmis_job_daily_stats", "pmis_job_sla", "pmis_job_version_history",
        "pmis_job_artifact", "pmis_job_webhook",
        "pmis_alert_dispatch",  # 通用预警派发表(供 cronjob + agent 共用)
        "pmis_schedule", "pmis_sla_", "pmis_webhook", "pmis_dag_",
        "pmis_job",  # 任务定义主表(无下划线后缀,需单独列出,长度 8 短于子表)
    ],
    "message":  ["pmis_msg_", "pmis_notification", "pmis_message_"],
    "workflow": ["pmis_flow_", "pmis_view_flow"],
    "agent":    ["pmis_agent_", "pmis_knowledge_", "pmis_token_", "pmis_tool_",
                 "pmis_hitl_", "pmis_mcp_"],
    "literule": [
        "pmis_rule_def", "pmis_rule_version", "pmis_rule_template",
        "pmis_rule_test", "pmis_rule_variable", "pmis_rule_chain",
        "pmis_rule_dependency", "pmis_rule_pack", "pmis_rule_node",
        "pmis_rule_event", "pmis_rule_log",
    ],
}

# Extension prefix for each module. 放在 common 里,所有模块共享。
EXTENSIONS = {
    "common": ["uuid-ossp", "pgcrypto", "pg_trgm"]
}

def get_module_for_table(table_name):
    """Determine which module a table belongs to based on its name."""
    # Sort prefixes by length descending for more specific matches first
    all_prefixes = []
    for mod, prefixes in MODULE_PREFIXES.items():
        for p in prefixes:
            all_prefixes.append((p, mod))
    all_prefixes.sort(key=lambda x: len(x[0]), reverse=True)
    
    for prefix, mod in all_prefixes:
        if table_name.startswith(prefix):
            return mod
    return "common"  # default

def parse_sql_statements(sql_text):
    """Parse SQL into individual statements."""
    statements = []
    current = []
    i = 0
    length = len(sql_text)
    
    while i < length:
        ch = sql_text[i]
        
        # Line comment
        if ch == '-' and i + 1 < length and sql_text[i + 1] == '-':
            while i < length and sql_text[i] != '\n':
                current.append(sql_text[i])
                i += 1
            if i < length:
                current.append(sql_text[i])
                i += 1
            continue
        
        # Block comment
        if ch == '/' and i + 1 < length and sql_text[i + 1] == '*':
            current.append(sql_text[i])
            current.append(sql_text[i + 1])
            i += 2
            while i < length:
                if sql_text[i] == '*' and i + 1 < length and sql_text[i + 1] == '/':
                    current.append(sql_text[i])
                    current.append(sql_text[i + 1])
                    i += 2
                    break
                current.append(sql_text[i])
                i += 1
            continue
        
        # Dollar-quoted string
        if ch == '$':
            j = i + 1
            while j < length and (sql_text[j].isalnum() or sql_text[j] == '_'):
                j += 1
            if j < length and sql_text[j] == '$':
                tag = sql_text[i:j + 1]
                end = sql_text.find(tag, j + 1)
                if end != -1:
                    current.append(sql_text[i:end + len(tag)])
                    i = end + len(tag)
                    continue
        
        # Single-quoted string
        if ch == "'":
            current.append(ch)
            i += 1
            while i < length:
                if sql_text[i] == '\\' and i + 1 < length:
                    current.append(sql_text[i])
                    current.append(sql_text[i + 1])
                    i += 2
                    continue
                if sql_text[i] == "'":
                    if i + 1 < length and sql_text[i + 1] == "'":
                        current.append(sql_text[i])
                        current.append(sql_text[i + 1])
                        i += 2
                        continue
                    else:
                        current.append(sql_text[i])
                        i += 1
                        break
                current.append(sql_text[i])
                i += 1
            continue
        
        # Semicolon
        if ch == ';':
            current.append(ch)
            statements.append(''.join(current))
            current = []
            i += 1
            continue
        
        current.append(ch)
        i += 1
    
    rem = ''.join(current).strip()
    if rem:
        statements.append(rem)
    
    return statements

def extract_table_name(stmt):
    """Extract table name from a SQL statement."""
    # CREATE TABLE [IF NOT EXISTS] table_name
    m = re.search(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # COMMENT ON TABLE table_name
    m = re.search(r'COMMENT\s+ON\s+TABLE\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # COMMENT ON COLUMN table_name.column
    m = re.search(r'COMMENT\s+ON\s+COLUMN\s+(?:IF\s+EXISTS\s+)?(\w+)\.', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # CREATE [UNIQUE] INDEX [IF NOT EXISTS] name ON table_name
    m = re.search(r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?\w+\s+ON\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # INSERT INTO table_name
    m = re.search(r'INSERT\s+INTO\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # ALTER TABLE table_name
    m = re.search(r'ALTER\s+TABLE\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # ANALYZE table_name
    m = re.search(r'ANALYZE\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)
    
    # CREATE TRIGGER ... ON table_name
    m = re.search(r'CREATE\s+TRIGGER\s+\w+\s+(?:BEFORE|AFTER)\s+\w+\s+ON\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)

    # CREATE [OR REPLACE] VIEW table_name
    m = re.search(r'CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)

    # COMMENT ON VIEW view_name
    m = re.search(r'COMMENT\s+ON\s+VIEW\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)

    # ALTER VIEW view_name
    m = re.search(r'ALTER\s+VIEW\s+(\w+)', stmt, re.IGNORECASE)
    if m:
        return m.group(1)

    return None

def fix_id_defaults(sql_content):
    """Add DEFAULT to id VARCHAR columns that are PRIMARY KEY without DEFAULT."""
    # Pattern: id <whitespace> VARCHAR(<n>) <whitespace> PRIMARY KEY
    # Replace with: id <whitespace> VARCHAR(<n>) <whitespace> PRIMARY KEY DEFAULT replace(uuid_generate_v4()::text,'-','')
    pattern = r"(id\s+VARCHAR\(\d+\)\s+PRIMARY\s+KEY)(?!\s+DEFAULT)"
    replacement = r"\1 DEFAULT replace(uuid_generate_v4()::text,'-','')"
    return re.sub(pattern, replacement, sql_content, flags=re.IGNORECASE)

def fix_comment_if_exists(sql_content):
    """Fix 'COMMENT ON COLUMN IF EXISTS' to 'COMMENT ON COLUMN'."""
    return re.sub(
        r'COMMENT\s+ON\s+COLUMN\s+IF\s+EXISTS\s+',
        'COMMENT ON COLUMN ',
        sql_content,
        flags=re.IGNORECASE
    )

def fix_extension_placement(sql_content):
    """Move CREATE EXTENSION vector to the top with other extensions."""
    # Find and remove the vector extension line
    vector_pattern = r"CREATE\s+EXTENSION\s+IF\s+NOT\s+EXISTS\s+vector\s*;[^\n]*\n"
    vector_match = re.search(vector_pattern, sql_content, re.IGNORECASE)
    
    if vector_match:
        # Remove from current position
        sql_content = sql_content[:vector_match.start()] + sql_content[vector_match.end():]
        
        # Add after pgcrypto extension
        pgcrypto_pattern = r'(CREATE\s+EXTENSION\s+IF\s+NOT\s+EXISTS\s+"pgcrypto"\s*;[^\n]*\n)'
        pgcrypto_match = re.search(pgcrypto_pattern, sql_content, re.IGNORECASE)
        if pgcrypto_match:
            insert_pos = pgcrypto_match.end()
            sql_content = (
                sql_content[:insert_pos] +
                'CREATE EXTENSION IF NOT EXISTS vector;\n' +
                sql_content[insert_pos:]
            )
            print("[FIX] Moved CREATE EXTENSION vector to top")
        else:
            print("[WARN] Could not find pgcrypto extension to insert after")
    else:
        print("[INFO] CREATE EXTENSION vector not found separately, may already be at top")
    
    return sql_content

def main():
    print("=" * 60)
    print("SQL Fix & Regenerate Script")
    print("=" * 60)
    
    # 1. Read original file
    print(f"\n[1] Reading {SQL_FILE}...")
    with open(SQL_FILE, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    print(f"    File size: {len(sql_content)} chars, {sql_content.count(chr(10))} lines")
    
    # 2. Fix issues
    print(f"\n[2] Applying fixes...")
    
    # Fix 1: COMMENT ON COLUMN IF EXISTS
    before = sql_content.count('COMMENT ON COLUMN IF EXISTS')
    sql_content = fix_comment_if_exists(sql_content)
    after = sql_content.count('COMMENT ON COLUMN IF EXISTS')
    print(f"    COMMENT ON COLUMN IF EXISTS: {before} -> {after}")
    
    # Fix 2: id DEFAULT
    before_count = len(re.findall(r"id\s+VARCHAR\(\d+\)\s+PRIMARY\s+KEY(?!\s+DEFAULT)", sql_content, re.IGNORECASE))
    sql_content = fix_id_defaults(sql_content)
    after_count = len(re.findall(r"id\s+VARCHAR\(\d+\)\s+PRIMARY\s+KEY(?!\s+DEFAULT)", sql_content, re.IGNORECASE))
    print(f"    id DEFAULT added: {before_count} -> {after_count} remaining without DEFAULT")
    
    # Fix 3: Extension placement
    sql_content = fix_extension_placement(sql_content)
    
    # 3. Write fixed file
    print(f"\n[3] Writing fixed {SQL_FILE}...")
    with open(SQL_FILE, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    print(f"    Done")
    
    # 4. Split into modules
    print(f"\n[4] Splitting into modules...")
    statements = parse_sql_statements(sql_content)
    print(f"    Total statements: {len(statements)}")
    
    # Categorize statements
    module_statements = {mod: [] for mod in MODULE_PREFIXES.keys()}
    no_module = []
    
    for stmt in statements:
        table_name = extract_table_name(stmt)
        if table_name:
            mod = get_module_for_table(table_name)
            module_statements[mod].append(stmt)
        else:
            # Check for extension/pragma statements
            stripped = stmt.strip()
            if re.match(r'CREATE\s+EXTENSION', stripped, re.IGNORECASE):
                # Extensions go to common
                if 'vector' in stripped.lower() or 'uuid' in stripped.lower() or 'pgcrypto' in stripped.lower() or 'pg_trgm' in stripped.lower():
                    module_statements["common"].append(stmt)
                elif 'pg_stat_statements' in stripped.lower() or 'pg_hint_plan' in stripped.lower():
                    module_statements["common"].append(stmt)
                else:
                    module_statements["common"].append(stmt)
            elif re.match(r'CREATE\s+OR\s+REPLACE\s+FUNCTION|CREATE\s+OR\s+REPLACE\s+PROCEDURE|CREATE\s+FUNCTION|DO\s+\$', stripped, re.IGNORECASE):
                # Functions/procedures go to common
                module_statements["common"].append(stmt)
            elif re.match(r'--\s*=|--\s*-', stripped):
                # Comment blocks - skip
                pass
            else:
                # 兜底:事务头(BEGIN/COMMIT)、SET 会话参数、孤儿 DDL 等
                # 全部归 common,作为全局"前言"。这样保证 V1.0.0.sql
                # 重新合并时事务包装和 search_path 不会丢失。
                module_statements["common"].append(stmt)
                no_module.append(stmt)  # 同时记录便于 WARN 复核
    
    # Report
    for mod, stmts in module_statements.items():
        print(f"    {mod}: {len(stmts)} statements")
    if no_module:
        print(f"    [WARN] No module assigned: {len(no_module)} statements")
        for s in no_module[:5]:
            print(f"      - {s[:100]}")
    
    # 5. Write module files
    print(f"\n[5] Writing module files...")
    headers = {
        "common": """-- ============================================================
-- PMIS common module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本承载全局 PG 扩展 (uuid-ossp / pgcrypto / pg_trgm / vector) 与
--  PL/pgSQL 函数 / 触发器,无业务 DDL。所有业务表按"物理 Mapper 所在后端模块"
-- 归位到 system / userinfo / project / cronjob / message / workflow / agent / literule 各自脚本。
-- 跨服务引用:Feign + NameAssembler,统一在 CommonAutoConfiguration 注册。
""",
    }
    default_header = """-- ============================================================
-- PMIS {module} module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 {module} 服务 (ydsz-pmis-{module}) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
"""

    for mod, stmts in module_statements.items():
        filepath = os.path.join(MODULE_DIR, f"V1.0.0_{mod}.sql")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(headers.get(mod, default_header).format(module=mod))
            for stmt in stmts:
                f.write(stmt.strip() + '\n\n')
        print(f"    {filepath}: {len(stmts)} statements")
    
    # 6. Write all.sql
    all_filepath = os.path.join(MODULE_DIR, "V1.0.0_all.sql")
    with open(all_filepath, 'w', encoding='utf-8') as f:
        f.write("""-- ============================================================
-- PMIS Full Database Initialization Script
-- Executes all module scripts in dependency order
-- ============================================================

""")
        for mod in MODULE_PREFIXES.keys():
            f.write(f"\\i V1.0.0_{mod}.sql\n")
        print(f"    {all_filepath}: all modules referenced")
    
    print(f"\n[DONE] All fixes applied and modules regenerated.")

if __name__ == "__main__":
    main()
