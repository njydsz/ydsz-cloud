-- ============================================
-- V1.0.0_045__add_decision_table_hit_policy.sql
-- 决策表命中策略字段
--
-- 为 pmis_rule_decision_table 增加 hit_policy 列，支持 DMN 标准命中策略：
--   UNIQUE  - 唯一命中，匹配多行时报错
--   FIRST   - 首次命中（默认）
--   PRIORITY- 优先级命中，返回优先级最高的匹配行
--   COLLECT - 收集命中，返回所有匹配行
--   ANY     - 任意命中，返回任意一条匹配行
-- ============================================

ALTER TABLE pmis_rule_decision_table
    ADD COLUMN IF NOT EXISTS hit_policy VARCHAR(32) NOT NULL DEFAULT 'FIRST';

COMMENT ON COLUMN pmis_rule_decision_table.hit_policy IS '命中策略：UNIQUE/FIRST/PRIORITY/COLLECT/ANY';
