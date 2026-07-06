-- ====================================================================
-- V1.0.1__add_rate_to_time_entry.sql
-- --------------------------------------------------------------------
-- 目的:工时录入表新增费率卡关联字段(rate_id, rate),用于成本归集时按实际费率核算
-- 兼容性: PostgreSQL 18+,与 V1.0.0 之后所有 schema 兼容
-- 回滚: ALTER TABLE pmis_execution_time_entry DROP COLUMN IF EXISTS rate; ALTER TABLE pmis_execution_time_entry DROP COLUMN IF EXISTS rate_id;
-- 影响行数: ~0 (DDL only,旧数据 rate_id/rate 为 NULL)
-- 维护窗口: < 5s
-- ====================================================================

BEGIN;

-- 工时录入表新增费率卡关联字段
ALTER TABLE pmis_execution_time_entry
    ADD COLUMN IF NOT EXISTS rate_id BIGINT,
    ADD COLUMN IF NOT EXISTS rate   NUMERIC(10,2);

COMMENT ON COLUMN pmis_execution_time_entry.rate_id IS '命中的费率卡 ID(关联 pmis_rate_card.id,可空:未匹配到费率卡)';
COMMENT ON COLUMN pmis_execution_time_entry.rate IS '人天费率(冗余,锁定当时报价,用于成本归集)';

COMMIT;
