-- =============================================================
-- V1.0.0_031__add_pmis_flow_weight.sql
-- 流程多实例会签权重 + VOTE 通过率
--
-- P1-5: 多实例会签权重（per-user 权重）+ VOTE 通过率（可配置阈值）
--      对标钉钉/飞书的会签权重：财务总监 3 票，普通员工 1 票。
--      默认阈值 50% + 1（即过半数通过），支持节点 ext 配置 passRate（0~1）。
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_user 增加 weight 字段（每个办理人的票数/权重）
-- -----------------------------------
ALTER TABLE pmis_flow_user
    ADD COLUMN IF NOT EXISTS weight INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN pmis_flow_user.weight IS '办理人权重（默认 1，可配置 2/3 等）';

-- -------------------------------------------
-- 2. pmis_flow_task 增加 vote_pass_rate 字段（VOTE 模式下的通过率阈值）
-- -------------------------------------------
ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS vote_pass_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.5;

COMMENT ON COLUMN pmis_flow_task.vote_pass_rate IS 'VOTE 模式通过率阈值（0~1，默认 0.5 表示过半数）';
