-- ========================================================
-- V1.0.0_024__add_version_to_core_tables.sql
-- P1-12 乐观锁（@Version）覆盖核心实体
--
-- 为 10 张核心业务表添加 version 列，配合 MyBatis-Plus
-- OptimisticLockerInnerInterceptor 实现乐观锁控制。
--
-- 涉及表：
--   pmis_project 项目域：initiation / contract / contract_change / project_change
--   pmis_finance 财务域：invoice / payment / customer_credit
--   pmis_execution 执行域：wbs_task / purchase / ops_ticket
--
-- 默认值 0：所有现有记录初始版本号为 0，下一次 UPDATE 时自动 +1。
-- NOT NULL 约束：避免 NULL 导致乐观锁失效。
-- ========================================================

-- ========== 项目域 ==========
ALTER TABLE pmis_project_initiation
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 财务域 ==========
-- 早期版本误加 pmis_finance. schema 前缀，但所有表均建在 public schema
-- （与上方 project/execution 域的写法保持一致），执行时会报
-- "模式 pmis_finance 不存在" 错误，故去除 schema 前缀。
ALTER TABLE pmis_finance_invoice
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_payment
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_customer_credit
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 执行域 ==========
-- 早期版本把 pmis_cost_purchase 误写为 pmis_execution_purchase,
-- 把 pmis_ops_ticket 误写为 pmis_execution_ops_ticket。修正为实际
-- 表名（@TableName 定义）以避免 "关系不存在" 错误。
ALTER TABLE pmis_execution_wbs_task
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_cost_purchase
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_ops_ticket
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 同步更新 init schema 脚本中的字段注释（仅文档作用，不影响运行） ==========
COMMENT ON COLUMN pmis_project_initiation.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_contract.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_contract_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_invoice.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_payment.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_customer_credit.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_execution_wbs_task.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_cost_purchase.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_ops_ticket.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
