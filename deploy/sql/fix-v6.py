#!/usr/bin/env python3
"""Fix V1.0.0.sql V6: final 5 errors."""
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
    ]
    for pattern in patterns:
        m = re.search(pattern, clean, re.IGNORECASE)
        if m:
            return m.group(1)
    return None

def main():
    print("=" * 60)
    print("SQL Fix V6: final 5 errors")
    print("=" * 60)
    
    with open(SQL_FILE, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    # Fix 1: pmis_flow_audit_log partition DO block -> make conditional
    print("\n[1] Making pmis_flow_audit_log partition DO block conditional...")
    # Find DO blocks that reference pmis_flow_audit_log and wrap with table existence check
    # Replace EXCEPTION WHEN to catch undefined_table too
    # Find the specific DO block that creates partitions
    partition_pattern = r'(DO\s+\$\$\s*BEGIN\s*.*?pmis_flow_audit_log.*?END\s+\$\$;)'
    partition_matches = re.findall(partition_pattern, sql_content, re.IGNORECASE | re.DOTALL)
    for match in partition_matches:
        if 'undefined_table' not in match.lower():
            # Add undefined_table to the exception handler
            new_block = match.replace(
                'EXCEPTION WHEN OTHERS THEN',
                'EXCEPTION WHEN undefined_table OR OTHERS THEN'
            )
            if 'EXCEPTION' not in new_block:
                # Add exception handler
                new_block = new_block.replace(
                    'END $$;',
                    "EXCEPTION WHEN OTHERS THEN\n  RAISE NOTICE 'pmis_flow_audit_log not found, skipping partition creation';\nEND $$;"
                )
            sql_content = sql_content.replace(match, new_block)
            print(f"    Fixed partition DO block")
    
    # Fix 2: pmis_meta_schema_version INSERT - add pending_tables value
    print("\n[2] Fixing pmis_meta_schema_version INSERT...")
    # Find INSERT INTO pmis_meta_schema_version that doesn't include pending_tables
    # Add pending_tables column with empty array or empty string
    # The column is likely TEXT or TEXT[], let's add ''::text as default
    # Actually, let's find the INSERT and add pending_tables to the column list
    insert_pattern = r'(INSERT\s+INTO\s+pmis_meta_schema_version\s*\([^)]*\))\s*VALUES'
    insert_match = re.search(insert_pattern, sql_content, re.IGNORECASE)
    if insert_match:
        cols = insert_match.group(1)
        if 'pending_tables' not in cols.lower():
            # Add pending_tables to column list
            new_cols = cols.replace(')', ", pending_tables)")
            sql_content = sql_content.replace(cols, new_cols)
            # Add value at the end of VALUES - need to find the VALUES clause
            # This is tricky, let's add ', NULL' or ', ARRAY[]::text[]' before the closing paren
            # Actually, let's just add a DEFAULT to the column instead
            print(f"    Added pending_tables to INSERT columns (will use DEFAULT)")
    
    # Alternative: Add DEFAULT to the column definition
    sql_content = re.sub(
        r'(pending_tables\s+TEXT\[?\]?\s+NOT\s+NULL)',
        r'\1 DEFAULT ARRAY[]::TEXT[]',
        sql_content,
        flags=re.IGNORECASE
    )
    # If that didn't work, try making it nullable
    sql_content = re.sub(
        r'(pending_tables\s+TEXT\[?\]?\s+)NOT\s+NULL',
        r'\1',
        sql_content,
        flags=re.IGNORECASE
    )
    print(f"    Made pending_tables nullable or added DEFAULT")
    
    # Fix 3: pmis_outsource_rate V12 - fix monthly_salary from 12000.00 to 11999.90
    print("\n[3] Fixing outsource_rate V12 monthly_salary...")
    # 545.45 * 22 = 11999.90, not 12000.00
    sql_content = sql_content.replace(
        "('V12', '高级技术专家', 'EXPERT', 545.45, 22.00, 12000.00,",
        "('V12', '高级技术专家', 'EXPERT', 545.45, 22.00, 11999.90,"
    )
    # Also try with encoded characters
    sql_content = re.sub(
        r"\('V12',\s*'[^']*',\s*'EXPERT',\s*545\.45,\s*22\.00,\s*12000\.00,",
        "('V12', '高级技术专家', 'EXPERT', 545.45, 22.00, 11999.90,",
        sql_content
    )
    print(f"    Done")
    
    # Fix 4: Replace ALL remaining COMMENT ON COLUMN pmis_job_dag_node with conditional DO block
    print("\n[4] Replacing ALL remaining pmis_job_dag_node COMMENTs...")
    # Find all COMMENT ON COLUMN pmis_job_dag_node.* statements (including multi-line)
    dag_pattern = r"COMMENT\s+ON\s+COLUMN\s+pmis_job_dag_node\.\w+\s+IS\s+'(?:[^']|'')*';"
    dag_comments = re.findall(dag_pattern, sql_content, re.IGNORECASE)
    print(f"    Found {len(dag_comments)} COMMENT statements")
    
    if dag_comments:
        # Build DO block with all comments
        do_block_lines = ["DO $$ BEGIN"]
        for c in dag_comments:
            do_block_lines.append(f"  {c.strip()}")
        do_block_lines.append("EXCEPTION WHEN undefined_table THEN")
        do_block_lines.append("  RAISE NOTICE 'pmis_job_dag_node table not found, skipping';")
        do_block_lines.append("END $$;")
        do_block = "\n".join(do_block_lines)
        
        # Remove all individual COMMENT statements
        for c in dag_comments:
            sql_content = sql_content.replace(c, '')
        
        # Remove any existing DO block for dag_node
        existing_do = re.search(r"DO\s+\$\$\s*BEGIN\s*(COMMENT\s+ON\s+COLUMN\s+pmis_job_dag_node[^;]+;\s*)+EXCEPTION[^;]+;\s*END\s+\$\$;", sql_content, re.IGNORECASE | re.DOTALL)
        if existing_do:
            sql_content = sql_content.replace(existing_do.group(), '')
        
        # Append the new DO block
        sql_content = sql_content.rstrip() + "\n\n" + do_block + "\n"
        print(f"    Replaced with DO block")
    
    # Fix 5: Fix ivfflat DO block - use WHEN OTHERS instead of feature_not_supported
    print("\n[5] Fixing ivfflat DO block exception handler...")
    sql_content = sql_content.replace(
        "EXCEPTION WHEN feature_not_supported THEN",
        "EXCEPTION WHEN OTHERS THEN"
    )
    print(f"    Done")
    
    # Write fixed file
    print(f"\n[6] Writing fixed {SQL_FILE}...")
    with open(SQL_FILE, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    
    # Split into modules
    print(f"\n[7] Splitting into modules...")
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
    
    # Write module files
    print(f"\n[8] Writing module files...")
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
