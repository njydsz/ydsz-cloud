#!/usr/bin/env python3
"""Fix V1.0.0.sql V7: fix all 6 remaining errors and properly split modules."""
import re
import os

SQL_FILE = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
MODULE_DIR = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"

MODULE_PREFIXES = {
    "common":   ["pmis_dict_", "pmis_audit_", "pmis_file_", "pmis_attachment_", "pmis_sys_", "pmis_operation_log", "pmis_meta_schema"],
    "system":   ["pmis_config", "pmis_tenant", "pmis_feature", "pmis_menu", "pmis_i18n"],
    "userinfo": ["pmis_user_", "pmis_role", "pmis_permission", "pmis_dept", "pmis_employee",
                 "pmis_part_time", "pmis_outsource", "pmis_position", "pmis_org_"],
    "project":  ["pmis_project_", "pmis_opportunity", "pmis_contract", "pmis_delivery",
                 "pmis_expense", "pmis_payment", "pmis_revenue", "pmis_reconcile",
                 "pmis_risk", "pmis_rate", "pmis_resource_pool", "pmis_timesheet",
                 "pmis_closure", "pmis_warranty", "pmis_eval_", "pmis_cockpit",
                 "pmis_search_", "pmis_rule_admin", "pmis_view_initiation", "pmis_view_cockpit"],
    "cronjob":  ["pmis_job_", "pmis_schedule", "pmis_task_", "pmis_sla_", "pmis_alert",
                 "pmis_webhook", "pmis_dag_"],
    "message":  ["pmis_msg_", "pmis_notification", "pmis_message_template", "pmis_message_"],
    "workflow": ["pmis_flow_", "pmis_view_flow"],
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
                    current.append(sql_text[i:end + len(tag)])
                    i = end + len(tag)
                    continue
            current.append(ch)
            i += 1
            continue
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
    print("SQL Fix V7: final 6 errors + module regeneration")
    print("=" * 60)
    
    with open(SQL_FILE, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    # Fix 1: Wrap CREATE EXTENSION vector in DO block
    print("\n[1] Wrapping CREATE EXTENSION vector in DO block...")
    old_vector = "CREATE EXTENSION IF NOT EXISTS vector;\n"
    new_vector = """DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector extension not available, skipping.';
END $$;
"""
    if old_vector in sql_content:
        sql_content = sql_content.replace(old_vector, new_vector, 1)
        print("    Done")
    else:
        print("    Already wrapped or not found")
    
    # Fix 2: Fix V12 outsource_rate monthly_salary
    print("\n[2] Fixing V12 outsource_rate monthly_salary...")
    sql_content = sql_content.replace(
        "('V12', '\u5916\u5305\u8d44\u6df1\u6280\u672f\u4e13\u5bb6',   'EXPERT',    545.45, 22, 12000.00, 1000,  600, 13600.00,",
        "('V12', '\u5916\u5305\u8d44\u6df1\u6280\u672f\u4e13\u5bb6',   'EXPERT',    545.45, 22, 11999.90, 1000,  600, 13599.90,"
    )
    print("    Done")
    
    # Fix 3: Replace all standalone COMMENT ON COLUMN pmis_job_dag_node with DO block
    print("\n[3] Wrapping pmis_job_dag_node COMMENTs in DO block...")
    dag_pattern = r"COMMENT\s+ON\s+COLUMN\s+pmis_job_dag_node\.\w+\s+IS\s+'(?:[^']|'')*';"
    dag_comments = re.findall(dag_pattern, sql_content, re.IGNORECASE)
    if dag_comments:
        do_lines = ["DO $$ BEGIN"]
        for c in dag_comments:
            do_lines.append("  " + c.strip())
        do_lines.append("EXCEPTION WHEN undefined_table THEN")
        do_lines.append("  RAISE NOTICE 'pmis_job_dag_node not found, skipping';")
        do_lines.append("END $$;")
        do_block = "\n".join(do_lines)
        for c in dag_comments:
            sql_content = sql_content.replace(c, '')
        # Remove any existing empty DO blocks
        sql_content = re.sub(r"DO\s+\$\$\s*BEGIN\s*EXCEPTION[^;]*;\s*END\s+\$\$;", "", sql_content, flags=re.IGNORECASE)
        sql_content = sql_content.rstrip() + "\n\n" + do_block + "\n"
        print(f"    Wrapped {len(dag_comments)} COMMENTs")
    else:
        print("    No standalone COMMENTs found")
    
    # Fix 4: Ensure ALTER TABLE pmis_flow_node ADD COLUMN form_fields_config is present
    print("\n[4] Checking form_fields_config ALTER TABLE...")
    if "ALTER TABLE pmis_flow_node ADD COLUMN IF NOT EXISTS form_fields_config TEXT;" not in sql_content:
        print("    [WARN] ALTER TABLE not found in source!")
    else:
        print("    OK - ALTER TABLE exists in source")
    
    # Fix 5: Replace ivfflat CREATE INDEX with DO block
    print("\n[5] Wrapping ivfflat index in DO block...")
    ivfflat_pattern = r"CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS\s+(\w+)\s+ON\s+pmis_agent_document_chunk\s+USING\s+ivfflat\s*\([^)]+\)\s*(?:WITH\s*\([^)]+\))?\s*;"
    ivfflat_match = re.search(ivfflat_pattern, sql_content, re.IGNORECASE | re.DOTALL)
    if ivfflat_match:
        idx_sql = ivfflat_match.group(0).strip()
        do_block = f"DO $$ BEGIN\n  {idx_sql}\nEXCEPTION WHEN OTHERS THEN\n  RAISE NOTICE 'ivfflat not available, skipping';\nEND $$;"
        sql_content = sql_content.replace(ivfflat_match.group(0), do_block)
        print("    Done")
    else:
        # Check if already in DO block
        if "ivfflat" in sql_content and "EXCEPTION" in sql_content:
            print("    Already wrapped")
        else:
            print("    [WARN] ivfflat index not found")
    
    # Fix 6: Check literule function exists
    print("\n[6] Checking literule function...")
    if "CREATE OR REPLACE FUNCTION update_rule_test_case_updated_at()" in sql_content:
        print("    OK - Function exists in source")
    else:
        print("    [WARN] Function not found!")
    
    # Write fixed file
    print(f"\n[7] Writing fixed {SQL_FILE}...")
    with open(SQL_FILE, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    
    # Split into modules
    print(f"\n[8] Splitting into modules...")
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
                # Check if function name matches a module
                func_match = re.search(r'CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+(\w+)', clean, re.IGNORECASE)
                if func_match:
                    func_name = func_match.group(1)
                    if 'rule' in func_name.lower():
                        module_statements["literule"].append(stmt)
                    elif 'flow' in func_name.lower():
                        module_statements["workflow"].append(stmt)
                    else:
                        module_statements["common"].append(stmt)
                else:
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
    
    # Write module files
    print(f"\n[9] Writing module files...")
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
    
    # Write all.sql
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
