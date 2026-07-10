#!/usr/bin/env python3
"""Test SQL module scripts against PostgreSQL - V2 with full-file execution."""
import psycopg2
import os
import sys

DB_HOST = "192.168.31.87"
DB_PORT = 5432
DB_USER = "postgres"
DB_PASS = "Ydsz1020"
TEST_DB = "pmis_sqltest"
SQL_DIR = r"d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"

MODULES = [
    "common", "system", "userinfo", "project",
    "cronjob", "message", "workflow", "agent", "literule"
]

def connect(dbname="postgres"):
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT,
        user=DB_USER, password=DB_PASS,
        dbname=dbname
    )

def drop_test_db(conn):
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(f"DROP DATABASE IF EXISTS {TEST_DB}")
    cur.close()

def create_test_db(conn):
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(f"CREATE DATABASE {TEST_DB}")
    cur.close()

def run_sql_file(conn, filepath):
    """Execute a SQL file by sending entire content to server.
    psycopg2 sends the full text to PostgreSQL which handles multiple statements."""
    with open(filepath, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    errors = []
    cur = conn.cursor()
    try:
        cur.execute(sql_content)
    except Exception as e:
        errors.append(str(e).strip())
    conn.commit()
    cur.close()
    return errors

def run_sql_file_safe(conn, filepath):
    """Execute SQL file statement-by-statement for better error reporting.
    Uses a proper SQL parser that handles comments, strings, and dollar-quoting."""
    with open(filepath, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    statements = parse_sql_statements(sql_content)
    errors = []
    cur = conn.cursor()
    
    for i, stmt in enumerate(statements):
        stmt = stmt.strip()
        if not stmt:
            continue
        try:
            cur.execute(stmt)
        except Exception as e:
            first_line = stmt.split('\n')[0][:150]
            errors.append(f"Line ~{i}: {str(e).strip()}\n  SQL: {first_line}")
    
    conn.commit()
    cur.close()
    return errors

def parse_sql_statements(sql_text):
    """Parse SQL into statements, properly handling:
    - Line comments (-- ...)
    - Block comments (/* ... */)
    - Single-quoted strings ('...')
    - Dollar-quoted strings ($tag$...$tag$)
    """
    statements = []
    current = []
    i = 0
    length = len(sql_text)
    
    while i < length:
        ch = sql_text[i]
        
        # Line comment: -- until end of line
        if ch == '-' and i + 1 < length and sql_text[i + 1] == '-':
            # Add the comment line to current statement (for context)
            while i < length and sql_text[i] != '\n':
                current.append(sql_text[i])
                i += 1
            if i < length:
                current.append(sql_text[i])
                i += 1
            continue
        
        # Block comment: /* ... */
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
            # Try to find matching $tag$
            j = i + 1
            while j < length and (sql_text[j].isalnum() or sql_text[j] == '_'):
                j += 1
            if j < length and sql_text[j] == '$':
                tag = sql_text[i:j + 1]  # $tag$
                # Find closing tag
                end = sql_text.find(tag, j + 1)
                if end != -1:
                    # Include the entire dollar-quoted string
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
                        # Escaped quote ''
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
        
        # Semicolon - end of statement (but only if not in string/comment)
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

def get_table_count(conn):
    cur = conn.cursor()
    cur.execute("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")
    count = cur.fetchone()[0]
    cur.close()
    return count

def get_table_list(conn):
    cur = conn.cursor()
    cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name")
    tables = [row[0] for row in cur.fetchall()]
    cur.close()
    return tables

def main():
    print("=" * 60)
    print("PMIS SQL Module Test Script V2")
    print("=" * 60)
    
    # 1. Connect
    print(f"\n[1] Connecting to PostgreSQL at {DB_HOST}:{DB_PORT}...")
    try:
        admin_conn = connect("postgres")
        ver = admin_conn.get_parameter_status('server_version')
        print(f"    Connected! Server: {ver}")
    except Exception as e:
        print(f"    [FAIL] Connection error: {e}")
        sys.exit(1)
    
    # 2. Create test database
    print(f"\n[2] Preparing test database '{TEST_DB}'...")
    drop_test_db(admin_conn)
    create_test_db(admin_conn)
    admin_conn.close()
    
    # 3. Connect to test database
    print(f"\n[3] Connecting to {TEST_DB}...")
    test_conn = connect(TEST_DB)
    test_conn.autocommit = True
    
    # 4. Run each module SQL
    all_errors = {}
    for mod in MODULES:
        sql_file = os.path.join(SQL_DIR, f"V1.0.0_{mod}.sql")
        if not os.path.exists(sql_file):
            print(f"\n[{mod}] SKIP - file not found")
            continue
        
        print(f"\n[{mod}] Testing {os.path.basename(sql_file)}...")
        errors = run_sql_file_safe(test_conn, sql_file)
        
        if errors:
            print(f"    [FAIL] {len(errors)} errors:")
            for e in errors[:5]:
                print(f"      - {e}")
            if len(errors) > 5:
                print(f"      ... and {len(errors) - 5} more")
            all_errors[mod] = errors
        else:
            print(f"    [OK] No errors")
    
    # 5. Summary
    table_count = get_table_count(test_conn)
    table_list = get_table_list(test_conn)
    print(f"\n{'=' * 60}")
    print(f"TEST SUMMARY")
    print(f"{'=' * 60}")
    print(f"  Database: {TEST_DB}")
    print(f"  Tables created: {table_count}")
    if table_count <= 20:
        for t in table_list:
            print(f"    - {t}")
    print(f"  Modules tested: {len(MODULES)}")
    
    total_errors = sum(len(e) for e in all_errors.values())
    if total_errors == 0:
        print(f"  Errors: 0 - ALL MODULES PASSED!")
    else:
        print(f"  Errors: {total_errors}")
        for mod, errors in all_errors.items():
            print(f"    {mod}: {len(errors)} errors")
    
    test_conn.close()
    
    # 6. Cleanup - drop test database
    print(f"\n[6] Cleaning up - dropping test database '{TEST_DB}'...")
    admin_conn = connect("postgres")
    admin_conn.autocommit = True
    cur = admin_conn.cursor()
    cur.execute(f"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='{TEST_DB}' AND pid<>pg_backend_pid()")
    cur.close()
    drop_test_db(admin_conn)
    admin_conn.close()
    print("    [OK] Test database dropped. Cleanup complete.")
    
    if total_errors > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
