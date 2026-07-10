#!/usr/bin/env python3
"""Test SQL module scripts against PostgreSQL - V3 with full-file execution."""
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
    cur.execute(f"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='{TEST_DB}' AND pid<>pg_backend_pid()")
    cur.execute(f"DROP DATABASE IF EXISTS {TEST_DB}")
    cur.close()

def create_test_db(conn):
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(f"CREATE DATABASE {TEST_DB}")
    cur.close()

def run_sql_file_full(conn, filepath):
    """Execute a SQL file by sending entire content to server.
    psycopg2 sends the full text to PostgreSQL which handles multiple statements,
    DO blocks, dollar-quoting, etc. natively."""
    with open(filepath, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    errors = []
    cur = conn.cursor()
    try:
        cur.execute(sql_content)
    except Exception as e:
        errors.append(str(e).strip())
    finally:
        try:
            conn.commit()
        except:
            conn.rollback()
    cur.close()
    return errors

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
    print("PMIS SQL Module Test Script V3 (full-file execution)")
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
        errors = run_sql_file_full(test_conn, sql_file)
        
        if errors:
            print(f"    [FAIL] {len(errors)} errors:")
            for e in errors[:5]:
                # Truncate long error messages
                if len(e) > 200:
                    e = e[:200] + "..."
                print(f"      - {e}")
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
    if table_count <= 30:
        for t in table_list:
            print(f"    - {t}")
    else:
        print(f"    (first 10):")
        for t in table_list[:10]:
            print(f"    - {t}")
        print(f"    ... and {table_count - 10} more")
    
    total_errors = sum(len(e) for e in all_errors.values())
    if total_errors == 0:
        print(f"\n  *** ALL MODULES PASSED! ***")
    else:
        print(f"\n  Errors: {total_errors}")
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
