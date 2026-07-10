#!/usr/bin/env python3
"""Fix V1.0.0.sql V4: fix table name references, conditional comments, clean double left()."""
import re
import os

SQL_FILE = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
MODULE_DIR = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"

MODULE_PREFIXES = {
    "common":   ["pmis_dict_", "pmis_audit_", "pmis_file_", "pmis_attachment_", "pmis_sys_", "pmis_view_", "pmis_operation_log"],
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
    "message":  ["pmis_msg_", "pmis_notification", "pmis_message_template", "pmis_message_"],
    "workflow": ["pmis_flow_"],
    "agent":    ["pmis_agent_", "pmis_knowledge_", "pmis_token_", "pmis_tool_",
                 "pmis_hitl_", "pmis_mcp_"],
    "literule": ["pmis_rule_def", "pmis_rule_version", "pmis_rule_template",
                 "pmis_rule_test", "pmis_rule_variable", "pmis_rule_chain",
                 "pmis_rule_dependency", "pmis_rule_pack", "pmis_rule_node",
                 "pmis_rule_event", "pmis_rule_log"],
}

def get_module_for_table(table_name):
    all_prefixes = []
    for mod, prefixes in MODULE_PREFIXES.items():
        for p in prefixes:
            all_prefixes.append((p, mod))
    all_prefixes.sort(key=lambda x: len(x[0]), reverse=True)
    for prefix, mod in all_prefixes:
        if table_name.startswith(prefix):
            return mod
    return "common"

def parse_sql_statements(sql_text):
    statements = []
    current = []
    i = 0
    length = len(sql_text)
    while i < length:
        ch = sql_text[i]
        if ch == '-' and i + 1 < length and sql_text[i + 1] == '-':
            while i < length and sql_text[i] != '\n':
                current.append(sql_text[i]); i += 1
            if i < length:
                current.append(sql_text[i]); i += 1
            continue
        if ch == '/' and i + 1 < length and sql_text[i + 1] == '*':
            current.append(sql_text[i]); current.append(sql_text[i + 1]); i += 2
            while i < length:
                if sql_text[i] == '*' and i + 1 < length and sql_text[i + 1] == '/':
                    current.append(sql_text[i]); current.append(sql_text[i + 1]); i += 2; break
                current.append(sql_text[i]); i += 1
            continue
        if ch == '$':
            j = i + 1
            while j < length and (sql_text[j].isalnum() or sql_text[j] == '_'):
                j += 1
            if j < length and sql_text[j] == '$':
                tag = sql_text[i:j + 1]
                end = sql_text.find(tag, j + 1)
                if end != -1:
                    current.append(sql_text[i:end + len(tag)]); i = end + len(tag); continue
        if ch == "'":
            current.append(ch); i += 1
            while i < length:
                if sql_text[i] == '\\' and i + 1 < length:
                    current.append(sql_text[i]); current.append(sql_text[i + 1]); i += 2; continue
                if sql_text[i] == "'":
                    if i + 1 < length and sql_text[i + 1] == "'":
                        current.append(sql_text[i]); current.append(sql_text[i + 1]); i += 2; continue
                    else:
                        current.append(sql_text[i]); i += 1; break
                current.append(sql_text[i]); i += 1
            continue
        if ch == ';':
            current.append(ch); statements.append(''.join(current)); current = []; i += 1; continue
        current.append(ch); i += 1
    rem = ''.join(current).strip()
    if rem:
        statements.append(rem)
    return statements

def strip_leading_comments(stmt):
    lines = stmt.split('\n')
    while lines and (lines[0].strip().startswith('--') or lines[0].strip() == ''):
        lines.pop(0)
    return '\n'.join(lines)

def extract_table_name(stmt):
    clean = strip_leading_comments(stmt)
    patterns = [
        r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)',
        r'COMMENT\s+ON\s+TABLE\s+(\w+)',
        r'COMMENT\s+ON\s+COLUMN\s+(?:IF\s+EXISTS\s+)?(\w+)\.',
        r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?\w+\s+ON\s+(\w+)',
        r'INSERT\s+INTO\s+(\w+)',
        r'ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(\w+)',
        r'ANALYZE\s+(\w+)',
        r'CREATE\s+TRIGGER\s+\w+\s+(?:BEFORE|AFTER)\s+\w+\s+ON\s+(\w+)',
        r'CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+(\w+)',
        r'COMMENT\s+ON\s+VIEW\s+(\w+)',
        r'CREATE\s+MATERIALIZED\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)',
        r'DROP\s+TABLE\s+IF\s+EXISTS\s+(\w+)',
        r'DROP\s+TRIGGER\s+IF\s+EXISTS\s+\w+\s+ON\s+(\w+)',
        r'GRANT\s+\w+\s+ON\s+(\w+)',
        r'REVOKE\s+\w+\s+ON\s+(\w+)',
        r'COMMENT\s+ON\s+CONSTRAINT\s+\w+\s+ON\s+(\w+)',
        r'COMMENT\s+ON\s+FUNCTION\s+([\w.]+)',
    ]
    for pattern in patterns:
        m = re.search(pattern, clean, re.IGNORECASE)
        if m:
            return m.group(1)
    return None

def main():
    print("=" * 60)
    print("SQL Fix V4: table names + conditional comments + clean left()")
    print("=" * 60)
    
    # 1. Read
    print(f"\n[1] Reading {SQL_FILE}...")
    with open(SQL_FILE, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    # 2. Fix: Clean double left()
    print(f"\n[2] Cleaning double left()...")
    old = "left(left(replace(gen_random_uuid()::text,'-',''),20),20)"
    new = "left(replace(gen_random_uuid()::text,'-',''),20)"
    count = sql_content.count(old)
    sql_content = sql_content.replace(old, new)
    print(f"    Cleaned {count} occurrences")
    
    # 3. Fix: pmis_message_template -> pmis_msg_template in INSERT statements
    # But only in INSERT context, not in comments
    print(f"\n[3] Fixing pmis_message_template -> pmis_msg_template in INSERT...")
    # Replace INSERT INTO pmis_message_template with INSERT INTO pmis_msg_template
    count = len(re.findall(r'INSERT\s+INTO\s+pmis_message_template', sql_content, re.IGNORECASE))
    sql_content = re.sub(
        r'INSERT\s+INTO\s+pmis_message_template',
        'INSERT INTO pmis_msg_template',
        sql_content,
        flags=re.IGNORECASE
    )
    # Also fix WHERE NOT EXISTS references
    sql_content = re.sub(
        r'FROM\s+pmis_message_template',
        'FROM pmis_msg_template',
        sql_content,
        flags=re.IGNORECASE
    )
    sql_content = re.sub(
        r'WHERE\s+NOT\s+EXISTS\s+\(SELECT\s+1\s+FROM\s+pmis_message_template',
        'WHERE NOT EXISTS (SELECT 1 FROM pmis_msg_template',
        sql_content,
        flags=re.IGNORECASE
    )
    print(f"    Fixed {count} INSERT references")
    
    # 4. Fix: Wrap pmis_job_dag_node COMMENT statements in conditional DO block
    # The table doesn't exist yet, so COMMENT statements fail
    print(f"\n[4] Wrapping pmis_job_dag_node COMMENTs in conditional DO block...")
    # Find all COMMENT ON COLUMN pmis_job_dag_node.* statements
    dag_node_comments = re.findall(
        r"(COMMENT\s+ON\s+COLUMN\s+pmis_job_dag_node\.\w+\s+IS\s+'[^']*';)",
        sql_content,
        re.IGNORECASE
    )
    if dag_node_comments:
        # Replace them with a DO block
        do_block = "DO $$ BEGIN\n"
        for c in dag_node_comments:
            do_block += f"  {c}\n"
        do_block += """EXCEPTION WHEN undefined_table THEN
  RAISE NOTICE 'pmis_job_dag_node table not found, skipping column comments';
END $$;"""
        # Replace all consecutive COMMENT statements with the DO block
        # Find the pattern of all these comments together
        pattern = r"(COMMENT\s+ON\s+COLUMN\s+pmis_job_dag_node\.\w+\s+IS\s+'[^']*';\s*)+"
        sql_content = re.sub(pattern, do_block + "\n", sql_content, flags=re.IGNORECASE)
        print(f"    Wrapped {len(dag_node_comments)} COMMENT statements in DO block")
    else:
        print(f"    No pmis_job_dag_node COMMENTs found")
    
    # 5. Write fixed file
    print(f"\n[5] Writing fixed {SQL_FILE}...")
    with open(SQL_FILE, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    
    # 6. Split into modules
    print(f"\n[6] Splitting into modules...")
    statements = parse_sql_statements(sql_content)
    print(f"    Total statements: {len(statements)}")
    
    module_statements = {mod: [] for mod in MODULE_PREFIXES.keys()}
    no_module = []
    
    for stmt in statements:
        table_name = extract_table_name(stmt)
        if table_name:
            mod = get_module_for_table(table_name)
            module_statements[mod].append(stmt)
        else:
            clean = strip_leading_comments(stmt).strip()
            if re.match(r'CREATE\s+EXTENSION|DO\s+\$', clean, re.IGNORECASE):
                module_statements["common"].append(stmt)
            elif re.match(r'CREATE\s+OR\s+REPLACE\s+FUNCTION|CREATE\s+FUNCTION|CREATE\s+PROCEDURE', clean, re.IGNORECASE):
                module_statements["common"].append(stmt)
            elif re.match(r'SET\s+|BEGIN\s*;|COMMIT\s*;|RESET\s+', clean, re.IGNORECASE):
                pass
            elif clean.startswith('--') or clean == '':
                pass
            else:
                no_module.append(stmt)
    
    for mod, stmts in module_statements.items():
        print(f"    {mod}: {len(stmts)} statements")
    if no_module:
        print(f"    [WARN] No module: {len(no_module)}")
        for s in no_module[:3]:
            safe = strip_leading_comments(s)[:120].encode('ascii', 'replace').decode('ascii')
            print(f"      - {safe}")
    
    # 7. Write module files
    print(f"\n[7] Writing module files...")
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
    
    # 8. Write all.sql
    all_filepath = os.path.join(MODULE_DIR, "V1.0.0_all.sql")
    with open(all_filepath, 'w', encoding='utf-8') as f:
        f.write("""-- ============================================================
-- PMIS Full Database Initialization Script
-- Executes all module scripts in dependency order
-- ============================================================

""")
        for mod in MODULE_PREFIXES.keys():
            f.write(f"\\i V1.0.0_{mod}.sql\n")
    
    print(f"\n[DONE]")

if __name__ == "__main__":
    main()
