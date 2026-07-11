#!/usr/bin/env python3
"""
PMIS SQL Split Script: V1.0.0_project.sql -> sales / finance / project + literule migration
Based on physical Mapper location after DDD refactor (2026-07-12)
"""
import re
import os

SOURCE_FILE = os.path.join(os.path.dirname(__file__), "V1.0.0_project.sql")
LITERULE_FILE = os.path.join(os.path.dirname(__file__), "V1.0.0_literule.sql")

# --- Table -> Module mapping ---
SALES_TABLES = {
    'pmis_project_opportunity',
    'pmis_project_opportunity_follow',
    'pmis_project_contract',
    'pmis_project_contract_supplement',
    'pmis_project_contract_change',
    'pmis_project_contract_template',
}

FINANCE_TABLES = {
    'pmis_finance_invoice',
    'pmis_finance_payment',
    'pmis_finance_customer_credit',
    'pmis_cost_expense',
    'pmis_profit_revenue',
    'pmis_profit_snapshot',
    'pmis_profit_simulation',
    'pmis_reconcile_daily',
}

LITERULE_TABLES = {
    'pmis_rule_execution_trace',
    'pmis_rule_decision_table',
    'pmis_rule_canary_bucket',
    'pmis_rule_scorecard',
    'pmis_rule_decision_tree',
    'pmis_rule_script',
    'pmis_rule_ab_policy',
    'pmis_rule_ab_rollback',
}

# Project tables = all others with CREATE TABLE
PROJECT_TABLES = {
    'pmis_project_initiation',
    'pmis_project_budget_item',
    'pmis_project_gate_review',
    'pmis_project_change',
    'pmis_execution_wbs_task',
    'pmis_execution_time_entry',
    'pmis_execution_risk',
    'pmis_execution_delivery_standard',
    'pmis_execution_delivery_item',
    'pmis_execution_closure',
    'pmis_cost_allocation',
    'pmis_cost_purchase',
    'pmis_evm_measure',
    'pmis_rate_card',
    'pmis_rate_internal',
    'pmis_warranty',
    'pmis_ops_ticket',
    'pmis_satisfaction',
    'pmis_billable_utilization_snapshot',
    'pmis_alert_dispatch',
}

# Read source file
with open(SOURCE_FILE, 'r', encoding='utf-8') as f:
    lines = f.readlines()

total_lines = len(lines)
print(f"Source file: {total_lines} lines")

# Find all CREATE TABLE positions
create_pattern = re.compile(r'^CREATE TABLE IF NOT EXISTS (\w+)')
table_positions = []  # list of (line_index, table_name)

for i, line in enumerate(lines):
    m = create_pattern.match(line)
    if m:
        table_positions.append((i, m.group(1)))

print(f"Found {len(table_positions)} CREATE TABLE statements")

# For each CREATE TABLE, find block start (search backwards for separator)
SEP_PATTERN = re.compile(r'^-- ={5,}')

table_blocks = []
for idx, (line_num, table_name) in enumerate(table_positions):
    # Search backwards for separator
    block_start = line_num
    for j in range(line_num - 1, max(line_num - 20, -1), -1):
        if j < 0:
            break
        if SEP_PATTERN.match(lines[j]):
            block_start = j
            break

    # Block end = next table's block_start - 1, or end of file for last table
    if idx + 1 < len(table_positions):
        next_start = table_positions[idx + 1][0]
        # Search backwards from next table to find its separator
        next_block_start = next_start
        for j in range(next_start - 1, max(next_start - 20, -1), -1):
            if j < 0:
                break
            if SEP_PATTERN.match(lines[j]):
                next_block_start = j
                break
        block_end = next_block_start - 1
        # Trim trailing empty lines
        while block_end > line_num and lines[block_end].strip() == '':
            block_end -= 1
    else:
        block_end = total_lines - 1
        # Trim trailing empty lines
        while block_end > line_num and lines[block_end].strip() == '':
            block_end -= 1

    table_blocks.append((table_name, block_start, block_end, line_num))

# Print table blocks
print("\nTable blocks:")
for name, start, end, create_line in table_blocks:
    print(f"  {name}: lines {start}-{end} ({end - start + 1} lines)")

# Classify and collect content
sales_content = []
finance_content = []
project_content = []
literule_content = []

for name, start, end, _ in table_blocks:
    block = ''.join(lines[start:end + 1])
    if name in SALES_TABLES:
        sales_content.append(block)
    elif name in FINANCE_TABLES:
        finance_content.append(block)
    elif name in LITERULE_TABLES:
        literule_content.append(block)
    elif name in PROJECT_TABLES:
        project_content.append(block)
    else:
        print(f"  WARNING: Unknown table {name} -> defaulting to project")
        project_content.append(block)

# Count tables per module
sales_count = sum(1 for name, _, _, _ in table_blocks if name in SALES_TABLES)
finance_count = sum(1 for name, _, _, _ in table_blocks if name in FINANCE_TABLES)
project_count = sum(1 for name, _, _, _ in table_blocks if name in PROJECT_TABLES)
literule_count = sum(1 for name, _, _, _ in table_blocks if name in LITERULE_TABLES)

# --- Write Sales SQL ---
sales_header = f"""-- ============================================================
-- PMIS sales module SQL
-- 商务销售服务 (ydsz-pmis-sales, port 9010)
-- ============================================================
-- 本脚本 DDL 对应后端 sales 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (SalesDataClient / FinanceDataClient)。
--
-- 表归属依据: ydsz-pmis-sales/src/main/java/.../infra/mapper/
-- 表数量: {sales_count} 张
-- --------------------------------------------------------------------

"""
sales_file = os.path.join(os.path.dirname(__file__), "V1.0.0_sales.sql")
with open(sales_file, 'w', encoding='utf-8') as f:
    f.write(sales_header)
    for block in sales_content:
        f.write(block)
        f.write('\n')
print(f"\nWrote {sales_file} ({sales_count} tables)")

# --- Write Finance SQL ---
finance_header = f"""-- ============================================================
-- PMIS finance module SQL
-- 财务会计服务 (ydsz-pmis-finance, port 9011)
-- ============================================================
-- 本脚本 DDL 对应后端 finance 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-pmis-finance/src/main/java/.../infra/mapper/
-- 表数量: {finance_count} 张
-- --------------------------------------------------------------------

"""
finance_file = os.path.join(os.path.dirname(__file__), "V1.0.0_finance.sql")
with open(finance_file, 'w', encoding='utf-8') as f:
    f.write(finance_header)
    for block in finance_content:
        f.write(block)
        f.write('\n')
print(f"Wrote {finance_file} ({finance_count} tables)")

# --- Write Project SQL (remaining) ---
project_header = f"""-- ============================================================
-- PMIS project module SQL
-- 项目执行服务 (ydsz-pmis-project, port 9003)
-- ============================================================
-- 本脚本 DDL 对应后端 project 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-pmis-project/src/main/java/.../infra/mapper/
-- 表数量: {project_count} 张 (原 42 张表拆分后剩余)
-- --------------------------------------------------------------------
-- [P4 架构优化提示] 跨模块冗余字段：pmis_cost_allocation.employee_name、
--   pmis_cost_purchase.applicant_name / approver_name 等 *_name 字段为历史
--   冗余存储，原则上应通过 NameAssembler 实时解析，禁止在写入时同步冗余。
--   现有数据保留（兼容历史查询），新写入由 Java 端 NameAssembler 自动注入。
-- --------------------------------------------------------------------

"""
project_file = os.path.join(os.path.dirname(__file__), "V1.0.0_project.sql")
with open(project_file, 'w', encoding='utf-8') as f:
    f.write(project_header)
    for block in project_content:
        f.write(block)
        f.write('\n')
print(f"Wrote {project_file} ({project_count} tables)")

# --- Append literule tables to V1.0.0_literule.sql ---
literule_append = """
-- ============================================================
-- 以下表从 V1.0.0_project.sql 迁移 (2026-07-12 DDD 拆分)
-- 原 Mapper 在 project 模块, 现已迁移至 literule 模块
-- 表归属依据: ydsz-pmis-literule/src/main/java/.../mapper/
-- ============================================================

"""
with open(LITERULE_FILE, 'a', encoding='utf-8') as f:
    f.write(literule_append)
    for block in literule_content:
        f.write(block)
        f.write('\n')
print(f"Appended to {LITERULE_FILE} ({literule_count} tables)")

print(f"\n=== Split Summary ===")
print(f"Sales:   {sales_count} tables -> V1.0.0_sales.sql")
print(f"Finance: {finance_count} tables -> V1.0.0_finance.sql")
print(f"Project: {project_count} tables -> V1.0.0_project.sql")
print(f"Literule: {literule_count} tables appended -> V1.0.0_literule.sql")
print(f"Total:   {sales_count + finance_count + project_count + literule_count} tables")
