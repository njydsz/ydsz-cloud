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

# Module order and their table prefixes
MODULE_PREFIXES = {
    "common":   ["pmis_dict_", "pmis_audit_", "pmis_file_", "pmis_attachment_", "pmis_sys_"],
    "system":   ["pmis_config", "pmis_tenant", "pmis_feature", "pmis_menu", "pmis_i18n"],
    "userinfo": ["pmis_user_", "pmis_role", "pmis_permission", "pmis_dept", "pmis_employee",
                 "pmis_part_time", "pmis_outsource", "pmis_position", "pmis_org_"],
    "project":  ["pmis_project_", "pmis_opportunity", "pmis_contract", "pmis_delivery",
                 "pmis_expense", "pmis_payment", "pmis_revenue", "pmis_reconcile",
                 "pmis_risk", "pmis_rate", "pmis_resource_pool", "pmis_timesheet",
                 "pmis_closure", "pmis_warranty", "pmis_eval_", "pmis_cockpit",
                 "pmis_search_", "pmis_rule_admin"],
    "cronjob":  ["pmis_job_", "pmis_schedule", "pmis_task_", "pmis_sla_", "pmis_alert",
                 "pmis_webhook", "pmis_dag_"],
    "message":  ["pmis_msg_", "pmis_notification", "pmis_message_"],
    "workflow": ["pmis_flow_"],
    "agent":    ["pmis_agent_", "pmis_knowledge_", "pmis_token_", "pmis_tool_",
                 "pmis_hitl_", "pmis_mcp_"],
    "literule": ["pmis_rule_def", "pmis_rule_version", "pmis_rule_template",
                 "pmis_rule_test", "pmis_rule_variable", "pmis_rule_chain",
                 "pmis_rule_dependency", "pmis_rule_pack", "pmis_rule_node",
                 "pmis_rule_event", "pmis_rule_log"],
}

# Extension prefix for each module
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
                no_module.append(stmt)
    
    # Report
    for mod, stmts in module_statements.items():
        print(f"    {mod}: {len(stmts)} statements")
    if no_module:
        print(f"    [WARN] No module assigned: {len(no_module)} statements")
        for s in no_module[:5]:
            print(f"      - {s[:100]}")
    
    # 5. Write module files
    print(f"\n[5] Writing module files...")
    header = """-- ============================================================
-- PMIS {module} module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================

"""
    
    for mod, stmts in module_statements.items():
        filepath = os.path.join(MODULE_DIR, f"V1.0.0_{mod}.sql")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(header.format(module=mod))
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
